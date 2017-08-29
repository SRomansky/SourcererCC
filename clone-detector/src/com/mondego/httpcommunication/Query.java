package com.mondego.httpcommunication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.xml.bind.DatatypeConverter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;

import com.mondego.indexbased.Daemon;
import com.mondego.indexbased.SearchManager;

@Path("/query")
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
	public String query(
			@FormDataParam("query_file") InputStream uploadedInputStream,
			@FormDataParam("meta_data") FormDataBodyPart metaData
			) {
		/**
		 * run a query on the client using files from the POST message
		 */

		MultivaluedMap metaDataMap = metaData.getValueAs(MultivaluedMap.class);
		String qid = (String) ((java.util.LinkedList) metaDataMap.get("qid")).get(0);  // why

		
		File uploadDir = new File("query_sets");  // TODO refactor into daemon
		uploadDir.mkdir();
		
		/* read contents from manager */
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
		System.out.println(shash);  // TODO log the hash? It could be useful for debugging?
		System.out.println("Got query id: " + qid);
		
		/* unpack contents from manager into query directory */
		// XXX check if querydir==datasetdir: log a warning if it is.
		// unpack contents into querydir
		// TODO generate a new querydir per query? or reuse the same querydir
		
		// TODO check if the daemon is busy in a query
		// TODO if the daemon is busy there is a bug :)
		// TODO (later) if the daemon is busy, send a POST with an error to results/{id} on the manager
		
		
		/* run the query */
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
        
        /* get the query results and send them to the manager */
        // XXX uses output dir from SCC like SourcererCC/clone-detector/NODE_1/output8.0
        // FilePath results = daemon.getResults(qid)
        // File? compressedResults = zip(results)
        // sendResults(qid, compressedResults)
        File resultsDir = daemon.getResults();
        try {
			java.nio.file.Path zippedResultsDir = packageResultDir(resultsDir);
			sendResults(zippedResultsDir, qid);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return "Query completed.";
	}
	
	
	@Path("/local") // TODO figure out the path for this
	@POST
	public String queryLocalFiles() {
	/**
	 * run a query using data already available to the client
	 */
		
		
		// TODO send results to server
		// TODO on the server generate a query id if "local" is the qid included in the POST message
		return "todo build this method.";
	}
	
	public java.nio.file.Path packageResultDir(File resultsDir) throws IOException {
		/**
		 * Takes a directory and zips it into a new file.
		 * Assume that the directory contains the SourcererCC result files.
		 */

		String zipPath = resultsDir.getParent();
		File zipFile = new File(zipPath);
		java.nio.file.Path zipTempFile = Files.createTempFile(zipFile.toPath(), "", ".zip");
		pack(resultsDir.getPath(), zipTempFile);
		return zipTempFile;
	}
	
	// https://stackoverflow.com/questions/15968883/how-to-zip-a-folder-itself-using-java/32052016#32052016
	public static void pack(String sourceDirPath, java.nio.file.Path zipFilePath) throws IOException {
	    //java.nio.file.Path p = Files.createFile(Paths.get(zipFilePath));
	    try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
	        java.nio.file.Path pp = Paths.get(sourceDirPath);
	        Files.walk(pp)
	          .filter(path -> !Files.isDirectory(path))
	          .forEach(path -> {
	              ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString());
	              try {
	                  zs.putNextEntry(zipEntry);
	                  zs.write(Files.readAllBytes(path));
	                  zs.closeEntry();
	            } catch (Exception e) {
	                System.err.println(e);
	            }
	          });
	    }
	}
	
	public void sendResults(java.nio.file.Path zippedResults, String queryId) {
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.register(MultiPartFeature.class); 
		
		FileDataBodyPart filePart = new FileDataBodyPart("results_file", zippedResults.toFile());

		Client client = ClientBuilder.newClient(clientConfig);
    	WebTarget webTarget = client.target("http://localhost:4567/results");  // TODO dynamic
    	
    	MultivaluedMap formData = new MultivaluedStringMap();
    	formData.add("qid", queryId);
    	
    	FormDataContentDisposition cd = null;
		try {
			cd = new FormDataContentDisposition("form-data; name=\"meta_data\"");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			System.out.println("A problem occurred.");
			e.printStackTrace();
		}
    	
    	BodyPart formBody = new BodyPart().entity(formData);
    	formBody.setMediaType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    	formBody.setContentDisposition(cd);
    	
    	MultiPart entity = new FormDataMultiPart()
    			.bodyPart(filePart)
    			.bodyPart(formBody);
    	
    	Response response = webTarget
    			.request()
    			.post(Entity.entity(entity, MediaType.MULTIPART_FORM_DATA));
	}
}