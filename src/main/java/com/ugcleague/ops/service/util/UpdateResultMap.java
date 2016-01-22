package com.ugcleague.ops.service.util;

import com.ugcleague.ops.domain.GameServer;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class UpdateResultMap extends ConcurrentHashMap<GameServer, UpdateResult> {

    public List<GameServer> getSlowUpdates(int attempts) {
        return entrySet().stream()
            .filter(e -> e.getValue().getAttempts().get() > attempts)
            .map(Entry::getKey)
            .collect(Collectors.toList());
    }

    public List<GameServer> getAndResetSlowUpdates(int attempts) {
        List<GameServer> failed = getSlowUpdates(10);
        resetAttemptsFrom(failed);
        return failed;
    }

    private void resetAttemptsFrom(List<GameServer> list) {
        entrySet().stream()
            .filter(e -> list.contains(e.getKey()))
            .forEach(e -> e.getValue().getAttempts().set(0));
    }

    public UpdateResultMap duplicate() {
        UpdateResultMap copy = new UpdateResultMap();
        entrySet().forEach(e -> copy.put(e.getKey(), e.getValue()));
        return copy;
    }
}
