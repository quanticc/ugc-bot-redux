package com.ugcleague.ops.service.util;

import com.ugcleague.ops.service.SizzlingApiClient;
import com.ugcleague.ops.web.rest.SizzMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

/**
 * An iterator to retrieve SizzlingStats matches as they are needed.
 */
public class SizzMatchIterator implements Iterator<SizzMatch> {

    private static final Logger log = LoggerFactory.getLogger(SizzMatchIterator.class);

    private SizzlingApiClient client;
    private Queue<SizzMatch> cache = new ArrayDeque<>();
    private long id;
    private int skip = 0;

    public SizzMatchIterator(SizzlingApiClient client, long id64) {
        this.client = client;
        this.id = id64;
        loadMatches();
    }

    @Override
    public boolean hasNext() {
        if (cache.peek() != null) {
            return true;
        }
        List<SizzMatch> list = loadMatches();
        return (list != null && !list.isEmpty());
    }

    private List<SizzMatch> loadMatches() {
        List<SizzMatch> list = client.getMatches(id, Math.max(1, skip));
        skip += list.size();
        log.info("Loaded {} matches from SizzlingStats", list.size());
        cache.addAll(list);
        return list;
    }

    @Override
    public SizzMatch next() {
        return cache.poll();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

}


