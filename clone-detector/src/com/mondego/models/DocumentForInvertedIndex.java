package com.mondego.models;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class DocumentForInvertedIndex {
    public long id;
    public long fId;
    public long pId;
    public int size;
    public int ct;
    public int prefixSize;
    public Map<String,TermInfo> termInfoMap;
    public Set<Token> tokenFrequencies; 
    
    public DocumentForInvertedIndex() {
        super();
        this.termInfoMap = new HashMap<String,TermInfo>();
        this.tokenFrequencies = new LinkedHashSet<Token>();
    }
}
