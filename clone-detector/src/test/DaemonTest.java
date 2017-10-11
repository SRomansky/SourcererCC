package test;

import static org.junit.Assert.*;

import org.junit.Test;

public class DaemonTest {

	@Test
	public void test() {
		fail("Not yet implemented");
	}

	// XXX Each test should be independent and should be runnable regardless of execution order.
	/**
	 * Check the daemon can start without crashing.
	 */
	
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
