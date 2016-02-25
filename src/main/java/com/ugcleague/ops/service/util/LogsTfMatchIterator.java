package com.ugcleague.ops.service.util;

import com.ugcleague.ops.service.LogsTfApiClient;
import com.ugcleague.ops.web.rest.LogsTfMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * An iterator to retrieve LogsTf matches as they are needed.
 */
public class LogsTfMatchIterator implements Iterator<LogsTfMatch> {

    private static final Logger log = LoggerFactory.getLogger(LogsTfMatchIterator.class);

    private final LogsTfApiClient client;
    private final Queue<LogsTfMatch> cache = new PriorityQueue<>();
    private final Set<Long> cached = new LinkedHashSet<>();
    private long id;
    private int limit;

    public LogsTfMatchIterator(LogsTfApiClient client, long id64, int chunkSize) {
        this.client = client;
        this.id = id64;
        this.limit = Math.max(1, chunkSize);
        loadMatches();
    }

    @Override
    public boolean hasNext() {
        return cache.peek() != null || (loadMatches() > 0);
    }

    private int loadMatches() {
        int queued = 0;
        List<LogsTfMatch> list = client.getMatches(id, limit);
        if (list != null) {
            for (LogsTfMatch match : list) {
                if (!cached.contains(match.getId())) {
                    cached.add(match.getId());
                    cache.add(match);
                    queued++;
                }
            }
        }
        log.info("LogsTF: Loaded {} new matches", queued);
        return queued;
    }

    @Override
    public LogsTfMatch next() {
        return cache.poll();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

}


