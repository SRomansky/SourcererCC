package com.mondego.indexbased;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mondego.models.QueryFileProcessor;
import com.mondego.utility.TokensFileReader;
import com.mondego.utility.Util;

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
}
