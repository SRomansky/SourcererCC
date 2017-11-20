package com.mondego.indexbased;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;

import com.mondego.httpcommunication.Register;
import com.mondego.models.QueryFileProcessor;
import com.mondego.utility.TokensFileReader;
import com.mondego.utility.Util;

import spark.Request;

public class Daemon {
	public static SearchManager sm = null;
	private static final Logger logger = LogManager.getLogger(Daemon.class);
	public static boolean RESET_GTPM = false;
	private static Daemon daemon = null;
	public static String ip = "";
	public static int port = 0;
	public static String outputDir = null;
	public static String dataset_id = null; // In INIT this variable is set to the SHA-256 of the dataset.
	
	HashMap<String, String> queryHeaderMap = new HashMap<String, String>();
	HashMap<String, String> queryLicenseMap = new HashMap<String, String>();
	HashMap<String, String> queryCodeMap = new HashMap<String, String>();

	HashMap<String, String> datasetHeaderMap = new HashMap<String, String>();
	HashMap<String, String> datasetLicenseMap = new HashMap<String, String>();
	HashMap<String, String> datasetCodeMap = new HashMap<String, String>();
	public HttpServer server;

	
	public enum State {
		/**
		 * BUSY - running query
		 * IDLE - waiting for query
		 * INIT - initializing, loading the dataset into memory
		 */
		BUSY, IDLE, INIT
	}
	
	private static State state = State.INIT; // TODO do semaphores need to be used to access and modify the state?
	public static String managerIP = "localhost";
	public static int managerPort = 4567;
	
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
	
	public Daemon(SearchManager theInstance, int daemonPort) {
		sm = theInstance;
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
		if (fileList == null) {
			System.out.println("dataset not found in directory: " + dir.toString());
		}
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
    	setState(State.INIT);
    	dataset_id = calculateDatasetId();
    	
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
		File resultsPath = new File(outputDir);
		return resultsPath;
	}

	public String generateReport(String queryHeaderFilePath, String queryLicenseFilePath, String queryCodeFilePath,
			String datasetHeaderFilePath,
			String datasetLicenseFilePath, String datasetCodeFilePath) {
		loadCsvFileToMap(Paths.get(queryHeaderFilePath), queryHeaderMap); // XXX Assert that the hashes have the same length. (They can have different lengths if not all of the parser files were copied to the client.)
		loadCsvFileToMap(Paths.get(queryLicenseFilePath), queryLicenseMap);
		loadCsvFileToMap(Paths.get(queryCodeFilePath), queryCodeMap);
		loadCsvFileToMap(Paths.get(datasetHeaderFilePath), datasetHeaderMap);
		loadCsvFileToMap(Paths.get(datasetLicenseFilePath), datasetLicenseMap);
		loadCsvFileToMap(Paths.get(datasetCodeFilePath), datasetCodeMap);
		
		System.out.println("size of codemap: " + datasetCodeMap.size());
		
		String report = "";
		
		String css = "<style>\n" + 
		"  div#expand{\n" + 
		"  display:block;\n" + 
		"  }\n" + 
		"\n" + 
		"  .wrapper {\n" + 
		"  width: 100%;\n" + 
		"  border: 1px solid black;\n" + 
		"  overflow: hidden;\n" + 
		"  }\n" + 
		"\n" + 
		"  .first {\n" + 
		"  border: 1px solid grey;\n" + 
		"  width: 49%;\n" + 
		"  float: left;\n" + 
		"  }\n" + 
		"  .second {\n" + 
		"  border: 1px solid grey;\n" + 
		"  width: 49%;\n" + 
		"  float: right;\n" + 
		"  }\n" + 
		"</style>\n";
		
		String js = "<script>\n" + 
		"  function show(id)\n" + 
		"  {\n" + 
		"  if(document.getElementById(id).style.display == 'none')\n" + 
		"  document.getElementById(id).style.display = 'block';\n" + 
		"  else\n" + 
		"  document.getElementById(id).style.display = 'none';\n" + 
		"  }\n" + 
		"</script>\n";
		
		report += css + js;
		
		try {
			File dir = new File(outputDir);
			File[] directoryListing = dir.listFiles();
			int lineno = 0;
			StringBuilder sb = new StringBuilder();
			if (directoryListing != null) {
				for (File file : directoryListing) {
					if (file.isFile()) {
						// read the file
						BufferedReader bufferedReader;

						bufferedReader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);

						String line = bufferedReader.readLine();
						while(line != null){
							String[] components = line.split(",");
							int qpid = 0;
							int qbid = 1;
							int dpid = 2;
							int dbid = 3;

							
							
							
							String rowContent = wrap(components[dpid]) + wrap(components[dbid]) + wrap(components[qpid]) + wrap(components[qbid]) +
									wrap(datasetHeaderMap.get(components[dpid])) +
									wrap(datasetLicenseMap.get(components[dpid])) +
									wrap(queryHeaderMap.get(components[qpid])) +
									wrap(queryLicenseMap.get(components[qpid])) + "</tr><tr>" +
									"<td colspan=8>" + makeCodeBlock("" + lineno, 
											"<pre><code class=\"language-python\">" + // colspan states how many columns this entry may span.
													  StringEscapeUtils.unescapeJava( queryCodeMap.get("u'" + components[qpid])) +  // XXX This probably doesn't unescape the code properly. But, it is a start.
													  "</code></pre>", 
											"<pre><code class=\"language-python\">" + // colspan states how many columns this entry may span.
											  StringEscapeUtils.unescapeJava( datasetCodeMap.get("u'" + components[dpid])) +  // XXX This probably doesn't unescape the code properly. But, it is a start.
											  "</code></pre>") + "</td>";
									
							String row = "<tr class=\\\"none\\\">" + rowContent + "</tr>";
							sb.append(row);

							line = bufferedReader.readLine();
							lineno++;
						}
						bufferedReader.close();
					}
					else {
						logger.error("Found unexpected folder in results directory.");
					}
				}
			} else {
				logger.error("The results directory doesn't seem to exist.");
			}
			report = report.concat(sb.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return report;
	}
	
	public void loadCsvFileToMap(Path csvFile, HashMap<String, String> map) {
		/**
		 * This function is used to load the header and license file into maps.
		 * 
		 * The header file has a structure like this:
		 * cloneId,path,startLineNo,endLineNo
		 * 1,../../data_set/100_modules/0-core-client-1.1.0a5.tar.gz/0-core-client-1.1.0a5/zeroos/core0/client/__init__.py,1,1
		 * 
		 * The license file has a structure like this:
		 * cloneId,license(s)
		 * 1,NONE
		 */
		
		map.clear();
		// based on: http://www.java67.com/2015/08/how-to-load-data-from-csv-file-in-java.html
		try (BufferedReader br = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
			String line = br.readLine();
			while (line != null) {
				String[] parts = line.split(",", 2);

				map.put(parts[0], parts[1]);
				line = br.readLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String wrap(String value) { 
		return "<td>" + value + "</td>";
	}
	
	private String makeCodeBlock(String id, String queryCode, String dataCode) {
		String html = "<button onclick=\"show('" + id + "')\">Show/Hide Code</button>\n" + 
		"\n" + 
		"<div id=\"" + id + "\" class=\"wrapper\" style=\"display: none;\">\n" + 
		"  <div class=\"first\">\n" + 
		"    <div>\n" + 
		"      Query code\n" + 
		"    </div>\n" + 
		"    <div>\n" + 
		"      " + queryCode +
		"    </div>\n" + 
		"  </div>\n" + 
		"  <div class=\"second\">\n" + 
		"    <div>\n" + 
		"      Dataset code\n" + 
		"    </div>\n" + 
		"    <div>\n" + 
		"      " + dataCode +
		"    </div>\n" + 
		"  </div>\n" + 
		"</div>";
		return html;
		}
}
