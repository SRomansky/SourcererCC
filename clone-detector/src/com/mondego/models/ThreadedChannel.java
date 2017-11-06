package com.mondego.models;

import java.lang.reflect.InvocationTargetException;
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
//    	final Runnable o;
//    	if (e.getClass() == QueryBlock.class) {
//    		o = CandidateSearcher.class.getDeclaredConstructor(e.getClass()).newInstance(e);
//    	} else if (e.getClass() == String.class) {
//    		o = QueryLineProcessor.class.getDeclaredConstructor(e.getClass()).newInstance(e);
//    	} else if (e.getClass() == QueryCandidates.class) {
//    		o = CandidateProcessor.class.getDeclaredConstructor(e.getClass()).newInstance(e);
//    	} else if (e.getClass() == CandidatePair.class) {
//    		o = CloneValidator.class.getDeclaredConstructor(e.getClass()).newInstance(e);
//    	} else if (e.getClass() == ClonePair.class) {
//    		o = CloneReporter.class.getDeclaredConstructor(e.getClass()).newInstance(e);
//    	} else if (e.getClass() == Bag.class) {
//    		o = InvertedIndexCreator.class.getDeclaredConstructor(e.getClass()).newInstance(e);
//    	} else {
//    		throw new NoSuchMethodException("Unknown object: " + e.getClass());
//    	}
    	
    	
        final Runnable o = this.workerType.getDeclaredConstructor(e.getClass()).newInstance(e);
        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            logger.error("Caught interrupted exception " + ex);
        }

        try {
//        	executor.execute(o);
            executor.execute(new Runnable() {
                public void run() {
//                	o.run();
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
