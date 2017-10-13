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
		// jvm args are supposed to be set by gradle -Xms10g -Xmx10g see gradle.properties in clone-detector
		// setup command line parameters
		String sourcererCCPath = System.getProperty("user.dir"); // <path>/SourcererCC/clone-detector
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
