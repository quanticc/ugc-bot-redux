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

public class Gatekeeper {

    private static final Logger log = LoggerFactory.getLogger(Gatekeeper.class);

    private final Map<String, List<CommandJob>> requests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService dispatcher = Executors.newSingleThreadScheduledExecutor();

    public int getQueuedJobCount(String key) {
        return requests.containsKey(key) ? requests.get(key).size() : 0;
    }

    public CompletableFuture<String> queue(String key, CommandJob job) {
        requests.computeIfAbsent(key, k -> new ArrayList<>()).add(job);
        return CompletableFuture.supplyAsync(job, dispatcher)
            .exceptionally(Throwable::getMessage).thenApply(s -> {
                requests.get(key).remove(job);
                return s;
            });
    }
}
