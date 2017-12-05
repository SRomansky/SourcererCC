package com.mondego.httpcommunication;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
	private static final ExecutorService TASK_EXECUTOR = Executors.newCachedThreadPool();

	private static final Logger logger = LogManager.getLogger(Query.class);
	
	private String getFileHash(java.nio.file.Path filePath) throws IOException, NoSuchAlgorithmException {
		byte[] b = Files.readAllBytes(filePath);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(b);
        String shash = DatatypeConverter.printHexBinary(hash);
		return shash;
	}
	
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
			@FormDataParam("query_src") InputStream querySrcInputStream,
			@FormDataParam("header_file") InputStream headerInputStream,
			@FormDataParam("license_file") InputStream licenseInputStream,
			@FormDataParam("code_file") InputStream codeInputStream,
			@FormDataParam("meta_data") FormDataBodyPart metaData,
			@FormDataParam("batch_name") InputStream batchNameStream
			) {
		/**
		 * run a query on the client using files from the POST message
		 * 
		 * side-effect: delete everything in daemon.sm.QUERY_DIR_PATH
		 * side-effect: puts new files into daemon.sm.QUERY_DIR_PATH
		 */

		try {
            Daemon.semaphore.acquire();
        } catch (InterruptedException ex) {
            logger.error("Caught interrupted exception " + ex);
        }
		
		// TODO check if the client is in the IDLE state
		Daemon.executor.submit(new Runnable() {
            @Override
            public void run() {
            	try {
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
			
	        shash = getFileHash(tempFile);

//			java.nio.file.Path querySrcPath = Paths.get(SearchManager.QUERY_SRC_DIR);
//			Files.deleteIfExists(querySrcPath);
//			Files.createFile(querySrcPath);
//			Files.copy(querySrcInputStream, querySrcPath);
		} catch (IOException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(shash);  // TODO log the hash? It could be useful for debugging?
		System.out.println("Got query id: " + qid);
		
		SearchManager.batch_name = null;
		try {
			System.out.println("Header path: " + SearchManager.queryHeaderFilePath);
			java.nio.file.Path queryHeaderPath = Paths.get(SearchManager.queryHeaderFilePath);
			Files.deleteIfExists(queryHeaderPath);
			Files.createFile(queryHeaderPath);  // TODO check if file attributes are needed...
			Files.copy(headerInputStream, queryHeaderPath, StandardCopyOption.REPLACE_EXISTING);
			
			java.nio.file.Path queryLicensePath = Paths.get(SearchManager.queryLicenseFilePath);
			Files.deleteIfExists(queryLicensePath);
			Files.createFile(queryLicensePath);  // TODO check if file attributes are needed...
			Files.copy(licenseInputStream, queryLicensePath, StandardCopyOption.REPLACE_EXISTING);
			
			java.nio.file.Path queryCodePath = Paths.get(SearchManager.QUERY_SRC_DIR);
			Files.deleteIfExists(queryCodePath);
			Files.createFile(queryCodePath);  // TODO check if file attributes are needed...
			Files.copy(codeInputStream, queryCodePath, StandardCopyOption.REPLACE_EXISTING);
			
			java.nio.file.Path batchNamePath = Paths.get("batch_name.txt");
			Files.deleteIfExists(batchNamePath);
			Files.createFile(batchNamePath);  // TODO check if file attributes are needed...
			Files.copy(batchNameStream, batchNamePath, StandardCopyOption.REPLACE_EXISTING);
			
			SearchManager.batch_name = String.join(" ", Files.readAllLines(batchNamePath)); //IOUtils.toString(batchNameStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		/* unpack contents from manager into query directory */
		
		// TODO check if the daemon is busy in a query
		// TODO if the daemon is busy there is a bug :)
		// TODO (later) if the daemon is busy, send a POST with an error to results/{id} on the manager
		
		Daemon daemon = Daemon.getInstance();

		try {
			FileUtils.cleanDirectory(new File(daemon.sm.QUERY_DIR_PATH));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			unzip(tempFile.toString(), daemon.sm.QUERY_DIR_PATH);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		/* run the query */
		// TODO check if daemon status is loaded
//		if (!theInstance.daemon_loaded) {
//			return "daemon is still initializing.";
//		}
		
		
		// TODO wait until the daemon has started before running a query
		long timeStartSearch = System.nanoTime();
		// Daemon.setState(running query);
		daemon.query();
		long estimatedTime = System.nanoTime() - timeStartSearch;
        logger.info("Total run Time: " + (estimatedTime / 1000) + " micors");
        logger.info("number of clone pairs detected: " + daemon.sm.clonePairsCount);  // TODO need to reset clonePairsCount after a run has been completed.
        
        /* get the query results and send them to the manager */
        // XXX uses output dir from SCC like SourcererCC/clone-detector/NODE_1/output8.0
        File resultsDir = daemon.getResults();
        String report = daemon.generateReport(
        		SearchManager.queryHeaderFilePath, SearchManager.queryLicenseFilePath, SearchManager.QUERY_SRC_DIR,
        		SearchManager.datasetHeaderFilePath, SearchManager.datasetLicenseFilePath, SearchManager.DATASET_SRC_DIR // TODO rename
        		);
        
        
        sendResults(report, qid, daemon.dataset_id);
            } finally {
            		Daemon.semaphore.release();
            }
            }});
		
		
		return "Query running.";
	}
	
	
	@Path("/local") // TODO figure out the path for this
	@POST
	public String queryLocalFiles() {
	/**
	 * run a query using data already available to the client
	 */
		Daemon daemon = Daemon.getInstance();
		long timeStartSearch = System.nanoTime();
		// Daemon.setState(running query);
		daemon.query();
		long estimatedTime = System.nanoTime() - timeStartSearch;
        logger.info("Total run Time: " + (estimatedTime / 1000) + " micors");
        logger.info("number of clone pairs detected: " + daemon.sm.clonePairsCount);  // TODO need to reset clonePairsCount after a run has been completed.
        
        /* get the query results and send them to the manager */
        // XXX uses output dir from SCC like SourcererCC/clone-detector/NODE_1/output8.0
        File resultsDir = daemon.getResults();
        String report = daemon.generateReport(
        		SearchManager.queryHeaderFilePath, SearchManager.queryLicenseFilePath, SearchManager.QUERY_SRC_DIR, 
        		SearchManager.datasetHeaderFilePath, SearchManager.datasetLicenseFilePath, SearchManager.DATASET_SRC_DIR // TODO rename
        		);
        sendResults(report, "local", daemon.dataset_id);  // TODO qid should be sha-256 of the query content zip file. It is not guaranteed that the zip file will exist/be identifiable from this location.
		
		
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
	
	// code from: http://www.codejava.net/java-se/file-io/programmatically-extract-a-zip-file-using-java
    private static final int BUFFER_SIZE = 4096;
    /**
     * Extracts a zip file specified by the zipFilePath to a directory specified by
     * destDirectory (will be created if does not exists)
     * @param zipFilePath
     * @param destDirectory
     * @throws IOException
     */
    public void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdir();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }
    /**
     * Extracts a zip entry (file entry)
     * @param zipIn
     * @param filePath
     * @throws IOException
     */
    private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }
	
	public void sendResults(String report, String queryId, String datasetShardId) {
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.register(MultiPartFeature.class); 
		
		Client client = ClientBuilder.newClient(clientConfig);
		WebTarget webTarget = client.target("http://" + Daemon.managerIP + ":" + Daemon.managerPort + "/results");  // TODO dynamic

		MultivaluedMap formData = new MultivaluedStringMap();
		formData.add("report", report);
		formData.add("queryId", queryId);
		formData.add("datasetShardId", datasetShardId);
		formData.add("batch_name", SearchManager.batch_name);

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
				.bodyPart(formBody);

		Response response = webTarget
				.request()
				.post(Entity.entity(entity, MediaType.MULTIPART_FORM_DATA));
	}
}