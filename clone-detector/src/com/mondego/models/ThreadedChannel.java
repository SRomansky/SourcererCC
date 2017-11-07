package com.mondego.models;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ThreadedChannel<E> {

    private ExecutorService executor;
    private Class<Runnable> workerType;
    private Semaphore semaphore;
	static private ConcurrentHashMap<String, Integer> blockedRecorder = new ConcurrentHashMap();
    private static final Logger logger = LogManager.getLogger(ThreadedChannel.class);

    public ThreadedChannel(int nThreads) {
    		this.executor = Executors.newFixedThreadPool(5);
    }
    
    public ThreadedChannel(int nThreads, Class clazz) {
        this.executor = Executors.newFixedThreadPool(nThreads);
        this.workerType = clazz;
        this.semaphore = new Semaphore(nThreads);
    }

    /**
     * complete all of the current tasks
     */
    public void finish() {
    		this.shutdown();
    		this.executor = Executors.newFixedThreadPool(5);
    }
    
    public void send(E e) throws InstantiationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, SecurityException {
    	
    	
        final Runnable o = this.workerType.getDeclaredConstructor(e.getClass()).newInstance(e);
        try {
        	if (blockedRecorder.containsKey(this.workerType.getCanonicalName())) {
        		blockedRecorder.put(this.workerType.getCanonicalName(), blockedRecorder.get(this.workerType.getCanonicalName()) + 1);
        	} else {
        		blockedRecorder.put(this.workerType.getCanonicalName(), 1);
        	}
        	
        	if (blockedRecorder.values().stream().mapToInt(Integer::intValue).sum() % 1000000 == 0)
        		System.out.println(blockedRecorder.toString());
            semaphore.acquire();
        } catch (InterruptedException ex) {
            logger.error("Caught interrupted exception " + ex);
        }

        try {
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        o.run();
                    } finally {
                        semaphore.release();
                    }
                }
            });
        } catch (RejectedExecutionException ex) {
            semaphore.release();
        }
    }

    public void shutdown() {
        this.executor.shutdown();
        try {
            this.executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            logger.error("inside catch, shutdown");
        }
    }
}
