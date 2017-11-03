package test;

import static org.junit.Assert.*;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mondego.httpcommunication.JerseyServer;
import com.mondego.indexbased.SearchManager;

public class RESTTest {
	// run the tests with gradle.
	// requires an instance of the scc-manager to be running.

	// clone-detector/src/test/input contains a mock format of clone-detector/input
	
    public static final String BASE_URI = "http://127.0.0.1:4568/";
	SearchManager instance;
	@Before
	public void setUp() { 
		instance = DaemonTest.createDaemon();
		instance.server = new JerseyServer("localhost", instance.daemonPort);
//		instance.server.run();
		Thread thread = new Thread("Jersey Thread") {
			public void run(){
				instance.server.run();
			}
		};
		thread.start();
	}
	
	@After
	public void tearDown() {
		instance.server.stop();
	}
	
//	@Test
	public void testStatus() {
		// XXX This test is bad. There will be variance in runtime based on what is running it.
		Client client = ClientBuilder.newClient();
        WebTarget target = client.target(BASE_URI).path("status");
        
        try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        Response r1 = target.request().get();
        assert(r1.getStatus() == Response.Status.OK.getStatusCode());
        assertEquals("Unexpected state.", r1.readEntity(String.class), "IDLE");
        
        // TODO add a INIT status request. Can the client respond to status queries asynchronously?
        
	}
	// TODO make tests
}
