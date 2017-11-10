package com.mondego.models;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mondego.indexbased.DocumentMaker;
import com.mondego.indexbased.SearchManager;

public class InvertedIndexCreator implements IListener, Runnable {
    private DocumentMaker documentMaker;
    private Bag bag;
    private static final Logger logger = LogManager
            .getLogger(InvertedIndexCreator.class);

    public InvertedIndexCreator(Bag bag) {
        super();
        this.documentMaker = new DocumentMaker();
        this.bag = bag;
    }

    @Override
    public void run() {
        try {
            this.index(this.bag);
        } catch (NoSuchElementException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    static public void index(Bag bag) throws InterruptedException,
            InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException,
            NoSuchMethodException, SecurityException {
    		DocumentMaker documentMaker = new DocumentMaker();
        DocumentForInvertedIndex documentForII = documentMaker.prepareDocumentForII(bag);
        SearchManager.documentsForII.put(documentForII.id, documentForII);
        Set<Long> docs = null;
        int prefixLength = documentForII.prefixSize;
        int pos = 0;
        TermInfo termInfo = null;
        for (Token tf : bag) {
            if (prefixLength > 0) {
                String term = tf.getValue();
                if (SearchManager.invertedIndex.containsKey(term)){
                    docs= SearchManager.invertedIndex.get(term);
                }else{
                    docs = new HashSet<Long>();
                    SearchManager.invertedIndex.put(term, docs);
                }
                docs.add(documentForII.id);
                termInfo = new TermInfo();
                termInfo.frequency=tf.getFrequency();
                termInfo.position = pos;
                pos = pos + tf.getFrequency();
                documentForII.termInfoMap.put(term, termInfo);
                prefixLength -= tf.getFrequency();
            }
            documentForII.tokenFrequencies.add(tf);
        }
    }

    public DocumentMaker getIndexer() {
        return documentMaker;
    }
}
