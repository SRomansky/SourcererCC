package com.mondego.httpcommunication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.mondego.indexbased.Daemon;

@Path("/batch")
public class BatchName {
	private static final ExecutorService TASK_EXECUTOR = Executors.newCachedThreadPool();


	@GET
	public String getBatchName() {
		// TODO figure out how to make this async
		Daemon daemon = Daemon.getInstance();

		return daemon.sm.batch_name;
	}
}
