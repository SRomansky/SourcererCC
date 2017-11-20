package com.mondego.noindex;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mondego.models.Bag;
import com.mondego.models.Token;
import com.mondego.utility.Util;

/**
 * 
 */

/**
 * @author vaibhavsaini
 * 
 */
public class CloneTestHelper {

    /**
     * returns a set of 10 bags
     * 
     * @return Set<Bag>
     */
    public static HashSet<Bag> getTestSet(int start, int stop) {
        HashSet<Bag> set = new HashSet<Bag>();
        for (int i = start; i < stop; i++) {
            set.add(getTestBag(i));
        }
        return set;
    }

    /**
     * 
     * @param i
     *            integer to create value of a token
     * @return Token
     */
    public static Token getTestToken() {
        return new Token("t" + Util.getRandomNumber(21, 1));
    }

    /**
     * creates and return a bag of 10 tokens
     * 
     * @param i
     *            id of the bag
     * @return Bag
     */
    public static Bag getTestBag(int i) {
        Bag bag = new Bag(i);
        for (int j = 0; j < 10; j++) {
            Token t = getTestToken();
            t.setFrequency(Util.getRandomNumber(1, 1));
            bag.add(t);
        }
        return bag;
    }

    public static Map<String, Integer> getGlobalTokenPositionMap(
            Set<Bag> setA, Set<Bag> setB) {
        Map<String, Integer> tokenPositionMap = new HashMap<String, Integer>();
        Map<Token, Token> map = new HashMap<Token, Token>();
        fetchTokenFrequencyList(map, setA);
        fetchTokenFrequencyList(map, setB);
        List<Token> list = new ArrayList<Token>( map.values());
        Collections.sort(list, new Comparator<Token>() {
            public int compare(Token tfFirst, Token tfSecond) {
                return tfFirst.getFrequency() - tfSecond.getFrequency();
            }
        });
        int position = 0;
        for (Token tokenFrequency : list) {
            tokenPositionMap.put(tokenFrequency.getValue(), position);
            position++;
        }
        return tokenPositionMap;
    }

    private static void fetchTokenFrequencyList(
            Map<Token, Token> map, Set<Bag> setA) {
        for (Bag bag : setA) {
            mergeCollections(map, bag);
        }
    }

    private static void mergeCollections(
            Map<Token, Token> map,
            Collection<Token> listB) {
        for (Token tf : listB) {
            if (map.containsKey(tf)) {
            	Token tokenFrequency = map.get(tf);
                tokenFrequency.setFrequency(tokenFrequency.getFrequency()
                        + tf.getFrequency());
            } else {
                map.put(tf, tf);
            }
        }
    }
}
