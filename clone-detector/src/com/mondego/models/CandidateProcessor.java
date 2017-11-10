package com.mondego.models;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mondego.indexbased.SearchManager;

public class CandidateProcessor implements IListener, Runnable {
    private QueryCandidates qc;
    private static final Logger logger = LogManager.getLogger(CandidateProcessor.class);

    public CandidateProcessor(QueryCandidates qc) {
        // TODO Auto-generated constructor stub
        this.qc = qc;
    }

    @Override
    public void run() {
        try {
            this.processResultWithFilter(this.qc);
        } catch (NoSuchElementException e) {
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        } catch (Exception e) {
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        }
    }

    static void processResultWithFilter(QueryCandidates qc) throws InterruptedException, InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        Map<Long, CandidateSimInfo> simMap = qc.simMap;
        QueryBlock queryBlock = qc.queryBlock;
        long sstart_time = System.currentTimeMillis();
        logger.debug(
                SearchManager.NODE_PREFIX + ", num candidates: " + simMap.entrySet().size() + ", query: " + queryBlock);
        
        // This makes a lot of function calls
        simMap.entrySet().parallelStream().forEach(e -> {
            CandidateSimInfo simInfo = e.getValue();
            long candidateId = simInfo.doc.fId;
            long functionIdCandidate = simInfo.doc.pId;
            int newCt = -1;
            int candidateSize = simInfo.doc.size;
            if (candidateSize < queryBlock.getComputedThreshold() || candidateSize > queryBlock.getMaxCandidateSize()) {
                return; // ignore this candidate
            }
            if (candidateSize > queryBlock.getSize()) {
                newCt = simInfo.doc.ct;
            }
            CandidatePair candidatePair = null;
            if (newCt != -1) {
                candidatePair = new CandidatePair(queryBlock, simInfo, newCt, candidateSize,
                        functionIdCandidate, candidateId);
            } else {
                candidatePair = new CandidatePair(queryBlock, simInfo,
                        queryBlock.getComputedThreshold(), candidateSize, functionIdCandidate, candidateId);
            }
            
            try {
				CloneValidator.validate(candidatePair);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException | InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
        });
    }

}
