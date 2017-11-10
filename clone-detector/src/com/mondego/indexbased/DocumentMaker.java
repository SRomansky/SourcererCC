/**
 * 
 */
package com.mondego.indexbased;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;

import com.mondego.models.Bag;
import com.mondego.models.DocumentForInvertedIndex;
import com.mondego.models.Token;
import com.mondego.models.TokenFrequency;
import com.mondego.utility.BlockInfo;

/**
 * @author vaibhavsaini
 * 
 */
public class DocumentMaker {
    private IndexWriter indexWriter;

    /**
     * @param indexDir
     * @param indexWriter
     * @param cloneHelper
     * @param isAppendIndex
     * @param isPrefixIndex
     */
    private static final Logger logger = LogManager.getLogger(DocumentMaker.class);
    public DocumentMaker(IndexWriter indexWriter) {
        super();
        this.indexWriter = indexWriter;
    }
    
    public DocumentMaker(){
        super();
    }


    private Document wfmEntry;
    private StringField wordField;
    private StoredField freqField;
    public void indexWFMEntry(String word, long frequency) {
	// Create the document and fields only once, for no GC
	if (wfmEntry == null) {
	    wfmEntry = new Document();
	    wordField = new StringField("key", word,
					       Field.Store.NO);
	    wfmEntry.add(wordField);
	    freqField = new StoredField("frequency", frequency);
	    wfmEntry.add(freqField);
	}
	else {
	    wordField.setStringValue(word);
	    freqField.setLongValue(frequency);
	}

        try {
	    this.indexWriter.updateDocument(new Term("key", word), wfmEntry);
        } catch (IOException e) {
            logger.error("EXCEPTION caught while indexing document for wfm entry "
                            + word + ":" + frequency);
            e.printStackTrace();
        }
    }

    public Document prepareDocumentForFwdIndex(Bag bag) {
        Document document = new Document();
        StringField idField = new StringField("id", bag.getId() + "",
                Field.Store.NO);
        document.add(idField);
        
        StringBuilder tokenString = new StringBuilder();
        for (Token tf : bag) {
            tokenString.append(tf.getValue() + ":" + tf.getFrequency() + "::");
        }
        StoredField strField = new StoredField("tokens", tokenString.toString().trim());
        document.add(strField);
        return document;
    }

    public DocumentForInvertedIndex prepareDocumentForII(Bag bag) {
        DocumentForInvertedIndex document = new DocumentForInvertedIndex();
        document.id = SearchManager.getNextId();
        document.fId = bag.getId();
        document.pId = bag.getFunctionId();
        document.size = bag.getSize();
        document.ct = BlockInfo.getMinimumSimilarityThreshold(bag.getSize(),
                SearchManager.th);
        document.prefixSize = BlockInfo.getPrefixSize(bag.getSize(), document.ct);
        return document;
    }

    public void closeIndexWriter() {
        try {
            this.indexWriter.close();
        } catch (IOException e) {
            logger.warn(e.getMessage());
        }

    }

    public IndexWriter getIndexWriter() {
        return indexWriter;
    }

    public void setIndexWriter(IndexWriter indexWriter) {
        this.indexWriter = indexWriter;
    }

	public void indexWFMEntries(Map<String, Long> newEntries) {
		List<Document> docs = newEntries.keySet().stream().map(k -> {
			Document d = new Document();
			StringField s = new StringField("key", k, Field.Store.NO);
			StoredField f = new StoredField("frequency", newEntries.get(k));
			d.add(s);
			d.add(f);
			return d;
		})
		.collect(Collectors.toList());
		
		try {
			this.indexWriter.updateDocuments(null, docs);
		} catch (IOException e) {
			e.printStackTrace();
		} // ?
	}

}
