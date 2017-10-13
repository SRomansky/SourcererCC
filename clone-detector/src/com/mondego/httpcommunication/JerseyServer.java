package com.mondego.httpcommunication;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.grizzly2.servlet.GrizzlyWebContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;

import com.mondego.indexbased.Daemon;
import com.mondego.indexbased.SearchManager;

public class JerseyServer {
	private static URI BASE_URI = URI.create("http://localhost:4568/");  // TODO should use theInstance.daemonPort
    public static String ROOT_PATH = "";
    
    private static final Logger logger = LogManager.getLogger(JerseyServer.class);
    
    public JerseyServer() {
    	// default configurations
    }
    
    public JerseyServer(String URL, int port) {
    	BASE_URI = URI.create("http://" + URL + ":" + port + "/");
    }
    
    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    private static HttpServer startServer() {
        // create a resource config that scans for JAX-RS resources and providers
        // in com.example package
        final ResourceConfig rc = new ResourceConfig().packages("com.mondego.indexbased");  // TODO upodate this to httpcommunication?

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        return GrizzlyHttpServerFactory.createHttpServer(BASE_URI, rc);
    }
    
    public static void stop() {
    		Daemon daemon = Daemon.getInstance();
    		daemon.server.shutdownNow();
    }
    
    public static void run() {
    	try {
    		Daemon daemon = Daemon.getInstance();
    		daemon.register();
    		daemon.start(); // load dataset
    		/*
    		// TODO setup file upload directory
        	File uploadDir = new File("query_sets"); // TODO pick better dir for this?
    		uploadDir.mkdir();

            Spark.staticFiles.externalLocation("query_sets");
        	// TODO start a background job to register the shard with the data manager
        	// Daemon.setState(initializing);
            theInstance.daemon.register();
            theInstance.daemon.start();
            theInstance.daemon_loaded = true;
        	
        	*/
    		
    		
    		// Setup Jersey configuration
    		Map<String, String> initParams = new HashMap<>();
    		initParams.put(
    				ServerProperties.PROVIDER_PACKAGES,
    				Register.class.getPackage().getName());
    		initParams.put(
    				"jersey.config.server.provider.classnames", 
    				"org.glassfish.jersey.media.multipart.MultiPartFeature");
    		HttpServer server = GrizzlyWebContainerFactory.create(BASE_URI, ServletContainer.class, initParams);
    		daemon.server = server;
    		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
    			@Override
    			public void run() {
    				server.shutdownNow();
    			}
    		}));

    		logger.info(String.format("Application started.%nTry out %s%s%nStop the application using CTRL+C",
    				BASE_URI, ROOT_PATH));

    		Thread.currentThread().join();
    	} catch (IOException | InterruptedException ex) {
    		// Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
    	}
    }
}
