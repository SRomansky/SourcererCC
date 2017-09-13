package com.mondego.indexbased;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mondego.httpcommunication.Register;
import com.mondego.models.QueryFileProcessor;
import com.mondego.utility.TokensFileReader;
import com.mondego.utility.Util;

import spark.Request;

public class Daemon {
	public static SearchManager sm = null;
	private static final Logger logger = LogManager.getLogger(Daemon.class);
	private static Daemon daemon = null;
	public static String ip = "";
	public static int port = 0;
	public static String outputDir = null;
	public static String dataset_id = null; // In INIT this variable is set to the SHA-256 of the dataset.
	
	public enum State {
		/**
		 * BUSY - running query
		 * IDLE - waiting for query
		 * INIT - initializing, loading the dataset into memory
		 */
		BUSY, IDLE, INIT
	}
	
	private static State state = State.INIT; // TODO do semaphores need to be used to access and modify the state?
	
	private void setState(State newState) {
		state = newState;
	}
	
	public State getState() {
		return state;
	}
	
	public static Daemon getInstance() {
		if (daemon == null) {
			logger.warn("Daemon not initialized.");
		}
		
		return daemon;
	}
	
	public Daemon(SearchManager theInstance, String daemonIp, int daemonPort) {
		sm = theInstance;
		ip = daemonIp;
		port = daemonPort;
		daemon = this;
	}
	
	private String calculateDatasetId() {
		// based on https://stackoverflow.com/questions/3010071/how-to-calculate-md5-checksum-on-directory-with-java-or-groovy/15503271#15503271
		File datasetDir = new File(SearchManager.DATASET_DIR);
		
		assert (datasetDir.isDirectory());
	    Vector<FileInputStream> fileStreams = new Vector<FileInputStream>();

	    System.out.println("Found files for hashing:");
	    collectInputStreams(datasetDir, fileStreams, false);

	    SequenceInputStream seqStream = 
	            new SequenceInputStream(fileStreams.elements());

	    try {
	        String sha256Hash = DigestUtils.sha256Hex(seqStream);
	        seqStream.close();
	        return sha256Hash;
	    }
	    catch (IOException e) {
	        throw new RuntimeException("Error reading files to hash in "
	                                   + datasetDir.getAbsolutePath(), e);
	    }
	}
	
	private void collectInputStreams(File dir,
			List<FileInputStream> foundStreams,
			boolean includeHiddenFiles) {

		File[] fileList = dir.listFiles();        
		Arrays.sort(fileList,               // Need in reproducible order
				new Comparator<File>() {
			public int compare(File f1, File f2) {                       
				return f1.getName().compareTo(f2.getName());
			}
		});

		for (File f : fileList) {
			if (!includeHiddenFiles && f.getName().startsWith(".")) {
				// Skip it
			}
			else if (f.isDirectory()) {
				collectInputStreams(f, foundStreams, includeHiddenFiles);
			}
			else {
				try {
					System.out.println("\t" + f.getAbsolutePath());
					foundStreams.add(new FileInputStream(f));
				}
				catch (FileNotFoundException e) {
					throw new AssertionError(e.getMessage()
							+ ": file should never not be found!");
				}
			}
		}
	}
	
	public void start() {
    	/*
    	 * Start the daemon and load the dataset into memory if it exists.
    	 */
    	// TODO register the ip address and port number of this process with the manager service
    	// TODO if the daemon is restarted, or the dataset needs to be reloaded, the invertedIndex and documentsForII need to be cleared.
    	setState(State.INIT);
    	dataset_id = calculateDatasetId();
    	
    	SearchManager.gtpmSearcher = new CodeSearcher(Util.GTPM_INDEX_DIR, "key");  // when is this built/used?
        File datasetDir = new File(SearchManager.DATASET_DIR);
        if (datasetDir.isDirectory()) {
            logger.info("Dataset directory: " + datasetDir.getAbsolutePath());
            for (File inputFile : Util.getAllFilesRecur(datasetDir)) {
                logger.info("indexing dataset file: " + inputFile.getAbsolutePath());
                try {
                	File candidateFile = inputFile;
        			
        			int completedLines = 0;
        			while (true) {
        				// SearchManager() spawns threads to process the index information from the query files.
        				logger.info("creating indexes for " + candidateFile.getAbsolutePath());
        				completedLines = sm.createIndexes(candidateFile, completedLines);  // sends read Dataset to invertedIndex, documentsForII. Cuts up some of the bags if they are on memory boundaries. I haven't read how the memory boundary works.
        				logger.info("indexes created");
        				logger.debug("COMPLETED LINES: " + completedLines);
        				if (completedLines == -1) {
        					break;
        				}
        			}
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    logger.error(SearchManager.NODE_PREFIX + ", something nasty, exiting. counter:"
                            + SearchManager.statusCounter);
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        } else {
            logger.error("File: " + datasetDir.getName() + " is not a directory. Exiting now");
            System.exit(1);
        }
        setState(State.IDLE);
	}
    
    public void query() {
    	// TODO prevent multiple queries at the same time?
    	// start queue processors
    	setState(State.BUSY);
    	sm.completedQueries = new HashSet<Long>();

        sm.startQueryThreads();
    	
        sm.resetQueryCounters();	// reset the global counters
        long currentTime = System.nanoTime();
        outputDir = SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR
        		+ "_" + String.valueOf(currentTime);
        Util.createDirs(outputDir);
    	
		File datasetDir = new File(SearchManager.QUERY_DIR_PATH);
        if (datasetDir.isDirectory()) {
            logger.info("QuerySet directory: " + datasetDir.getAbsolutePath());
            for (File inputFile : Util.getAllFilesRecur(datasetDir)) {
                logger.info("indexing QuerySet file: " + inputFile.getAbsolutePath());
                try {
                	File queryFile = inputFile;
                    QueryFileProcessor queryFileProcessor = new QueryFileProcessor();
                    logger.info("Query File: " + queryFile.getAbsolutePath());
                    String filename = queryFile.getName().replaceFirst("[.][^.]+$", "");
                    try {
                        String cloneReportFileName = outputDir + "/" + filename + "clones_index_WITH_FILTER.txt";
                        
                        File cloneReportFile = new File(cloneReportFileName);
                        if (cloneReportFile.exists()) {
                            sm.appendToExistingFile = true;
                        } else {
                            sm.appendToExistingFile = false;
                        }
                        SearchManager.clonesWriter = Util.openFile(outputDir +"/" + filename + "clones_index_WITH_FILTER.txt",
                                sm.appendToExistingFile);
                        // recoveryWriter
                        SearchManager.recoveryWriter = Util.openFile(
                                outputDir + "/recovery.txt",
                                false);
                    } catch (IOException e) {
                        logger.error(e.getMessage() + " exiting");
                        System.exit(1);
                    }
                    
        			try {
        				TokensFileReader tfr = new TokensFileReader(SearchManager.NODE_PREFIX, queryFile,
        						SearchManager.max_tokens, queryFileProcessor);
        				tfr.read();
        			} catch (IOException e) {
        				logger.error(e.getMessage() + " skiping to next file");
        			} catch (ParseException e) {
        				logger.error(SearchManager.NODE_PREFIX + "parseException caught. message: " + e.getMessage());
        				e.printStackTrace();
        			}
        			
                } catch (Exception e) {
                    logger.error(SearchManager.NODE_PREFIX + ", something nasty, exiting. counter:"
                            + SearchManager.statusCounter);
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        } else {
            logger.error("File: " + datasetDir.getName() + " is not a directory. Exiting now");
            System.exit(1);
        }
        sm.stopQueryThreads();
        setState(State.IDLE);
	}
    
    public Path getPostFile(File uploadDirectory, Request req) {
    	/**
    	 * Extract a multi-part form binary to a temporary file if one is
    	 * found in a POST message. If a binary is not found, return null.
    	 * 
    	 * Assumption: the form part is named "query_file"
    	 */
    	Path tempFile = null;

        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

        try { // getPart needs to use same "name" as input field in form
        	tempFile = Files.createTempFile(uploadDirectory.toPath(), ".zip", "");
        	InputStream input = req.raw().getPart("query_file").getInputStream();
        	Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ServletException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        //logInfo(req, tempFile);
		try {
			byte[] b = Files.readAllBytes(tempFile);
	        byte[] hash = MessageDigest.getInstance("MD5").digest(b);
	        String shash = DatatypeConverter.printHexBinary(hash);
	        System.out.println("Got file with hash: " + shash);
		} catch (IOException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	return tempFile;
    }

	public void register() {
		/**
		 * Register this shard with the management application.
		 * 
		 * This method sends a POST to sm.managerURL:sm.managerPort
		 */
		setState(State.BUSY);
		Register.sendRegistration();
		setState(State.IDLE);
	}
	
	public File getResults() {
		//File resultsPath = new File(SearchManager.OUTPUT_DIR_TH);
		File resultsPath = new File(outputDir);
		return resultsPath;
	}
}
