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
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import com.mondego.models.ITokensFileProcessor;
import com.mondego.models.InvertedIndexCreator;
import com.mondego.models.QueryBlock;
import com.mondego.models.QueryCandidates;
import com.mondego.models.QueryFileProcessor;
import com.mondego.models.QueryLineProcessor;
import com.mondego.models.Shard;
import com.mondego.models.ThreadedChannel;
import com.mondego.noindex.CloneHelper;
import com.mondego.utility.TokensFileReader;
import com.mondego.utility.Util;
import com.mondego.validation.TestGson;

import net.jmatrix.eproperties.EProperties;
import spark.Spark;

import static spark.Spark.*;

/**
 * @author vaibhavsaini
 * 
 */
public class SearchManager {
    private static long clonePairsCount;
    public static CodeSearcher gtpmSearcher;
    public CloneHelper cloneHelper;
    public static String QUERY_DIR_PATH;
    public static String DATASET_DIR;
    public static String WFM_DIR_PATH;
    public static Writer clonesWriter; // writer to write the output
    public static Writer recoveryWriter; // writes the lines processed during
                                         // search. for recovery purpose.
    public static float th; // args[2]
                            // search
    public final static String ACTION_CREATE_SHARDS = "cshard";
    public final static String ACTION_SEARCH = "search";

    private long timeSpentInProcessResult;
    public static long timeSpentInSearchingCandidates;
    private long timeIndexing;
    private long timeGlobalTokenPositionCreation;
    private long timeSearch;
    private static long numCandidates;
    private Writer reportWriter;
    private long timeTotal;
    public static String ACTION;
    public boolean appendToExistingFile;
    TestGson testGson;
    public static final Integer MUL_FACTOR = 100;
    private static final String ACTION_INIT = "init";
    int deletemeCounter = 0;
    public static double ramBufferSizeMB;
    private long bagsSortTime;
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
    public static List<Shard> shards;
    public Set<Long> completedQueries;
    private boolean isSharding;
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
    public static Map<String, Set<Long>> invertedIndex;
    private static int docId;
    public static Map<Long, DocumentForInvertedIndex> documentsForII;

    public SearchManager(String[] args) throws IOException {
        SearchManager.clonePairsCount = 0;
        this.cloneHelper = new CloneHelper();
        this.timeSpentInProcessResult = 0;
        SearchManager.timeSpentInSearchingCandidates = 0;
        this.timeIndexing = 0;
        this.timeGlobalTokenPositionCreation = 0;
        this.timeSearch = 0;
        SearchManager.numCandidates = 0;
        this.timeTotal = 0;
        this.appendToExistingFile = true;
        this.bagsSortTime = 0;
        SearchManager.ACTION = args[0];
        SearchManager.statusCounter = 0;
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
            this.isSharding = Boolean.parseBoolean(properties.getProperty("IS_SHARDING"));

        } catch (NumberFormatException e) {
            logger.error(e.getMessage() + ", exiting now", e);
            System.exit(1);
        }
        if (SearchManager.ACTION.equals(ACTION_SEARCH)) {
            SearchManager.completedNodes = SearchManager.ROOT_DIR + "nodes_completed.txt";
            this.completedQueries = new HashSet<Long>();

            this.createShards(false);

            logger.info("action: " + SearchManager.ACTION + System.lineSeparator() + "threshold: " + args[1]
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
            logger.info("action: " + SearchManager.ACTION + System.lineSeparator() + "threshold: " + args[1]
                    + System.lineSeparator() + " BQ_THREADS: " + this.threadsToProcessBagsToSortQueue
                    + System.lineSeparator() + " SBQ_THREADS: " + this.threadToProcessIIQueue + System.lineSeparator()
                    + " IIQ_THREADS: " + this.threadsToProcessFIQueue + System.lineSeparator());
        } else if (SearchManager.ACTION.equals(ACTION_CREATE_SHARDS)) {
            // indexerWriters = new ArrayList<IndexWriter>();
            this.createShards(true);
        }
    }

    private void createShards(boolean forWriting) {
        int l1MinTokens = SearchManager.min_tokens;
        int l1MaxTokens = SearchManager.max_tokens;
        int l1ShardId = 1;
        SearchManager.shards = new ArrayList<Shard>();
        if (this.isSharding) {
            String level1ShardSegmentString = properties.getProperty("LEVEL_1_SHARD_MAX_NUM_TOKENS");
            logger.info("level1ShardSegmentString String is : " + level1ShardSegmentString);
            List<String> level1ShardSegments = new ArrayList<String>(
                    Arrays.asList(level1ShardSegmentString.split(",")));
            level1ShardSegments.add(SearchManager.max_tokens + ""); // add the
                                                                    // last
                                                                    // shard
            for (String segment : level1ShardSegments) {
                // create shards
                l1MaxTokens = Integer.parseInt(segment);
                String l1Path = l1ShardId + "";
                Shard level1Shard = null;

                String level2ShardSegmentString = properties.getProperty("LEVEL_2_SHARD_MAX_NUM_TOKENS");
                if (null != level2ShardSegmentString) {
                    level1Shard = new Shard(l1ShardId, l1MinTokens, l1MaxTokens, l1Path, false);
                    this.createSubShards(l1Path, level1Shard, 2, forWriting);
                } else {
                    level1Shard = new Shard(l1ShardId, l1MinTokens, l1MaxTokens, l1Path, forWriting);
                }
                SearchManager.shards.add(level1Shard);
                l1MinTokens = l1MaxTokens + 1;
                l1ShardId++;
            }
        } else {
            Shard shard = new Shard(l1ShardId, SearchManager.min_tokens, SearchManager.max_tokens, l1ShardId + "",
                    forWriting);
            SearchManager.shards.add(shard);
        }
        logger.debug("Number of Top level shards created: " + SearchManager.shards.size());
    }

    private void createSubShards(String parentShardPath, Shard parentShard, int level, boolean forWriting) {
        String shardSegmentString = properties.getProperty("LEVEL_" + level + "_SHARD_MAX_NUM_TOKENS");
        logger.info(level + " Segment String is : " + shardSegmentString);

        int metricMin = Integer.parseInt(properties.getProperty("LEVEL_" + level + "_MIN_TOKENS"));
        int metricMax = 0;
        int shardId = 1;
        List<String> shardSegments = new ArrayList<String>(Arrays.asList(shardSegmentString.split(",")));
        shardSegments.add(properties.getProperty("LEVEL_" + level + "_MAX_TOKENS")); // add
        // the
        // last
        // shard
        for (String segment : shardSegments) {
            // create shards
            metricMax = Integer.parseInt(segment);
            String shardPath = parentShardPath + "/" + shardId;
            int nextLevel = level + 1;
            String nextShardSegmentString = properties.getProperty("LEVEL_" + nextLevel + "_SHARD_MAX_NUM_TOKENS");
            Shard shard = null;
            if (null != nextShardSegmentString) {
                shard = new Shard(shardId, metricMin, metricMax, shardPath, false);
                this.createSubShards(shardPath, shard, nextLevel, forWriting);
            } else {
                shard = new Shard(shardId, metricMin, metricMax, shardPath, forWriting);
            }

            parentShard.subShards.add(shard);
            metricMin = metricMax + 1;
            shardId++;
        }
    }

    // this bag needs to be indexed in following shards
    public static List<Shard> getShards(Bag bag) {
        List<Shard> shardsToReturn = new ArrayList<Shard>();
        int level = 0;
        for (Shard shard : SearchManager.shards)
            if (bag.metrics.get(SearchManager.METRICS_ORDER_IN_SHARDS.get(level)) >= shard.getMinMetricValueToIndex()
                    && bag.metrics.get(SearchManager.METRICS_ORDER_IN_SHARDS.get(level)) <= shard
                            .getMaxMetricValueToIndex()) {
                SearchManager.getSubShards(bag, shard, level + 1, shardsToReturn);
            }
        return shardsToReturn;
    }

    private static void getSubShards(Bag bag, Shard parentShard, int level, List<Shard> shardsToReturn) {
        if (parentShard.subShards.size() > 0) {
            for (Shard shard : parentShard.subShards) {
                if (bag.metrics.get(SearchManager.METRICS_ORDER_IN_SHARDS.get(level)) >= shard
                        .getMinMetricValueToIndex()
                        && bag.metrics.get(SearchManager.METRICS_ORDER_IN_SHARDS.get(level)) <= shard
                                .getMaxMetricValueToIndex()) {
                    SearchManager.getSubShards(bag, shard, level + 1, shardsToReturn);
                }
            }
        } else {
            shardsToReturn.add(parentShard);
        }
    }

    // This query needs to be directed to the following shard
    public static Shard getShardToSearch(Bag bag) {
        Shard shard = SearchManager.getRootShard(bag);
        int level = 1;
        if (null != shard) {
            return SearchManager.getShardRecursive(bag, shard, level);
        } else {
            return shard;
        }
    }

    public static Shard getRootShard(Bag bag) {

        int low = 0;
        int high = SearchManager.shards.size() - 1;
        int mid = (low + high) / 2;
        Shard shard = null;
        while (low <= high) {
            shard = SearchManager.shards.get(mid);
            if (bag.getSize() >= shard.getMinMetricValue() && bag.getSize() <= shard.getMaxMetricValue()) {
                break;
            } else {
                if (bag.getSize() < shard.getMinMetricValue()) {
                    high = mid - 1;
                } else if (bag.getSize() > shard.getMaxMetricValue()) {
                    low = mid + 1;
                }
                mid = (low + high) / 2;
            }
        }
        return shard;
    }

    public static Shard getShardRecursive(Bag bag, Shard parentShard, int level) {
        if (parentShard.subShards.size() > 0) {
            int low = 0;
            int high = parentShard.subShards.size() - 1;
            int mid = (low + high) / 2;
            Shard shard = null;
            while (low <= high) {
                shard = parentShard.subShards.get(mid);
                if (bag.metrics.get(SearchManager.METRICS_ORDER_IN_SHARDS.get(level)) >= shard.getMinMetricValue()
                        && bag.metrics.get(SearchManager.METRICS_ORDER_IN_SHARDS.get(level)) <= shard
                                .getMaxMetricValue()) {
                    return SearchManager.getShardRecursive(bag, shard, level + 1);
                } else {
                    if (bag.metrics.get(SearchManager.METRICS_ORDER_IN_SHARDS.get(level)) < shard.getMinMetricValue()) {
                        high = mid - 1;
                    } else if (bag.metrics.get(SearchManager.METRICS_ORDER_IN_SHARDS.get(level)) > shard
                            .getMaxMetricValue()) {
                        low = mid + 1;
                    }
                    mid = (low + high) / 2;

                }
            }
            return shard;
        } else {
            return parentShard;
        }
    }

    public static void main(String[] args) throws IOException, ParseException, InterruptedException {
        long start_time = System.nanoTime();
        logger.info("user.dir is: " + System.getProperty("user.dir"));
        logger.info("root dir is:" + System.getProperty("properties.rootDir"));
        SearchManager.ROOT_DIR = System.getProperty("properties.rootDir");
        FileInputStream fis = null;
        logger.info("reading Q values from properties file");
        String propertiesPath = System.getProperty("properties.location");
        logger.debug("propertiesPath: " + propertiesPath);
        fis = new FileInputStream(propertiesPath);
        try {
            properties.load(fis);
            String[] params = new String[2];
            params[0] = args[0];
            params[1] = args[1];
            SearchManager.DATASET_DIR = SearchManager.ROOT_DIR + properties.getProperty("DATASET_DIR_PATH");
            SearchManager.isGenCandidateStats = Boolean
                    .parseBoolean(properties.getProperty("IS_GEN_CANDIDATE_STATISTICS"));
            SearchManager.isStatusCounterOn = Boolean.parseBoolean(properties.getProperty("IS_STATUS_REPORTER_ON"));
            SearchManager.NODE_PREFIX = properties.getProperty("NODE_PREFIX").toUpperCase();
            SearchManager.OUTPUT_DIR = SearchManager.ROOT_DIR + properties.getProperty("OUTPUT_DIR");
            SearchManager.QUERY_DIR_PATH = SearchManager.ROOT_DIR + properties.getProperty("QUERY_DIR_PATH");
            logger.debug("Query path:" + SearchManager.QUERY_DIR_PATH);
            SearchManager.LOG_PROCESSED_LINENUMBER_AFTER_X_LINES = Integer
                    .parseInt(properties.getProperty("LOG_PROCESSED_LINENUMBER_AFTER_X_LINES", "1000"));
            theInstance = new SearchManager(params);
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
                fis.close();
            }
        }
        logger.debug(SearchManager.NODE_PREFIX + " MAX_TOKENS=" + max_tokens + " MIN_TOKENS=" + min_tokens);

        Util.createDirs(SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR);
        if (SearchManager.ACTION.equalsIgnoreCase(ACTION_CREATE_SHARDS)) {
            long begin_time = System.currentTimeMillis();
            theInstance.doPartitions();
            for (Shard shard : SearchManager.shards) {
                shard.closeWriters();
            }
            logger.info("indexing over!");
            theInstance.timeIndexing = System.currentTimeMillis() - begin_time;
        } else if (SearchManager.ACTION.equalsIgnoreCase(ACTION_SEARCH)) {
            long timeStartSearch = System.currentTimeMillis();
            theInstance.initSearchEnv();

            // logger.info(NODE_PREFIX + " Starting to search");
            // theInstance.populateCompletedQueries();
            // theInstance.findCandidates();

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
            theInstance.timeSearch = System.currentTimeMillis() - timeStartSearch;
            signOffNode();
            if (SearchManager.NODE_PREFIX.equals("NODE_1")) {
                logger.debug("NODES COMPLETED SO FAR: " + getCompletedNodes());
                while (true) {
                    if (allNodesCompleted()) {
                        theInstance.backupInput();
                        break;
                    } else {
                        logger.info("waiting for all nodes to complete, check " + SearchManager.completedNodes
                                + " file to see the list of completed nodes");
                        Thread.sleep(4000);
                    }
                }
            }
        } else if (SearchManager.ACTION.equalsIgnoreCase(ACTION_INIT)) {
            WordFrequencyStore wfs = new WordFrequencyStore();
            wfs.populateLocalWordFreqMap(); // read query files and populate TreeMap with lucene configuration
        } else if (SearchManager.ACTION.equalsIgnoreCase(ACTION_DAEMON)) {
        	// TODO start a background job to register the shard with the data manager
        	theInstance.startDaemon();
        	
        	Spark.get("/hello", (req, res) -> "Hello World");
        	
        	Spark.post("/halt", (req, res) -> {
        		Spark.stop();
        		System.exit(0);
        		return "Stopping process.";
        		// TODO is there a better way to shutdown Spark and the jvm?
        	});
        	
        	Spark.get("/status", (req, res) -> {
        		// TODO return a message conveying the current state of the shard.
        		return "Status command is not implemented yet.";
        	});
        	
        	Spark.post("/query", (req, res) -> {
        		// TODO get post data
        		// TODO save a copy of post data?
        		// TODO daemon will send a message to manager about results?
        		// TODO daemon will have a command to return the last results?
        		
        		return "Query command is not implemented yet.";
        	});
        }
        long estimatedTime = System.nanoTime() - start_time;
        logger.info("Total run Time: " + (estimatedTime / 1000) + " micors");
        logger.info("number of clone pairs detected: " + SearchManager.clonePairsCount);
        theInstance.timeTotal = estimatedTime;
        // theInstance.genReport();
        Util.closeOutputFile(theInstance.reportWriter);
        try {
            Util.closeOutputFile(SearchManager.clonesWriter);
            Util.closeOutputFile(SearchManager.recoveryWriter);
            if (SearchManager.ACTION.equals(ACTION_SEARCH)) {
                theInstance.backupOutput();
            }
        } catch (Exception e) {
            logger.error("exception caught in main " + e.getMessage());
        }
        logger.info("completed on " + SearchManager.NODE_PREFIX);
    }

    private void startDaemon() {
    	/*
    	 * Start the daemon and load the dataset into memory if it exists.
    	 */
    	//SearchManager.DATASET_DIR
    	
    	// init, cshards, some of search
    	// code from findCandidates(shard)
		try {
			// no shardFolderPath, use File datasetDir = new
			// File(SearchManager.DATASET_DIR);
			String shardFolderPath = SearchManager.ROOT_DIR + "/index/" + shard.indexPath;
			File queryFile = new File(shardFolderPath + "/query.file");
			File candidateFile = new File(shardFolderPath + "/candidates.file"); // TODO output file?
			QueryFileProcessor queryFileProcessor = new QueryFileProcessor();
			logger.info("Query File: " + queryFile.getAbsolutePath());
			String filename = queryFile.getName().replaceFirst("[.][^.]+$", "");
			try {
				// TODO output file.
				String cloneReportFileName = SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR
						+ "/" + filename + "clones_index_WITH_FILTER.txt";
				File cloneReportFile = new File(cloneReportFileName);
				if (cloneReportFile.exists()) {
					theInstance.appendToExistingFile = true;
				} else {
					theInstance.appendToExistingFile = false;
				}
				// TODO output file writer.
				SearchManager.clonesWriter = Util.openFile(SearchManager.OUTPUT_DIR
						+ SearchManager.th / SearchManager.MUL_FACTOR + "/" + filename + "clones_index_WITH_FILTER.txt",
						theInstance.appendToExistingFile);
				// TODO recoveryWriter
				SearchManager.recoveryWriter = Util.openFile(
						SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR + "/recovery.txt",
						false);
			} catch (IOException e) {
				logger.error(e.getMessage() + " exiting");
				System.exit(1);
			}
			int completedLines = 0;
			while (true) {
				logger.info("creating indexes for " + candidateFile.getAbsolutePath());
				completedLines = theInstance.createIndexes(candidateFile, completedLines);
				logger.info("indexes created");
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
				logger.debug("COMPLETED LINES: " + completedLines);
				if (completedLines == -1) {
					break;
				}
			}
			// TODO what does createIndexes do with the candidateFile? Is it like the line
			// processor from cshard?
			// reads the candidateFile into the invertedIndexQueue
			// TODO who reads invertedIndex and documentsForII?
			SearchManager.invertedIndex = new ConcurrentHashMap<String, Set<Long>>();
			SearchManager.documentsForII = new ConcurrentHashMap<Long, DocumentForInvertedIndex>();
			BufferedReader br = new BufferedReader(new FileReader(candidateFile));
			String line = "";
			long size = 0;
			long gig = 1000000000l;
			long maxMemory = this.max_index_size * gig;
			int completedLines = 0;
			try {
				// SearchManager.bagsToSortQueue = new ThreadedChannel<Bag>(
				// this.threadsToProcessBagsToSortQueue, BagSorter.class);
				SearchManager.bagsToInvertedIndexQueue = new ThreadedChannel<Bag>(this.threadToProcessIIQueue,
						InvertedIndexCreator.class);
				while ((line = br.readLine()) != null && line.trim().length() > 0) {
					completedLines++;
					if (completedLines <= avoidLines) {
						continue;
					}
					Bag bag = theInstance.cloneHelper.deserialise(line);
					if (null != bag) {
						size = size + (bag.getNumUniqueTokens() * 300); // approximate mem utilization. 1 key value pair
																		// = 300 bytes
						logger.debug("indexing " + completedLines + " bag: " + bag + ", mem: " + size + " bytes");
						SearchManager.bagsToInvertedIndexQueue.send(bag);
						if (size >= maxMemory) {
							return completedLines;
						}
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				// SearchManager.bagsToSortQueue.shutdown();
				SearchManager.bagsToInvertedIndexQueue.shutdown();
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		// return -1;

		} catch (FileNotFoundException e) {
			logger.error(e.getMessage() + "exiting");
			System.exit(1);
		}
	}else {
		logger.fatal("FATAL error detected. exiting now"); // TODO what
		System.exit(1); // TODO don't system.exit(). Try to recover gracefully
	}}
    	/* this code from doPartitions loads the dataset and places it into the shard directories. But, I didn't see it load the dataset for the purpose of searching for clones.
    	SearchManager.gtpmSearcher = new CodeSearcher(Util.GTPM_INDEX_DIR, "key");
        File datasetDir = new File(SearchManager.DATASET_DIR);
        if (datasetDir.isDirectory()) {
            logger.info("Reading dataset from directory: " + datasetDir.getAbsolutePath());
            for (File inputFile : Util.getAllFilesRecur(datasetDir)) {
                logger.info("indexing dataset file: " + inputFile.getAbsolutePath());
                try {
                    TokensFileReader tfr = new TokensFileReader(SearchManager.NODE_PREFIX, inputFile,
                            SearchManager.max_tokens, datasetITokensFileProcessor);
                    tfr.read();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    logger.error(SearchManager.NODE_PREFIX + ", something nasty, exiting. counter:"
                            + SearchManager.statusCounter);
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        } else {
        	// TODO update the webservice status. The datasetDir is invalid. Try picking a directory instead of a file.
        }
        */
	}

	private void readAndUpdateRunMetadata() {

        this.readRunMetadata();
        // update the runMetadata
        SearchManager.RUN_COUNT += 1;
        this.updateRunMetadata(SearchManager.RUN_COUNT + "");
    }

    private void readRunMetadata() {
        File f = new File(Util.RUN_METADATA);
        BufferedReader br = null;
        if (f.exists()) {
            logger.debug(Util.RUN_METADATA + " file exists, reading it to get the run metadata");
            try {
                br = Util.getReader(f);
                String line = br.readLine().trim();
                if (!line.isEmpty()) {
                    SearchManager.RUN_COUNT = Long.parseLong(line);
                    logger.debug("last run count was: " + SearchManager.RUN_COUNT);
                } else {
                    SearchManager.RUN_COUNT = 1;
                }
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (NumberFormatException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            SearchManager.RUN_COUNT = 1;
        }

    }

    private void updateRunMetadata(String text) {
        File f = new File(Util.RUN_METADATA);
        try {
            Writer writer = Util.openFile(f, false);
            Util.writeToFile(writer, text, true);
            Util.closeOutputFile(writer);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void backupOutput() throws IOException {
        theInstance.readRunMetadata();
        String destDir = Util.OUTPUT_BACKUP_DIR + "/" + SearchManager.RUN_COUNT + "/" + SearchManager.NODE_PREFIX;
        Util.createDirs(destDir); // creates if it doesn't exist
        String sourceDir = SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR;
        logger.debug("moving " + sourceDir + " to " + destDir);
        FileUtils.copyDirectory(new File(sourceDir), new File(destDir), true); // copy
                                                                               // the
                                                                               // output
                                                                               // folder
                                                                               // instead
                                                                               // of
                                                                               // moving
                                                                               // it.
    }

    private void backupInput() {
        String previousDataFolder = SearchManager.DATASET_DIR + "/oldData/";
        Util.createDirs(previousDataFolder);
        File sourceDataFile = new File(SearchManager.DATASET_DIR + "/" + Util.QUERY_FILE_NAME);
        String targetFileName = previousDataFolder + System.currentTimeMillis() + "_" + Util.QUERY_FILE_NAME;
        sourceDataFile.renameTo(new File(targetFileName));
        File completedNodesFile = new File(SearchManager.completedNodes);
        completedNodesFile.delete();// delete the completedNodes file
    }

    private static boolean allNodesCompleted() {
        return 0 == (getNodes() - getCompletedNodes());
    }

    private static int getCompletedNodes() {
        File completedNodeFile = new File(SearchManager.completedNodes);
        FileLock lock = null;
        int count = 0;
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(completedNodeFile, "rw");
            FileChannel channel = raf.getChannel();
            try {
                lock = channel.lock();
                while (raf.readLine() != null) {
                    count++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    lock.release();
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (FileNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return count;
    }

    private static int getNodes() {
        if (-1 == SearchManager.totalNodes) {
            File searchMertadaFile = new File(Util.SEARCH_METADATA);
            try {
                BufferedReader br = Util.getReader(searchMertadaFile);
                String line = br.readLine();
                if (null != line) {
                    SearchManager.totalNodes = Integer.parseInt(line.trim());
                    return SearchManager.totalNodes;
                }
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return SearchManager.totalNodes;
    }

    private static void signOffNode() {
        logger.debug("signing off " + SearchManager.NODE_PREFIX);
        File file = new File(SearchManager.completedNodes);
        FileLock lock = null;
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "rwd");
            FileChannel channel = raf.getChannel();
            try {
                lock = channel.lock();
                logger.debug("lock obtained? " + lock);
                ByteBuffer outBuffer = ByteBuffer.allocate(100);
                outBuffer.clear();
                String endidStr = SearchManager.NODE_PREFIX + "\n";
                outBuffer.put(endidStr.getBytes());
                outBuffer.flip();
                // System.out.println(new String(outBuffer.array()));
                channel.write(outBuffer, raf.length());
                channel.force(false);
            } catch (IOException e) {
                e.printStackTrace();
                logger.error(e.getMessage());
            } finally {
                try {
                    lock.release();
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error(e.getMessage());
                }
            }
        } catch (FileNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            logger.error(e1.getMessage());
        }

    }

    private void populateCompletedQueries() {
        // TODO Auto-generated method stub
        BufferedReader br = null;
        String filename = SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR + "/recovery.txt";
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    if (line.trim().length() > 0) {
                        SearchManager.QUERY_LINES_TO_IGNORE = Long.parseLong(line.trim());
                    }
                } catch (NumberFormatException e) {
                    logger.error(
                            SearchManager.NODE_PREFIX + ", error in parsing:" + e.getMessage() + ", line: " + line);
                    e.printStackTrace();
                }
            }
        } catch (FileNotFoundException e) {
            logger.error(SearchManager.NODE_PREFIX + ", " + filename + " not found");
        } catch (UnsupportedEncodingException e) {
            logger.error(SearchManager.NODE_PREFIX + ", error in populateCompleteQueries" + e.getMessage());
            logger.error("stacktrace: ", e);
        } catch (IOException e) {
            logger.error(SearchManager.NODE_PREFIX + ", error in populateCompleteQueries IO" + e.getMessage());
            logger.error("stacktrace: ", e);
        }
        logger.info("lines to ignore in query file: " + SearchManager.QUERY_LINES_TO_IGNORE);
    }

    private void genReport() {
        String header = "";
        if (!this.appendToExistingFile) {
            header = "index_time, " + "globalTokenPositionCreationTime,num_candidates, "
                    + "num_clonePairs, total_run_time, searchTime,"
                    + "timeSpentInSearchingCandidates,timeSpentInProcessResult,"
                    + "operation,sortTime_during_indexing\n";
        }
        header += this.timeIndexing + ",";
        header += this.timeGlobalTokenPositionCreation + ",";
        header += SearchManager.numCandidates + ",";
        header += SearchManager.clonePairsCount + ",";
        header += this.timeTotal + ",";
        header += this.timeSearch + ",";
        header += SearchManager.timeSpentInSearchingCandidates + ",";
        header += this.timeSpentInProcessResult + ",";
        if (SearchManager.ACTION.equalsIgnoreCase("index")) {
            header += SearchManager.ACTION + ",";
            header += this.bagsSortTime;
        } else {
            header += SearchManager.ACTION;
        }
        Util.writeToFile(this.reportWriter, header, true);
    }

    private void doPartitions() throws InterruptedException, FileNotFoundException {
        SearchManager.gtpmSearcher = new CodeSearcher(Util.GTPM_INDEX_DIR, "key");
        File datasetDir = new File(SearchManager.DATASET_DIR);
        if (datasetDir.isDirectory()) {
            logger.info("Directory: " + datasetDir.getAbsolutePath());
            for (File inputFile : Util.getAllFilesRecur(datasetDir)) {
                logger.info("indexing file: " + inputFile.getAbsolutePath());
                try {
                    TokensFileReader tfr = new TokensFileReader(SearchManager.NODE_PREFIX, inputFile,
                            SearchManager.max_tokens, new ITokensFileProcessor() {
                                public void processLine(String line) throws ParseException {
                                    if (!SearchManager.FATAL_ERROR) {
                                        Bag bag = cloneHelper.deserialise(line);
                                        if (null == bag || bag.getSize() < SearchManager.min_tokens) {
                                            if (null == bag) {
                                                logger.debug(SearchManager.NODE_PREFIX
                                                        + " empty bag, ignoring. statusCounter= "
                                                        + SearchManager.statusCounter);
                                            } else {
                                                logger.debug(SearchManager.NODE_PREFIX + " ignoring bag " + ", " + bag
                                                        + ", statusCounter=" + SearchManager.statusCounter);
                                            }
                                            return; // ignore this bag.
                                        }
                                        Util.sortBag(bag);
                                        List<Shard> shards = SearchManager.getShards(bag);
                                        String bagString = bag.serialize();
                                        for (Shard shard : shards) {
                                            Util.writeToFile(shard.candidateFileWriter, bagString, true);
                                            shard.size++;
                                        }
                                        Shard shard = SearchManager.getShardToSearch(bag);
                                        if (null != shard) {
                                            Util.writeToFile(shard.queryFileWriter, bagString, true);
                                        }
                                    } else {
                                        logger.fatal("FATAL error detected. exiting now");
                                        System.exit(1);
                                    }
                                }
                            });
                    tfr.read();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
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

    private void findCandidates(Shard shard) throws InterruptedException {
        try {
            String shardFolderPath = SearchManager.ROOT_DIR + "/index/" + shard.indexPath;
            File queryFile = new File(shardFolderPath + "/query.file");
            File candidateFile = new File(shardFolderPath + "/candidates.file");
            QueryFileProcessor queryFileProcessor = new QueryFileProcessor();
            logger.info("Query File: " + queryFile.getAbsolutePath());
            String filename = queryFile.getName().replaceFirst("[.][^.]+$", "");
            try {
                String cloneReportFileName = SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR
                        + "/" + filename + "clones_index_WITH_FILTER.txt";
                File cloneReportFile = new File(cloneReportFileName);
                if (cloneReportFile.exists()) {
                    this.appendToExistingFile = true;
                } else {
                    this.appendToExistingFile = false;
                }
                SearchManager.clonesWriter = Util.openFile(SearchManager.OUTPUT_DIR
                        + SearchManager.th / SearchManager.MUL_FACTOR + "/" + filename + "clones_index_WITH_FILTER.txt",
                        this.appendToExistingFile);
                // recoveryWriter
                SearchManager.recoveryWriter = Util.openFile(
                        SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR + "/recovery.txt",
                        false);
            } catch (IOException e) {
                logger.error(e.getMessage() + " exiting");
                System.exit(1);
            }
            int completedLines = 0;
            while (true) {
                logger.info("creating indexes for " + candidateFile.getAbsolutePath());
                completedLines = this.createIndexes(candidateFile, completedLines);
                logger.info("indexes created");
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
                logger.debug("COMPLETED LINES: " + completedLines);
                if (completedLines == -1) {
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage() + "exiting");
            System.exit(1);
        }
    }

    private int createIndexes(File candidateFile, int avoidLines) throws FileNotFoundException {
        SearchManager.invertedIndex = new ConcurrentHashMap<String, Set<Long>>();
        SearchManager.documentsForII = new ConcurrentHashMap<Long, DocumentForInvertedIndex>();
        BufferedReader br = new BufferedReader(new FileReader(candidateFile));
        String line = "";
        long size = 0;
        long gig = 1000000000l;
        long maxMemory = this.max_index_size*gig;
        int completedLines = 0;
        try {
            // SearchManager.bagsToSortQueue = new ThreadedChannel<Bag>(
            // this.threadsToProcessBagsToSortQueue, BagSorter.class);
            SearchManager.bagsToInvertedIndexQueue = new ThreadedChannel<Bag>(this.threadToProcessIIQueue,
                    InvertedIndexCreator.class);
            while ((line = br.readLine()) != null && line.trim().length() > 0) {
                completedLines++;
                if (completedLines <= avoidLines) {
                    continue;
                }
                Bag bag = theInstance.cloneHelper.deserialise(line);
                if (null != bag) {
                    size = size + (bag.getNumUniqueTokens() * 300); // approximate mem utilization. 1 key value pair = 300 bytes
                    logger.debug("indexing "+ completedLines + " bag: "+ bag + ", mem: "+size + " bytes");
                    SearchManager.bagsToInvertedIndexQueue.send(bag);
                    if (size >= maxMemory) {
                        return completedLines;
                    }
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            // SearchManager.bagsToSortQueue.shutdown();
            SearchManager.bagsToInvertedIndexQueue.shutdown();
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    private void initSearchEnv() {
        if (SearchManager.NODE_PREFIX.equals("NODE_1")) {
            theInstance.readAndUpdateRunMetadata();
            File completedNodeFile = new File(SearchManager.completedNodes);
            if (completedNodeFile.exists()) {
                logger.debug(completedNodeFile.getAbsolutePath() + "exists, deleting it.");
                completedNodeFile.delete();
            }
        }

        // SearchManager.gtpmSearcher = new CodeSearcher(Util.GTPM_INDEX_DIR,
        // "key");
        Set<Integer> searchShards = new HashSet<Integer>();
        String searchShardsString = properties.getProperty("SEARCH_SHARDS", "ALL");
        if (searchShardsString.equalsIgnoreCase("ALL")) {
            searchShardsString = null;
        }
        if (null != searchShardsString) {
            String[] searchShardsArray = searchShardsString.split(",");
            for (String shardId : searchShardsArray) {
                searchShards.add(Integer.parseInt(shardId));
            }
        }
        for (Shard shard : SearchManager.shards) {
            if (searchShards.size() > 0) {
                if (searchShards.contains(shard.getId())) {
                    this.setupSearchers(shard);
                }
            } else {
                // search on all shards.
                this.setupSearchers(shard);
            }

        }
    }

    private void setupSearchers(Shard shard) {
        this.max_index_size = Integer.parseInt(properties.getProperty("MAX_INDEX_SIZE", "12"));
        if (shard.subShards.size() > 0) {
            for (Shard subShard : shard.subShards) {
                this.setupSearchers(subShard);
            }
        } else {
            try {
                this.findCandidates(shard);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static synchronized void updateNumCandidates(int num) {
        SearchManager.numCandidates += num;
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
