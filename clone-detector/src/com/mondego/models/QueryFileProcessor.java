package com.mondego.models;

import java.lang.reflect.InvocationTargetException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mondego.indexbased.SearchManager;

public class QueryFileProcessor implements ITokensFileProcessor {
    private static final Logger logger = LogManager.getLogger(QueryFileProcessor.class);
    public QueryFileProcessor() {
    }

    @Override
    public void processLine(String line) {
        try {
        	QueryLineProcessor.processLine(line);
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage()
                    + " skiping this query block, illegal args: "
                    + line.substring(0, 40));
            e.printStackTrace();
        }
    }

}
