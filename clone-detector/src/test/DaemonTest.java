package test;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mondego.httpcommunication.JerseyServer;
import com.mondego.indexbased.Daemon;
import com.mondego.indexbased.SearchManager;
import com.mondego.noindex.CloneHelper;
import com.mondego.utility.Util;

import net.jmatrix.eproperties.EProperties;

public class DaemonTest {
	// Run the tests with gradle.
	
	// XXX Query time for one project
	// XXX Parsing time for x, 10 * x, 100 * x projects
	
	
	// XXX Add the test data to the docker images?
	// XXX Each test should be independent and should be runnable regardless of execution order.

	
	/**
	 * Check the daemon can start without crashing.
	 */
	@Test
	public void testDaemonCreate() {
		createDaemon();
	}
	
	static SearchManager createDaemon() {
		// TODO get the gradle jvm parameters to work
		// TODO get the gradle.properties file to load
		// TODO add the gradle.properties file to git
		
		
		// jvm args are supposed to be set by gradle -Xms10g -Xmx10g see gradle.properties in clone-detector
		// clone-detector/src/test/input contains a mock format of clone-detector/input
		// setup command line parameters
		String sourcererCCPath = System.getProperty("user.dir"); // <path>/SourcererCC/clone-detector
		// update the path to the test dataset
		String testDataPath = sourcererCCPath + "/src/test/input/";
		String testOutputPath = sourcererCCPath + "/src/test/output/";
		String testQueryPath = sourcererCCPath + "/src/test/query/"; // XXX Warning, files in this directory are over-written.
		System.setProperty("properties.location", sourcererCCPath + "/NODE_1/sourcerer-cc.properties");
		System.setProperty("properties.rootDir", sourcererCCPath + "/");

		// code below here copied from global
	    SearchManager theInstance; // this is already in a getter?
		
		// code below here copied from main
		SearchManager.loadGlobalProperties();
		String[] args = {"daemon", "8", "4568"};
        theInstance = SearchManager.loadSearchManagerProperties(args);
        assert(theInstance != null);
        // TODO replace the properties values with mocked test values e.g. test directories.

        /**
         * Replace properties variables related to loading the dataset and storing the queryset.
         * 
        QUERY_DIR_PATH=query/dataset
		QUERY_SRC_PATH=input/query.code # This file is rewritten when a query is submitted to the client
		OUTPUT_DIR=${NODE_PREFIX}/output
		DATASET_DIR_PATH=input/dataset
		DATASET_SRC_PATH=input/test.code
		DATASET_HEADER_FILE_PATH=input/test.header
		DATASET_LICENSE_FILE_PATH=input/test.license
		QUERY_HEADER_FILE_PATH=input/query.header   # This file is rewritten when a query is submitted to the client
		QUERY_LICENSE_FILE_PATH=input/query.license # This file is rewritten when a query is submitted to the client
		
		 *
		 * Above are the variables from the sourcerer.properties files.
		 * For the dataset:
		 * DATASET_DIR_PATH -- a file path to the folder containing .tokens files
		 * DATASET_SRC_PATH -- a file path to the .code file
		 * DATASET_HEADER_FILE_PATH -- a file path to your .header file
		 * DATASET_LICENSE_FILE_PATH -- a file path to your .license file
		 * 
		 * Respectively, variables exist for the queryset. Except that, the query files are
		 * given in a POST message and the daemon over-writes these variables when a POST is
		 * received. Whereas, the dataset variables are provided by the user and are only
		 * writable by the user.
		 * 
		 * For the output:
		 * OUTPUT_DIR -- a path to the folder which will contain your detected clones text file.
         */
        EProperties properties = SearchManager.getProperties();
        
        
        SearchManager.DATASET_DIR = testDataPath + "/dataset";
        SearchManager.DATASET_SRC_DIR = testDataPath + "/test.code"; // this is actually a single file, this is the code file //TODO rename this variable.
        SearchManager.OUTPUT_DIR = testOutputPath;  // TODO move to /tmp?  // TODO erase it after testing?
        SearchManager.OUTPUT_DIR_TH = testOutputPath + SearchManager.th / SearchManager.MUL_FACTOR;
        SearchManager.QUERY_DIR_PATH = testQueryPath + "/dataset";
        SearchManager.QUERY_SRC_DIR = testQueryPath + "/query.code";  // this is actually a single file //TODO rename this variable if it is the query code file.
        SearchManager.datasetLicenseFilePath = testDataPath + "/test.license";
        SearchManager.datasetHeaderFilePath = testDataPath + "/test.header";  // Where is the code file?
        SearchManager.queryHeaderFilePath = testQueryPath + "/query.header";
        SearchManager.queryLicenseFilePath = testQueryPath + "/query.license";
        
        Util.createDirs(SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR);

		String ip = "127.0.0.1";  // TODO figure out how to specify this on command line

		theInstance.daemon = new Daemon(theInstance, ip, theInstance.daemonPort); // builds global daemon.

		theInstance.theInstance = theInstance;
		
		return theInstance;
	}
	
	/**
	 * Check the daemon can start and that it goes into the BUSY state while it loads the dataset.
	 * Then check that the daemon goes into the IDLE state after a given time period, from loading the dataset.
	 */
	
	/**
	 * Check properties of the loaded dataset to make sure the contents were loaded correctly.
	 */
	
	/**
	 * Check that the client can receive a queryset in a POST message.
	 * (Read the data locally, then send it to the client from the test.)
	 * Assert that the query content looks correct.
	 */
	
	/**
	 * Check that the client can run clone detection comparing the queryset to the dataset.
	 */
	
	/**
	 * Check that the report is generated properly by the client.
	 */
	
	// TODO on the server, ensure that a report can be received from the client.
	// TODO on the server, ensure that multiple reports can be received from the clients at the same time.
	// TODO on the server, ensure that the /results page lists the correct information
	// TODO on the server, check if the correct sha's are being used when referencing file sets
	// TODO on the server, ensure that the reports are being displayed correctly when a user clicks one on the /results page.
	// TODO on the server/client, check what happens when HTTP is used
	// TODO on the server/client, check what happens when HTTPS is used
	// TODO write unit tests for the parser
	// TODO migrate the parser to this repository
}
