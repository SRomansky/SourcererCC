package com.mondego.httpcommunication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.mondego.indexbased.Daemon;

@Path("/status")
public class Status {
	private static final ExecutorService TASK_EXECUTOR = Executors.newCachedThreadPool();

	@GET
	public String getStatus() {
		// TODO figure out how to make this async
		Daemon daemon = Daemon.getInstance();

		return daemon.getState().toString(); // TODO what does the enum look like as a String?
	}
}
