package com.mondego.httpcommunication;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.mondego.indexbased.Daemon;

@Path("/status")
public class Status {
	// String state = Daemon.getState();
	// TODO return a message conveying the current state of the shard.
//	return "Status command is not implemented yet.";
	
	@GET
	public String getStatus() {
		Daemon daemon = Daemon.getInstance();

		return daemon.getState().toString(); // TODO what does the enum look like as a String?
	}
}
