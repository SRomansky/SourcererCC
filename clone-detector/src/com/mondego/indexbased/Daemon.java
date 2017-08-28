package com.mondego.indexbased;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.HashSet;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.xml.bind.DatatypeConverter;

import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mondego.models.QueryFileProcessor;
import com.mondego.utility.TokensFileReader;
import com.mondego.utility.Util;

import spark.Request;

public class Daemon {
	private SearchManager sm;
	private static final Logger logger = LogManager.getLogger(Daemon.class);
	
	public Daemon(SearchManager theInstance) {
		sm = theInstance;
	}
	
	public void start() {
    	/*
    	 * Start the daemon and load the dataset into memory if it exists.
    	 */
    	// TODO register the ip address and port number of this process with the manager service
    	// TODO if the daemon is restarted, or the dataset needs to be reloaded, the invertedIndex and documentsForII need to be cleared.
    	
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
	}
    
    public void query() {
    	// TODO prevent multiple queries at the same time?
    	// start queue processors
    	sm.completedQueries = new HashSet<Long>();

        sm.startQueryThreads();
    	
        sm.resetQueryCounters();	// reset the global counters
        long currentTime = System.nanoTime();
        String outputDir = SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR
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
                    	// TODO make an output directory using a timestamp
                    	// TODO figure out how these will be reported from the web service
                    	
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
                    
                    // TODO does this code need the while loop?
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
		
		InetAddress ip = null;
		try {
			ip = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			logger.error("Failed to get ip address of client application."); // TODO handle big error..
			e.printStackTrace();
		}
		int port = sm.daemonPort;
		
		if (ip == null) {
			// uh oh
		}
		
		MultipartEntityBuilder mb = MultipartEntityBuilder.create();//org.apache.http.entity.mime
	    mb.addTextBody("foo", "bar");
	    mb.addTextBody("ip", ip.toString());
	    mb.addTextBody("port", String.valueOf(sm.daemonPort));
	    //mb.addBinaryBody("query_file", tempFile.toFile());
	    org.apache.http.HttpEntity e = mb.build();

	    try {
	    	String registrationUrl = "http://" + sm.managerURL + ":" + sm.managerPort + "/register";
	    	URLConnection conn = new URL(registrationUrl).openConnection();
	    	conn.setDoOutput(true);
	    	conn.addRequestProperty(e.getContentType().getName(), e.getContentType().getValue());//header "Content-Type"...
	    	conn.addRequestProperty("Content-Length", String.valueOf(e.getContentLength()));
	    	OutputStream fout = conn.getOutputStream();
	    	e.writeTo(fout);
	    	fout.close();
	    	conn.getInputStream().close();//output of remote url
	    } catch (IOException re) {
	    	logger.error("Failed to send registration POST");
	    }
	}
}
