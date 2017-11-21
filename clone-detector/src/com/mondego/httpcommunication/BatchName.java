package com.mondego.httpcommunication;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.mondego.indexbased.Daemon;

@Path("/batch")
public class BatchName {

	@GET
	public String getBatchName() {
		Daemon daemon = Daemon.getInstance();

		return daemon.sm.batch_name;
	}
}
