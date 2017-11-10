package com.mondego.models;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mondego.indexbased.SearchManager;
import com.mondego.utility.Util;

public class CloneValidator implements IListener, Runnable {
    private CandidatePair candidatePair;
    private static final Logger logger = LogManager.getLogger(CloneValidator.class);

    public CloneValidator(CandidatePair candidatePair) {
        // TODO Auto-generated constructor stub
        this.candidatePair = candidatePair;
    }

    @Override
    public void run() {
        try {
            this.validate(this.candidatePair);
        } catch (NoSuchElementException e) {
            logger.error("EXCEPTION CAUGHT::", e);
            e.printStackTrace();
        } catch (InterruptedException e) {
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
        }
    }

    static void validate(CandidatePair candidatePair)
            throws InterruptedException, InstantiationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, SecurityException {

        long startTime = System.nanoTime();
        if (candidatePair.simInfo.doc.tokenFrequencies!= null && candidatePair.simInfo.doc.tokenFrequencies.size() > 0) {
            int similarity = updateSimilarity(candidatePair.queryBlock,
                    candidatePair.computedThreshold, candidatePair.candidateSize, candidatePair.simInfo);
            
            if (similarity > 0) {
                ClonePair cp = new ClonePair(candidatePair.queryBlock.getFunctionId(), candidatePair.queryBlock.getId(),
                        candidatePair.functionIdCandidate, candidatePair.candidateId);
                long estimatedTime = System.nanoTime() - startTime;
                logger.debug(SearchManager.NODE_PREFIX + " CloneValidator, QueryBlock " + candidatePair + " in "
                        + estimatedTime / 1000 + " micros");
                SearchManager.reportCloneQueue.send(cp);
            }

        } else {
            logger.debug("tokens not found for document");
        }
    }

    static private int updateSimilarity(QueryBlock queryBlock,
            int computedThreshold, int candidateSize, CandidateSimInfo simInfo) {
        int tokensSeenInCandidate = 0;
        int similarity = simInfo.similarity;
        TokenInfo tokenInfo = null;
        boolean matchFound = false;
        for (Token tf : simInfo.doc.tokenFrequencies) {
            if (Util.isSatisfyPosFilter(similarity, queryBlock.getSize(), simInfo.queryMatchPosition, candidateSize,
                    simInfo.candidateMatchPosition, computedThreshold)) {
                tokensSeenInCandidate += tf.getFrequency();
                if (tokensSeenInCandidate > simInfo.candidateMatchPosition) {
                    simInfo.candidateMatchPosition = tokensSeenInCandidate;
                    matchFound = false;
                    if (simInfo.queryMatchPosition < queryBlock.getPrefixMapSize()) {
                        // check in prefix
                        if (queryBlock.getPrefixMap().containsKey(tf.getValue())) {
                            matchFound = true;
                            tokenInfo = queryBlock.getPrefixMap().get(tf.getValue());
                            similarity = updateSimilarityHelper(simInfo, tokenInfo, similarity, tf.getFrequency(),
                                    tf.getValue());
                        }
                    }
                    // check in suffix
                    if (!matchFound && queryBlock.getSuffixMap().containsKey(tf.getValue())) {
                        tokenInfo = queryBlock.getSuffixMap().get(tf.getValue());
                        similarity = updateSimilarityHelper(simInfo, tokenInfo, similarity, tf.getFrequency(),
                                tf.getValue());
                    }
                    if (similarity >= computedThreshold) {
                        return similarity;
                    }
                }
            } else {
                break;
            }
        }
        return -1;
    }

    static private int updateSimilarityHelper(CandidateSimInfo simInfo, TokenInfo tokenInfo, int similarity,
            int candidatesTokenFreq, String token) {
        simInfo.queryMatchPosition = tokenInfo.getPosition();
        similarity += Math.min(tokenInfo.getFrequency(), candidatesTokenFreq);

        return similarity;
    }
}
