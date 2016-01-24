package com.ugcleague.ops.service.util;

import com.ugcleague.ops.domain.GameServer;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class DeadServerInfo {

    private final GameServer gameServer;
    private final Instant created;
    private final AtomicInteger attempts;

    public DeadServerInfo(GameServer gameServer) {
        this.gameServer = gameServer;
        this.created = Instant.now();
        this.attempts = new AtomicInteger(0);
    }

    public GameServer getGameServer() {
        return gameServer;
    }

    public Instant getCreated() {
        return created;
    }

    public AtomicInteger getAttempts() {
        return attempts;
    }
}
