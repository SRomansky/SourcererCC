/**
 * 
 */
package com.mondego.indexbased;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexWriter;

import com.mondego.models.Bag;
import com.mondego.models.CandidatePair;
import com.mondego.models.CandidateProcessor;
import com.mondego.models.CandidateSearcher;
import com.mondego.models.ClonePair;
import com.mondego.models.CloneReporter;
import com.mondego.models.CloneValidator;
import com.mondego.models.DocumentForInvertedIndex;
import com.mondego.models.InvertedIndexCreator;
import com.mondego.models.QueryBlock;
import com.mondego.models.QueryCandidates;
import com.mondego.models.QueryLineProcessor;
import com.mondego.models.ThreadedChannel;
import com.mondego.noindex.CloneHelper;
import com.mondego.utility.Util;
import com.mondego.validation.TestGson;
import com.mondego.httpcommunication.JerseyServer;
import net.jmatrix.eproperties.EProperties;

/**
 * @author vaibhavsaini
 * 
 */
public class SearchManager {
    public static long clonePairsCount;
    public static CodeSearcher gtpmSearcher;
    public CloneHelper cloneHelper;
    public static String QUERY_DIR_PATH;
    public static String QUERY_SRC_DIR;
    public static String DATASET_DIR;
    public static String DATASET_SRC_DIR;
    public static String WFM_DIR_PATH;
    public static Writer clonesWriter; // writer to write the output
    public static Writer recoveryWriter; // writes the lines processed during
                                         // search. for recovery purpose.
    public static float th; // args[2]
                            // search
    public final static String ACTION_CREATE_SHARDS = "cshard";
    public final static String ACTION_SEARCH = "search";

    public static long timeSpentInSearchingCandidates;
    private Writer reportWriter;
    public static String ACTION;
    public boolean appendToExistingFile;
    TestGson testGson;
    public static final Integer MUL_FACTOR = 100;
    private static final String ACTION_INIT = "init";
    int deletemeCounter = 0;
    public static double ramBufferSizeMB;
    public static ThreadedChannel<String> queryLineQueue;
    public static ThreadedChannel<QueryBlock> queryBlockQueue;
    public static ThreadedChannel<QueryCandidates> queryCandidatesQueue;
    public static ThreadedChannel<CandidatePair> verifyCandidateQueue;
    public static ThreadedChannel<ClonePair> reportCloneQueue;

    public static ThreadedChannel<Bag> bagsToSortQueue;
    public static ThreadedChannel<Bag> bagsToInvertedIndexQueue;
    public static ThreadedChannel<Bag> bagsToForwardIndexQueue;
    public static SearchManager theInstance;
    public static List<IndexWriter> indexerWriters;
    private static EProperties properties = new EProperties();

    public static SearchManager getInstance() {
    	return theInstance;
    }
    public static EProperties getProperties() {
    	return properties;
    }
    
    public static Object lock = new Object();
    private int qlq_thread_count;
    private int qbq_thread_count;
    private int qcq_thread_count;
    private int vcq_thread_count;
    private int rcq_thread_count;
    private int threadsToProcessBagsToSortQueue;
    private int threadToProcessIIQueue;
    private int threadsToProcessFIQueue;
    public static int min_tokens;
    public static int max_tokens;
    public static boolean isGenCandidateStats;
    public static int statusCounter;
    public static boolean isStatusCounterOn;
    public static String NODE_PREFIX;
    public static String OUTPUT_DIR;
    public static int LOG_PROCESSED_LINENUMBER_AFTER_X_LINES;
    public static Map<String, Long> globalWordFreqMap;
    public Set<Long> completedQueries;
    private int max_index_size;
    public static String completedNodes;
    public static int totalNodes = -1;
    private static long RUN_COUNT;
    public static long QUERY_LINES_TO_IGNORE = 0;
    public static String ROOT_DIR;
    private static final Logger logger = LogManager.getLogger(SearchManager.class);
	private static final String ACTION_DAEMON = "daemon";
    public static boolean FATAL_ERROR;
    public static List<String> METRICS_ORDER_IN_SHARDS;
    public static Map<String, Set<Long>> invertedIndex = new ConcurrentHashMap<String, Set<Long>>();
    private static int docId;
    public static Map<Long, DocumentForInvertedIndex> documentsForII = new ConcurrentHashMap<Long, DocumentForInvertedIndex>();

    public static boolean daemon_loaded = false;
    public static Daemon daemon;
	public static String OUTPUT_DIR_TH;
    public JerseyServer server;
    public int daemonPort = 4568;
	public String managerURL = "localhost";
	public int managerPort = 4567;
	
	public static String queryHeaderFilePath = null;
	public static String queryLicenseFilePath = null;
	public static String datasetHeaderFilePath = null;
	public static String datasetLicenseFilePath = null;
    
    public SearchManager(String[] args) throws IOException {
    	this.resetQueryCounters();
    	SearchManager.ACTION = args[0];
    	
    	
        SearchManager.globalWordFreqMap = new HashMap<String, Long>();
        try {

            SearchManager.th = (Float.parseFloat(args[1]) * SearchManager.MUL_FACTOR);

            this.qlq_thread_count = Integer.parseInt(properties.getProperty("QLQ_THREADS", "1"));
            this.qbq_thread_count = Integer.parseInt(properties.getProperty("QBQ_THREADS", "1"));
            this.qcq_thread_count = Integer.parseInt(properties.getProperty("QCQ_THREADS", "1"));
            this.vcq_thread_count = Integer.parseInt(properties.getProperty("VCQ_THREADS", "1"));
            this.rcq_thread_count = Integer.parseInt(properties.getProperty("RCQ_THREADS", "1"));
            SearchManager.min_tokens = Integer.parseInt(properties.getProperty("LEVEL_1_MIN_TOKENS", "65"));
            SearchManager.max_tokens = Integer.parseInt(properties.getProperty("LEVEL_1_MAX_TOKENS", "500000"));
            this.threadsToProcessBagsToSortQueue = Integer.parseInt(properties.getProperty("BTSQ_THREADS", "1"));
            this.threadToProcessIIQueue = Integer.parseInt(properties.getProperty("BTIIQ_THREADS", "1"));
            this.threadsToProcessFIQueue = Integer.parseInt(properties.getProperty("BTFIQ_THREADS", "1"));
            
        } catch (NumberFormatException e) {
            logger.error(e.getMessage() + ", exiting now", e);
            System.exit(1);
        }
        if (SearchManager.ACTION.equals(ACTION_SEARCH)) {
            SearchManager.completedNodes = SearchManager.ROOT_DIR + "nodes_completed.txt";
            this.completedQueries = new HashSet<Long>();

            startQueryThreads();
        } else if (SearchManager.ACTION.equals(ACTION_CREATE_SHARDS)) {
            System.err.println("depricated action: " + ACTION_CREATE_SHARDS);
            System.exit(1);
        }
        
        try {
        	if (args.length >= 3) {
        		daemonPort = Integer.parseInt(args[2]);
        	}
        	else {
        		logger.warn("Failed to parse port numner. Using default port: " + this.daemonPort);
        	}
        } catch (NumberFormatException e) {
        	logger.warn("Failed to parse port numner. Using default port: " + this.daemonPort);
        }
        logger.info("got port: " + daemonPort);
    }


    public static void loadGlobalProperties() {
    	/**
    	 * Side-effect, loads and populates the global properties variable
    	 */
    		logger.info("user.dir is: " + System.getProperty("user.dir"));
        logger.info("root dir is:" + System.getProperty("properties.rootDir"));
        SearchManager.ROOT_DIR = System.getProperty("properties.rootDir");
        FileInputStream fis = null;
        logger.info("reading Q values from properties file");
    		String propertiesPath = System.getProperty("properties.location");
        logger.debug("propertiesPath: " + propertiesPath);
        try {
			fis = new FileInputStream(propertiesPath);
		} catch (FileNotFoundException e1) {
			logger.fatal("ERROR WHILE TRYING TO READ PROPERTIES, EXITING");
            System.exit(1);
		}
        try {
            properties.load(fis);
            SearchManager.DATASET_DIR = SearchManager.ROOT_DIR + properties.getProperty("DATASET_DIR_PATH");
            SearchManager.DATASET_SRC_DIR = properties.getProperty("DATASET_SRC_PATH"); // this is actually a single file
            SearchManager.isGenCandidateStats = Boolean
                    .parseBoolean(properties.getProperty("IS_GEN_CANDIDATE_STATISTICS"));
            SearchManager.isStatusCounterOn = Boolean.parseBoolean(properties.getProperty("IS_STATUS_REPORTER_ON"));
            SearchManager.NODE_PREFIX = properties.getProperty("NODE_PREFIX").toUpperCase();
            SearchManager.OUTPUT_DIR = SearchManager.ROOT_DIR + properties.getProperty("OUTPUT_DIR");
            SearchManager.OUTPUT_DIR_TH = SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR;
            SearchManager.QUERY_DIR_PATH = SearchManager.ROOT_DIR + properties.getProperty("QUERY_DIR_PATH");
            SearchManager.QUERY_SRC_DIR = properties.getProperty("QUERY_SRC_PATH");  // this is actually a single file
            logger.debug("Query path:" + SearchManager.QUERY_DIR_PATH);
            SearchManager.LOG_PROCESSED_LINENUMBER_AFTER_X_LINES = Integer
                    .parseInt(properties.getProperty("LOG_PROCESSED_LINENUMBER_AFTER_X_LINES", "1000"));
            SearchManager.datasetLicenseFilePath = properties.getProperty("DATASET_LICENSE_FILE_PATH");
            SearchManager.datasetHeaderFilePath = properties.getProperty("DATASET_HEADER_FILE_PATH");
            SearchManager.queryHeaderFilePath = properties.getProperty("QUERY_HEADER_FILE_PATH");
            SearchManager.queryLicenseFilePath = properties.getProperty("QUERY_LICENSE_FILE_PATH");

            
            String shardsOrder = properties.getProperty("METRICS_ORDER_IN_SHARDS");
            SearchManager.METRICS_ORDER_IN_SHARDS = new ArrayList<String>();
            for (String metric : shardsOrder.split(",")) {
                SearchManager.METRICS_ORDER_IN_SHARDS.add(metric.trim());
            }
            if (!(SearchManager.METRICS_ORDER_IN_SHARDS.size() > 0)) {
                logger.fatal("ERROR WHILE CREATING METRICS ORDER IN SHARDS, EXTING");
                System.exit(1);
            } else {
                logger.info("METRICS_ORDER_IN_SHARDS created: " + SearchManager.METRICS_ORDER_IN_SHARDS.size());
            }
        } catch (IOException e) {
            logger.error("ERROR READING PROPERTIES FILE, " + e.getMessage());
            System.exit(1);
        } finally {

            if (null != fis) {
                try {
					fis.close();
				} catch (IOException e) {
					logger.error("Failed to close file descriptor");
				}
            }
        }
    	
    }
    
    public static SearchManager loadSearchManagerProperties(String[] args) {
    		try {
			return new SearchManager(args);
		} catch (IOException e) {
			e.printStackTrace();
			logger.error("Failed to parse arguments");
			System.exit(1);
		}
    		return null;
    }
    
    public static void main(String[] args) throws IOException, ParseException, InterruptedException {
        long start_time = System.nanoTime();
        
        loadGlobalProperties();
        theInstance = loadSearchManagerProperties(args);
        logger.debug(SearchManager.NODE_PREFIX + " MAX_TOKENS=" + max_tokens + " MIN_TOKENS=" + min_tokens);

        Util.createDirs(SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR);
        if (SearchManager.ACTION.equalsIgnoreCase(ACTION_DAEMON)) {
        		//String ip = InetAddress.getLocalHost().getHostAddress();  // TODO this might be the wrong ip // It is wrong :(
        		String ip = "127.0.0.1";

        		SearchManager.daemon = new Daemon(theInstance, ip, theInstance.daemonPort); // builds global daemon.

        		// XXX Jersey stuff
        		// Base URI the Grizzly HTTP server will listen on
        		theInstance.server = new JerseyServer("localhost", theInstance.daemonPort); // TODO default config
        		theInstance.server.run();
        		
        		// XXX Jersey stuff
        } else {
        		System.err.println("Depricated action: " + SearchManager.ACTION);
        		System.exit(1);
        }
        long estimatedTime = System.nanoTime() - start_time;
        logger.info("Total run Time: " + (estimatedTime / 1000) + " micors");
        logger.info("number of clone pairs detected: " + SearchManager.clonePairsCount);
        Util.closeOutputFile(theInstance.reportWriter);
        try {
        	Util.closeOutputFile(SearchManager.clonesWriter);
        	Util.closeOutputFile(SearchManager.recoveryWriter);
        } catch (Exception e) {
            logger.error("exception caught in main " + e.getMessage());
        }
        logger.info("completed on " + SearchManager.NODE_PREFIX);
    }

    public void startQueryThreads() {
        logger.info("action: " + SearchManager.ACTION + System.lineSeparator() + "threshold: " + SearchManager.th
                + System.lineSeparator() + " QLQ_THREADS: " + this.qlq_thread_count + " QBQ_THREADS: "
                + this.qbq_thread_count + " QCQ_THREADS: " + this.qcq_thread_count + " VCQ_THREADS: "
                + this.vcq_thread_count + " RCQ_THREADS: " + this.rcq_thread_count + System.lineSeparator());
        SearchManager.queryLineQueue = new ThreadedChannel<String>(this.qlq_thread_count, QueryLineProcessor.class);
        SearchManager.queryBlockQueue = new ThreadedChannel<QueryBlock>(this.qbq_thread_count,
                CandidateSearcher.class);
        SearchManager.queryCandidatesQueue = new ThreadedChannel<QueryCandidates>(this.qcq_thread_count,
                CandidateProcessor.class);
        SearchManager.verifyCandidateQueue = new ThreadedChannel<CandidatePair>(this.vcq_thread_count,
                CloneValidator.class);
        SearchManager.reportCloneQueue = new ThreadedChannel<ClonePair>(this.rcq_thread_count, CloneReporter.class);
        logger.info("action: " + SearchManager.ACTION + System.lineSeparator() + "threshold: " + SearchManager.th
                + System.lineSeparator() + " BQ_THREADS: " + this.threadsToProcessBagsToSortQueue
                + System.lineSeparator() + " SBQ_THREADS: " + this.threadToProcessIIQueue + System.lineSeparator()
                + " IIQ_THREADS: " + this.threadsToProcessFIQueue + System.lineSeparator());
    }
    
    public void stopQueryThreads() {
    	SearchManager.queryLineQueue.shutdown();
        logger.info("shutting down QLQ, " + System.currentTimeMillis());
        logger.info("shutting down QBQ, " + (System.currentTimeMillis()));
        SearchManager.queryBlockQueue.shutdown();
        logger.info("shutting down QCQ, " + System.currentTimeMillis());
        SearchManager.queryCandidatesQueue.shutdown();
        logger.info("shutting down VCQ, " + System.currentTimeMillis());
        SearchManager.verifyCandidateQueue.shutdown();
        logger.info("shutting down RCQ, " + System.currentTimeMillis());
        SearchManager.reportCloneQueue.shutdown();
    }
    
    

	public void resetQueryCounters() {
		SearchManager.clonePairsCount = 0;
        this.cloneHelper = new CloneHelper();
        SearchManager.timeSpentInSearchingCandidates = 0;
        this.appendToExistingFile = true;
        SearchManager.statusCounter = 0;
	}

    public int createIndexes(File candidateFile, int avoidLines) throws FileNotFoundException {
        BufferedReader br = new BufferedReader(new FileReader(candidateFile));
        String line = "";
        long size = 0;
        long gig = 1000000000l;
        long maxMemory = this.max_index_size*gig;
        int completedLines = 0;
        
        ArrayList<String> lines = new ArrayList<String>();
        
        SearchManager.bagsToInvertedIndexQueue = new ThreadedChannel<Bag>(this.threadToProcessIIQueue,
                InvertedIndexCreator.class);
        System.out.println("*** Indexing dataset file: " + candidateFile.toString());
        try {
			while ((line = br.readLine()) != null && line.trim().length() > 0) {
				completedLines++;
				if (completedLines <= avoidLines)
					continue;
				lines.add(line);
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        
        try {
        	br.close();
        } catch (IOException e) {
        	e.printStackTrace();
        }
        
        System.out.println("*** Read: " + lines.size() + " lines.");
//        List<Bag> bagsForIndex = lines.stream()
//        		.map(l -> theInstance.cloneHelper.deserialise(l))
//        		.filter(bag -> bag != null)
//        		.collect(Collectors.toList());
        int parseCount = 0;
        List<Bag> bagsForIndex = new ArrayList<Bag>();
        for (String l : lines) {
        		Bag b = theInstance.cloneHelper.deserialise(l);
        		if (b == null)
        			continue;
        		bagsForIndex.add(b);
        		parseCount++;
        		if (parseCount % 100000 == 0) 
        			System.out.println("*** parsed: " + parseCount + " bags. ***");
        }
        
        System.out.println("*** Lines succesfully bagged: " + bagsForIndex.size());
        	bagsForIndex.stream().forEach(bag -> {
        		try {
					InvertedIndexCreator.index(bag);
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException | SecurityException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        });
        
        SearchManager.bagsToInvertedIndexQueue.shutdown();
        return -1;
    }

    public static synchronized void updateNumCandidates(int num) {
    }

    public static synchronized void updateClonePairsCount(int num) {
        SearchManager.clonePairsCount += num;
    }

    public static synchronized long getNextId() {
        // TODO Auto-generated method stub
        SearchManager.docId++;
        return SearchManager.docId;
    }

}
