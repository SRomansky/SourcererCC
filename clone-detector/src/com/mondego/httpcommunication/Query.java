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
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Set;
import java.util.ArrayList;
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
	
	private String query(
			InputStream uploadedInputStream, 
			InputStream querySrcInputStream, 
			InputStream headerInputStream, 
			InputStream licenseInputStream,
			InputStream codeInputStream, 
			InputStream batchNameStream, 
			InputStream taskNameStream, 
			String qid) {
		File uploadDir = new File("query_sets");  // TODO refactor into daemon
		uploadDir.mkdir();
		
		/* read contents from manager */
		java.nio.file.Path tempFile = null;
		String shash = "";
		try {
			tempFile = Files.createTempFile(uploadDir.toPath(), "", ".tokens"); // this is no longer a zip file
			Files.copy(uploadedInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
	        shash = getFileHash(tempFile);
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
			File storage = new File(daemon.sm.QUERY_DIR_PATH);
			storage.mkdir();
			Files.move(tempFile, Paths.get(daemon.sm.QUERY_DIR_PATH + "/tmp.tokens"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
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
        ArrayList<String> reportPages = daemon.generateReportPages(
        		SearchManager.queryHeaderFilePath, SearchManager.queryLicenseFilePath, SearchManager.QUERY_SRC_DIR,
        		SearchManager.datasetHeaderFilePath, SearchManager.datasetLicenseFilePath, SearchManager.DATASET_SRC_DIR, // TODO rename
        		qid,
        		daemon.dataset_id
        		);
        
        
        sendResultPages(reportPages, qid, daemon.dataset_id);
		return "query complete";
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
	public String taskrunner(
			@FormDataParam("query_file") InputStream uploadedInputStream,
			@FormDataParam("query_src") InputStream querySrcInputStream,
			@FormDataParam("header_file") InputStream headerInputStream,
			@FormDataParam("license_file") InputStream licenseInputStream,
			@FormDataParam("code_file") InputStream codeInputStream,
			@FormDataParam("meta_data") FormDataBodyPart metaData,
			@FormDataParam("input_name") InputStream batchNameStream,
			@FormDataParam("task") InputStream taskNameStream
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
		
		java.nio.file.Path taskNamePath = Paths.get("task.txt");
		try {
			Files.deleteIfExists(taskNamePath);
			Files.createFile(taskNamePath);  // TODO check if file attributes are needed...
			Files.copy(taskNameStream, taskNamePath, StandardCopyOption.REPLACE_EXISTING);
			SearchManager.task = String.join(" ", Files.readAllLines(taskNamePath));
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		
		// TODO check if the client is in the IDLE state
		MultivaluedMap metaDataMap = metaData.getValueAs(MultivaluedMap.class);
		String qid = (String) ((java.util.LinkedList) metaDataMap.get("qid")).get(0);  // why

		// do the following if
		if (SearchManager.task.equals(new String("query"))) //
			query(uploadedInputStream, querySrcInputStream, headerInputStream, licenseInputStream,
					codeInputStream, batchNameStream, taskNameStream, qid);
		else if (SearchManager.task.equals(new String("shard")))
			shard(uploadedInputStream, querySrcInputStream, headerInputStream, licenseInputStream,
					codeInputStream, batchNameStream, taskNameStream, qid);
		else
			logger.error("unknown task given to client: " + SearchManager.task);
		
        Daemon.semaphore.release();
		return "Query running.";
	}
	
	
	private void shard(InputStream uploadedInputStream, InputStream querySrcInputStream, InputStream headerInputStream,
			InputStream licenseInputStream, InputStream codeInputStream, InputStream batchNameStream,
			InputStream taskNameStream, String qid) {
		
		//SearchManager.DATASET_DIR // clean it out and replace the tokens file with the new shard data
		File shardDir = new File("shard_sets");
		shardDir.mkdir();
		
		/* read contents from manager */
		java.nio.file.Path tempFile = null;
		String shash = "";
		try {
			tempFile = Files.createTempFile(shardDir.toPath(), "", ".tokens"); // this is no longer a zip file
			Files.copy(uploadedInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
	        shash = getFileHash(tempFile);
	        
	        FileUtils.cleanDirectory(new File(SearchManager.DATASET_DIR));
			File storage = new File(SearchManager.DATASET_DIR);
			storage.mkdir();
			Files.move(tempFile, Paths.get(SearchManager.DATASET_DIR + "/tmp.tokens"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(shash);  // TODO log the hash? It could be useful for debugging?
		System.out.println("Got shard id: " + qid);
		
		SearchManager.batch_name = null;
		try {
			System.out.println("Header path: " + SearchManager.datasetHeaderFilePath);
			java.nio.file.Path dataHeaderPath = Paths.get(SearchManager.datasetHeaderFilePath);
			Files.deleteIfExists(dataHeaderPath);
			Files.createFile(dataHeaderPath);  // TODO check if file attributes are needed...
			Files.copy(headerInputStream, dataHeaderPath, StandardCopyOption.REPLACE_EXISTING);
			
			java.nio.file.Path dataLicensePath = Paths.get(SearchManager.datasetLicenseFilePath);
			Files.deleteIfExists(dataLicensePath);
			Files.createFile(dataLicensePath);  // TODO check if file attributes are needed...
			Files.copy(licenseInputStream, dataLicensePath, StandardCopyOption.REPLACE_EXISTING);
			
			java.nio.file.Path dataCodePath = Paths.get(SearchManager.DATASET_SRC_DIR);
			Files.deleteIfExists(dataCodePath);
			Files.createFile(dataCodePath);  // TODO check if file attributes are needed...
			Files.copy(codeInputStream, dataCodePath, StandardCopyOption.REPLACE_EXISTING);
			
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

		
		// TODO wait until the daemon has started before running a query
		long timeStartSearch = System.nanoTime();
		// Daemon.setState(running query);
		daemon.start();
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
	
	void sendResultPages(ArrayList<String> reportPages, String queryId, String datasetShardId) {	
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.register(MultiPartFeature.class); 
		
		Client client = ClientBuilder.newClient(clientConfig);
		WebTarget webTarget = client.target("http://" + Daemon.managerIP + ":" + String.valueOf(Daemon.managerPort) + "/results");  // TODO dynamic

		for (int pageno = 0; pageno < reportPages.size(); pageno++) {
			String page = reportPages.get(pageno);
			MultivaluedMap formData = new MultivaluedStringMap();
			formData.add("report", page);
			formData.add("queryId", queryId);
			formData.add("page_no", String.valueOf(pageno));
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
}
