package com.mondego.httpcommunication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.DatatypeConverter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.mondego.indexbased.Daemon;
import com.mondego.indexbased.SearchManager;

@Path("query")
public class Query {
	private static final Logger logger = LogManager.getLogger(Query.class);
	
	/**
	 * accept a zip file of query data from whomever and run a
	 * clone detection query with the data.
	 * 
	 * @param uploadedInputStream
	 * @return
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public String query(@FormDataParam("query_file") InputStream uploadedInputStream) {
		File uploadDir = new File("query_sets");  // TODO refactor into daemon
		uploadDir.mkdir();
		
		// read stuff from the user and send it to client
		java.nio.file.Path tempFile = null;
		String shash = "";
		try {
			tempFile = Files.createTempFile(uploadDir.toPath(), "", ".zip");
			Files.copy(uploadedInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
			
			byte[] b = Files.readAllBytes(tempFile);
	        byte[] hash = MessageDigest.getInstance("MD5").digest(b);
	        shash = DatatypeConverter.printHexBinary(hash);
		} catch (IOException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println(shash);
		
		// TODO check if daemon status is loaded
//		if (!theInstance.daemon_loaded) {
//			return "daemon is still initializing.";
//		}
		Daemon daemon = Daemon.getInstance();
		
		// TODO do something with the tempfile
		//Path tempFile = daemon.getPostFile(uploadDir, req);
		
		// TODO wait until the daemon has started before running a query
		// TODO save a copy of post data?
		// TODO daemon will send a message to manager about results?
		// TODO daemon will have a command to return the last results?
		long timeStartSearch = System.nanoTime();
		// Daemon.setState(running query);
		daemon.query();
		long estimatedTime = System.nanoTime() - timeStartSearch;
        logger.info("Total run Time: " + (estimatedTime / 1000) + " micors");
        logger.info("number of clone pairs detected: " + daemon.sm.clonePairsCount);  // TODO need to reset clonePairsCount after a run has been completed.
        // TODO read the cleanup scripts and see what is deleted/recreated for each run.
		// TODO store the query results?
        // TODO report the query results?
		
		return "Query completed.";
		

	}
}