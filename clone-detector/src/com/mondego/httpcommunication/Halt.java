package com.mondego.httpcommunication;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Path("/halt")
public class Halt {
	private static final Logger logger = LogManager.getLogger(Halt.class);
	
	@GET
	public void halt() {
		// TODO log shutting down?
		logger.info("Halting.");
		System.exit(0);
	}
}
