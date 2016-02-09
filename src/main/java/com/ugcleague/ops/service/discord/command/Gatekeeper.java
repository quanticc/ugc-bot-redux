package com.ugcleague.ops.service.discord.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * For command throttling
 */
public class Gatekeeper {

    private static final Logger log = LoggerFactory.getLogger(Gatekeeper.class);

    private final Map<String, List<CommandJob>> requests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService dispatcher = Executors.newSingleThreadScheduledExecutor();

    public int getQueuedJobCount(String key) {
        return requests.containsKey(key) ? requests.get(key).size() : 0;
    }

    public CompletableFuture<String> queue(final String key, final CommandJob job) {
        log.info("+++ Queueing command job to '{}': {}", key, job.getMessage().getContent());
        requests.computeIfAbsent(key, k -> new ArrayList<>()).add(job);
        return CompletableFuture.supplyAsync(job, dispatcher)
            .exceptionally(t -> {
                log.warn("Background job '" + key + "' terminated exceptionally", t);
                return ":no_good: Something happened. Something happened.";
            }).thenApply(s -> {
                log.info("--- Removing command job from '{}': {}", key, job.getMessage().getContent());
                requests.get(key).remove(job);
                return s;
            });
    }
}
