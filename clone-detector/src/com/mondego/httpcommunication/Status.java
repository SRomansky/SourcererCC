package com.mondego.httpcommunication;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.mondego.indexbased.Daemon;

@Path("/status")
public class Status {
	@GET
	public String getStatus() {
		Daemon daemon = Daemon.getInstance();

		return daemon.getState().toString(); // TODO what does the enum look like as a String?
	}
}
