package com.ugcleague.ops.service.util;

import com.ugcleague.ops.domain.GameServer;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DeadServerMap extends ConcurrentHashMap<GameServer, DeadServerInfo> {

    public List<GameServer> getNonResponsive(int attempts) {
        return entrySet().stream()
            .filter(e -> e.getValue().getAttempts().get() > attempts)
            .map(Entry::getKey)
            .collect(Collectors.toList());
    }

    private void resetAttemptsFrom(List<GameServer> list) {
        entrySet().stream()
            .filter(e -> list.contains(e.getKey()))
            .forEach(e -> e.getValue().getAttempts().set(0));
    }

    public DeadServerMap duplicate() {
        DeadServerMap copy = new DeadServerMap();
        entrySet().forEach(e -> copy.put(e.getKey(), e.getValue()));
        return copy;
    }
}
