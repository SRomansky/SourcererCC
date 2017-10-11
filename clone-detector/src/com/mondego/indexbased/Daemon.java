package com.mondego.indexbased;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mondego.httpcommunication.Register;
import com.mondego.models.QueryFileProcessor;
import com.mondego.utility.TokensFileReader;
import com.mondego.utility.Util;

import spark.Request;

public class Daemon {
	public static SearchManager sm = null;
	private static final Logger logger = LogManager.getLogger(Daemon.class);
	private static Daemon daemon = null;
	public static String ip = "";
	public static int port = 0;
	public static String outputDir = null;
	public static String dataset_id = null; // In INIT this variable is set to the SHA-256 of the dataset.
	
	HashMap<String, String> queryHeaderMap = new HashMap<String, String>();
	HashMap<String, String> queryLicenseMap = new HashMap<String, String>();
	
	HashMap<String, String> datasetHeaderMap = new HashMap<String, String>();
	HashMap<String, String> datasetLicenseMap = new HashMap<String, String>();
	HashMap<String, String> datasetCodeMap = new HashMap<String, String>();

	
	public enum State {
		/**
		 * BUSY - running query
		 * IDLE - waiting for query
		 * INIT - initializing, loading the dataset into memory
		 */
		BUSY, IDLE, INIT
	}
	
	private static State state = State.INIT; // TODO do semaphores need to be used to access and modify the state?
	
	private void setState(State newState) {
		state = newState;
	}
	
	public State getState() {
		return state;
	}
	
	public static Daemon getInstance() {
		if (daemon == null) {
			logger.warn("Daemon not initialized.");
		}
		
		return daemon;
	}
	
	public Daemon(SearchManager theInstance, String daemonIp, int daemonPort) {
		sm = theInstance;
		ip = daemonIp;
		port = daemonPort;
		daemon = this;
	}
	
	private String calculateDatasetId() {
		// based on https://stackoverflow.com/questions/3010071/how-to-calculate-md5-checksum-on-directory-with-java-or-groovy/15503271#15503271
		File datasetDir = new File(SearchManager.DATASET_DIR);
		
		assert (datasetDir.isDirectory());
	    Vector<FileInputStream> fileStreams = new Vector<FileInputStream>();

	    System.out.println("Found files for hashing:");
	    collectInputStreams(datasetDir, fileStreams, false);

	    SequenceInputStream seqStream = 
	            new SequenceInputStream(fileStreams.elements());

	    try {
	        String sha256Hash = DigestUtils.sha256Hex(seqStream);
	        seqStream.close();
	        return sha256Hash;
	    }
	    catch (IOException e) {
	        throw new RuntimeException("Error reading files to hash in "
	                                   + datasetDir.getAbsolutePath(), e);
	    }
	}
	
	private void collectInputStreams(File dir,
			List<FileInputStream> foundStreams,
			boolean includeHiddenFiles) {

		File[] fileList = dir.listFiles();
		if (fileList == null) {
			System.out.println("dataset not found in directory: " + dir.toString());
		}
		Arrays.sort(fileList,               // Need in reproducible order
				new Comparator<File>() {
			public int compare(File f1, File f2) {                       
				return f1.getName().compareTo(f2.getName());
			}
		});

		for (File f : fileList) {
			if (!includeHiddenFiles && f.getName().startsWith(".")) {
				// Skip it
			}
			else if (f.isDirectory()) {
				collectInputStreams(f, foundStreams, includeHiddenFiles);
			}
			else {
				try {
					System.out.println("\t" + f.getAbsolutePath());
					foundStreams.add(new FileInputStream(f));
				}
				catch (FileNotFoundException e) {
					throw new AssertionError(e.getMessage()
							+ ": file should never not be found!");
				}
			}
		}
	}
	
	public void start() {
    	/*
    	 * Start the daemon and load the dataset into memory if it exists.
    	 */
    	setState(State.INIT);
    	dataset_id = calculateDatasetId();
    	
    	SearchManager.gtpmSearcher = new CodeSearcher(Util.GTPM_INDEX_DIR, "key");  // when is this built/used?
        File datasetDir = new File(SearchManager.DATASET_DIR);
        if (datasetDir.isDirectory()) {
            logger.info("Dataset directory: " + datasetDir.getAbsolutePath());
            for (File inputFile : Util.getAllFilesRecur(datasetDir)) {
                logger.info("indexing dataset file: " + inputFile.getAbsolutePath());
                try {
                	File candidateFile = inputFile;
        			
        			int completedLines = 0;
        			while (true) {
        				// SearchManager() spawns threads to process the index information from the query files.
        				logger.info("creating indexes for " + candidateFile.getAbsolutePath());
        				completedLines = sm.createIndexes(candidateFile, completedLines);  // sends read Dataset to invertedIndex, documentsForII. Cuts up some of the bags if they are on memory boundaries. I haven't read how the memory boundary works.
        				logger.info("indexes created");
        				logger.debug("COMPLETED LINES: " + completedLines);
        				if (completedLines == -1) {
        					break;
        				}
        			}
                } catch (FileNotFoundException e) {
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
        setState(State.IDLE);
	}
    
    public void query() {
    	// TODO prevent multiple queries at the same time?
    	// start queue processors
    	setState(State.BUSY);
    	sm.completedQueries = new HashSet<Long>();

        sm.startQueryThreads();
    	
        sm.resetQueryCounters();	// reset the global counters
        long currentTime = System.nanoTime();
        outputDir = SearchManager.OUTPUT_DIR + SearchManager.th / SearchManager.MUL_FACTOR
        		+ "_" + String.valueOf(currentTime);
        Util.createDirs(outputDir);
    	
		File datasetDir = new File(SearchManager.QUERY_DIR_PATH);
        if (datasetDir.isDirectory()) {
            logger.info("QuerySet directory: " + datasetDir.getAbsolutePath());
            for (File inputFile : Util.getAllFilesRecur(datasetDir)) {
                logger.info("indexing QuerySet file: " + inputFile.getAbsolutePath());
                try {
                	File queryFile = inputFile;
                    QueryFileProcessor queryFileProcessor = new QueryFileProcessor();
                    logger.info("Query File: " + queryFile.getAbsolutePath());
                    String filename = queryFile.getName().replaceFirst("[.][^.]+$", "");
                    try {
                        String cloneReportFileName = outputDir + "/" + filename + "clones_index_WITH_FILTER.txt";
                        
                        File cloneReportFile = new File(cloneReportFileName);
                        if (cloneReportFile.exists()) {
                            sm.appendToExistingFile = true;
                        } else {
                            sm.appendToExistingFile = false;
                        }
                        SearchManager.clonesWriter = Util.openFile(outputDir +"/" + filename + "clones_index_WITH_FILTER.txt",
                                sm.appendToExistingFile);
                        // recoveryWriter
                        SearchManager.recoveryWriter = Util.openFile(
                                outputDir + "/recovery.txt",
                                false);
                    } catch (IOException e) {
                        logger.error(e.getMessage() + " exiting");
                        System.exit(1);
                    }
                    
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
        sm.stopQueryThreads();
        setState(State.IDLE);
	}
    
    public Path getPostFile(File uploadDirectory, Request req) {
    	/**
    	 * Extract a multi-part form binary to a temporary file if one is
    	 * found in a POST message. If a binary is not found, return null.
    	 * 
    	 * Assumption: the form part is named "query_file"
    	 */
    	Path tempFile = null;

        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

        try { // getPart needs to use same "name" as input field in form
        	tempFile = Files.createTempFile(uploadDirectory.toPath(), ".zip", "");
        	InputStream input = req.raw().getPart("query_file").getInputStream();
        	Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ServletException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        //logInfo(req, tempFile);
		try {
			byte[] b = Files.readAllBytes(tempFile);
	        byte[] hash = MessageDigest.getInstance("MD5").digest(b);
	        String shash = DatatypeConverter.printHexBinary(hash);
	        System.out.println("Got file with hash: " + shash);
		} catch (IOException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	return tempFile;
    }

	public void register() {
		/**
		 * Register this shard with the management application.
		 * 
		 * This method sends a POST to sm.managerURL:sm.managerPort
		 */
		setState(State.BUSY);
		Register.sendRegistration();
		setState(State.IDLE);
	}
	
	public File getResults() {
		//File resultsPath = new File(SearchManager.OUTPUT_DIR_TH);
		File resultsPath = new File(outputDir);
		return resultsPath;
	}

	public String generateReport(String queryHeaderFilePath, String queryLicenseFilePath, String datasetHeaderFilePath,
			String datasetLicenseFilePath, String datasetCodeFilePath) {
		loadCsvFileToMap(Paths.get(queryHeaderFilePath), queryHeaderMap); // XXX Assert that the hashes have the same length. (They can have different lengths if not all of the parser files were copied to the client.)
		loadCsvFileToMap(Paths.get(queryLicenseFilePath), queryLicenseMap);
		loadCsvFileToMap(Paths.get(datasetHeaderFilePath), datasetHeaderMap);
		loadCsvFileToMap(Paths.get(datasetLicenseFilePath), datasetLicenseMap);
		loadCsvFileToMap(Paths.get(datasetCodeFilePath), datasetCodeMap);
		
		System.out.println("size of codemap: " + datasetCodeMap.size());
		
		// TODO use the report file at this point with another application to look up the code snippets?
//        String qSrcDir = SearchManager.QUERY_SRC_DIR + "/src.zip";
//        String dSrcDir = SearchManager.DATASET_SRC_DIR + "/src.zip"; // Could be any folder in here? No. Force it to be src.zip too.
//        embedCode(report);
//        
		
		String report = "";
		try {
			File dir = new File(outputDir);
			File[] directoryListing = dir.listFiles();
			if (directoryListing != null) {
				for (File file : directoryListing) {
					if (file.isFile()) {
						// read the file
						BufferedReader bufferedReader;

						bufferedReader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);

						String line = bufferedReader.readLine();
						while(line != null){
							String[] components = line.split(",");
							int qpid = 0;
							int qbid = 1;
							int dpid = 2;
							int dbid = 3;

							
							
							
							String rowContent = wrap(components[dpid]) + wrap(components[dbid]) + wrap(components[qpid]) + wrap(components[qbid]) +
									wrap(datasetHeaderMap.get(components[dpid])) +
									wrap(datasetLicenseMap.get(components[dpid])) +
									wrap(queryHeaderMap.get(components[qpid])) +
									wrap(queryLicenseMap.get(components[qpid])) +
									wrap("<details>"+
									  "<summary>Show Code</summary>" +
											"<pre><code class=\"language-python\">" +
									  StringEscapeUtils.unescapeJava( datasetCodeMap.get("u'" + components[dpid])) +  // XXX This probably doesn't unescape the code properly. But, it is a start.
									  "</code></pre>" +
									  "</details>");
									
							String row = "<tr class=\\\"none\\\">" + rowContent + "</tr>";
							report = report.concat(row);

							line = bufferedReader.readLine();
						}
						bufferedReader.close();
					}
					else {
						logger.error("Found unexpected folder in results directory.");
					}
				}
			} else {
				logger.error("The results directory doesn't seem to exist.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return report;
	}
	
	public void loadCsvFileToMap(Path csvFile, HashMap<String, String> map) {
		/**
		 * This function is used to load the header and license file into maps.
		 * 
		 * The header file has a structure like this:
		 * cloneId,path,startLineNo,endLineNo
		 * 1,../../data_set/100_modules/0-core-client-1.1.0a5.tar.gz/0-core-client-1.1.0a5/zeroos/core0/client/__init__.py,1,1
		 * 
		 * The license file has a structure like this:
		 * cloneId,license(s)
		 * 1,NONE
		 */
		
		map.clear();
		// based on: http://www.java67.com/2015/08/how-to-load-data-from-csv-file-in-java.html
		try (BufferedReader br = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
			String line = br.readLine();
			while (line != null) {
				String[] parts = line.split(",", 2);

				map.put(parts[0], parts[1]);
				line = br.readLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String wrap(String value) { 
		return "<td>" + value + "</td>";
	}
	
	/*
	 *
	 * unescape_perl_string()
	 *
	 *      Tom Christiansen <tchrist@perl.com>
	 *      Sun Nov 28 12:55:24 MST 2010
	 *
	 * It's completely ridiculous that there's no standard
	 * unescape_java_string function.  Since I have to do the
	 * damn thing myself, I might as well make it halfway useful
	 * by supporting things Java was too stupid to consider in
	 * strings:
	 * 
	 *   => "?" items  are additions to Java string escapes
	 *                 but normal in Java regexes
	 *
	 *   => "!" items  are also additions to Java regex escapes
	 *   
	 * Standard singletons: ?\a ?\e \f \n \r \t
	 * 
	 *      NB: \b is unsupported as backspace so it can pass-through
	 *          to the regex translator untouched; I refuse to make anyone
	 *          doublebackslash it as doublebackslashing is a Java idiocy
	 *          I desperately wish would die out.  There are plenty of
	 *          other ways to write it:
	 *
	 *              \cH, \12, \012, \x08 \x{8}, \u0008, \U00000008
	 *
	 * Octal escapes: \0 \0N \0NN \N \NN \NNN
	 *    Can range up to !\777 not \377
	 *    
	 *      TODO: add !\o{NNNNN}
	 *          last Unicode is 4177777
	 *          maxint is 37777777777
	 *
	 * Control chars: ?\cX
	 *      Means: ord(X) ^ ord('@')
	 *
	 * Old hex escapes: \xXX
	 *      unbraced must be 2 xdigits
	 *
	 * Perl hex escapes: !\x{XXX} braced may be 1-8 xdigits
	 *       NB: proper Unicode never needs more than 6, as highest
	 *           valid codepoint is 0x10FFFF, not maxint 0xFFFFFFFF
	 *
	 * Lame Java escape: \[IDIOT JAVA PREPROCESSOR]uXXXX must be
	 *                   exactly 4 xdigits;
	 *
	 *       I can't write XXXX in this comment where it belongs
	 *       because the damned Java Preprocessor can't mind its
	 *       own business.  Idiots!
	 *
	 * Lame Python escape: !\UXXXXXXXX must be exactly 8 xdigits
	 * 
	 * TODO: Perl translation escapes: \Q \U \L \E \[IDIOT JAVA PREPROCESSOR]u \l
	 *       These are not so important to cover if you're passing the
	 *       result to Pattern.compile(), since it handles them for you
	 *       further downstream.  Hm, what about \[IDIOT JAVA PREPROCESSOR]u?
	 *
	 */

	public final static
	String unescape_perl_string(String oldstr) {

	    /*
	     * In contrast to fixing Java's broken regex charclasses,
	     * this one need be no bigger, as unescaping shrinks the string
	     * here, where in the other one, it grows it.
	     */

	    StringBuffer newstr = new StringBuffer(oldstr.length());

	    boolean saw_backslash = false;

	    for (int i = 0; i < oldstr.length(); i++) {
	        int cp = oldstr.codePointAt(i);
	        if (oldstr.codePointAt(i) > Character.MAX_VALUE) {
	            i++; /****WE HATES UTF-16! WE HATES IT FOREVERSES!!!****/
	        }

	        if (!saw_backslash) {
	            if (cp == '\\') {
	                saw_backslash = true;
	            } else {
	                newstr.append(Character.toChars(cp));
	            }
	            continue; /* switch */
	        }

	        if (cp == '\\') {
	            saw_backslash = false;
	            newstr.append('\\');
	            newstr.append('\\');
	            continue; /* switch */
	        }

	        switch (cp) {

	            case 'r':  newstr.append('\r');
	                       break; /* switch */

	            case 'n':  newstr.append('\n');
	                       break; /* switch */

	            case 'f':  newstr.append('\f');
	                       break; /* switch */

	            /* PASS a \b THROUGH!! */
	            case 'b':  newstr.append("\\b");
	                       break; /* switch */

	            case 't':  newstr.append('\t');
	                       break; /* switch */

	            case 'a':  newstr.append('\007');
	                       break; /* switch */

	            case 'e':  newstr.append('\033');
	                       break; /* switch */

	            /*
	             * A "control" character is what you get when you xor its
	             * codepoint with '@'==64.  This only makes sense for ASCII,
	             * and may not yield a "control" character after all.
	             *
	             * Strange but true: "\c{" is ";", "\c}" is "=", etc.
	             */
	            case 'c':   {
	                if (++i == oldstr.length()) { die("trailing \\c"); }
	                cp = oldstr.codePointAt(i);
	                /*
	                 * don't need to grok surrogates, as next line blows them up
	                 */
	                if (cp > 0x7f) { die("expected ASCII after \\c"); }
	                newstr.append(Character.toChars(cp ^ 64));
	                break; /* switch */
	            }

	            case '8':
	            case '9': die("illegal octal digit");
	                      /* NOTREACHED */

	    /*
	     * may be 0 to 2 octal digits following this one
	     * so back up one for fallthrough to next case;
	     * unread this digit and fall through to next case.
	     */
	            case '1':
	            case '2':
	            case '3':
	            case '4':
	            case '5':
	            case '6':
	            case '7': --i;
	                      /* FALLTHROUGH */

	            /*
	             * Can have 0, 1, or 2 octal digits following a 0
	             * this permits larger values than octal 377, up to
	             * octal 777.
	             */
	            case '0': {
	                if (i+1 == oldstr.length()) {
	                    /* found \0 at end of string */
	                    newstr.append(Character.toChars(0));
	                    break; /* switch */
	                }
	                i++;
	                int digits = 0;
	                int j;
	                for (j = 0; j <= 2; j++) {
	                    if (i+j == oldstr.length()) {
	                        break; /* for */
	                    }
	                    /* safe because will unread surrogate */
	                    int ch = oldstr.charAt(i+j);
	                    if (ch < '0' || ch > '7') {
	                        break; /* for */
	                    }
	                    digits++;
	                }
	                if (digits == 0) {
	                    --i;
	                    newstr.append('\0');
	                    break; /* switch */
	                }
	                int value = 0;
	                try {
	                    value = Integer.parseInt(
	                                oldstr.substring(i, i+digits), 8);
	                } catch (NumberFormatException nfe) {
	                    die("invalid octal value for \\0 escape");
	                }
	                newstr.append(Character.toChars(value));
	                i += digits-1;
	                break; /* switch */
	            } /* end case '0' */

	            case 'x':  {
	                if (i+2 > oldstr.length()) {
	                    die("string too short for \\x escape");
	                }
	                i++;
	                boolean saw_brace = false;
	                if (oldstr.charAt(i) == '{') {
	                        /* ^^^^^^ ok to ignore surrogates here */
	                    i++;
	                    saw_brace = true;
	                }
	                int j;
	                for (j = 0; j < 8; j++) {

	                    if (!saw_brace && j == 2) {
	                        break;  /* for */
	                    }

	                    /*
	                     * ASCII test also catches surrogates
	                     */
	                    int ch = oldstr.charAt(i+j);
	                    if (ch > 127) {
	                        die("illegal non-ASCII hex digit in \\x escape");
	                    }

	                    if (saw_brace && ch == '}') { break; /* for */ }

	                    if (! ( (ch >= '0' && ch <= '9')
	                                ||
	                            (ch >= 'a' && ch <= 'f')
	                                ||
	                            (ch >= 'A' && ch <= 'F')
	                          )
	                       )
	                    {
	                        die(String.format(
	                            "illegal hex digit #%d '%c' in \\x", ch, ch));
	                    }

	                }
	                if (j == 0) { die("empty braces in \\x{} escape"); }
	                int value = 0;
	                try {
	                    value = Integer.parseInt(oldstr.substring(i, i+j), 16);
	                } catch (NumberFormatException nfe) {
	                    die("invalid hex value for \\x escape");
	                }
	                newstr.append(Character.toChars(value));
	                if (saw_brace) { j++; }
	                i += j-1;
	                break; /* switch */
	            }

	            case 'u': {
	                if (i+4 > oldstr.length()) {
	                    die("string too short for \\u escape");
	                }
	                i++;
	                int j;
	                for (j = 0; j < 4; j++) {
	                    /* this also handles the surrogate issue */
	                    if (oldstr.charAt(i+j) > 127) {
	                        die("illegal non-ASCII hex digit in \\u escape");
	                    }
	                }
	                int value = 0;
	                try {
	                    value = Integer.parseInt( oldstr.substring(i, i+j), 16);
	                } catch (NumberFormatException nfe) {
	                    die("invalid hex value for \\u escape");
	                }
	                newstr.append(Character.toChars(value));
	                i += j-1;
	                break; /* switch */
	            }

	            case 'U': {
	                if (i+8 > oldstr.length()) {
	                    die("string too short for \\U escape");
	                }
	                i++;
	                int j;
	                for (j = 0; j < 8; j++) {
	                    /* this also handles the surrogate issue */
	                    if (oldstr.charAt(i+j) > 127) {
	                        die("illegal non-ASCII hex digit in \\U escape");
	                    }
	                }
	                int value = 0;
	                try {
	                    value = Integer.parseInt(oldstr.substring(i, i+j), 16);
	                } catch (NumberFormatException nfe) {
	                    die("invalid hex value for \\U escape");
	                }
	                newstr.append(Character.toChars(value));
	                i += j-1;
	                break; /* switch */
	            }

	            default:   newstr.append('\\');
	                       newstr.append(Character.toChars(cp));
	           /*
	            * say(String.format(
	            *       "DEFAULT unrecognized escape %c passed through",
	            *       cp));
	            */
	                       break; /* switch */

	        }
	        saw_backslash = false;
	    }

	    /* weird to leave one at the end */
	    if (saw_backslash) {
	        newstr.append('\\');
	    }

	    return newstr.toString();
	}

	/*
	 * Return a string "U+XX.XXX.XXXX" etc, where each XX set is the
	 * xdigits of the logical Unicode code point. No bloody brain-damaged
	 * UTF-16 surrogate crap, just true logical characters.
	 */
	 public final static
	 String uniplus(String s) {
	     if (s.length() == 0) {
	         return "";
	     }
	     /* This is just the minimum; sb will grow as needed. */
	     StringBuffer sb = new StringBuffer(2 + 3 * s.length());
	     sb.append("U+");
	     for (int i = 0; i < s.length(); i++) {
	         sb.append(String.format("%X", s.codePointAt(i)));
	         if (s.codePointAt(i) > Character.MAX_VALUE) {
	             i++; /****WE HATES UTF-16! WE HATES IT FOREVERSES!!!****/
	         }
	         if (i+1 < s.length()) {
	             sb.append(".");
	         }
	     }
	     return sb.toString();
	 }

	private static final
	void die(String foa) {
	    throw new IllegalArgumentException(foa);
	}

	private static final
	void say(String what) {
	    System.out.println(what);
	}
}
