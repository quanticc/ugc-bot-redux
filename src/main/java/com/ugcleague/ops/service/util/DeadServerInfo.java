package com.ugcleague.ops.service.util;

import com.ugcleague.ops.domain.GameServer;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class DeadServerInfo {

    private final GameServer gameServer;
    private final Instant created;
    private final AtomicInteger attempts;

    public DeadServerInfo(GameServer gameServer) {
        this(gameServer, Instant.now(), 0);
    }

    public DeadServerInfo(GameServer gameServer, Instant firstAttempt, int attempts) {
        this.gameServer = gameServer;
        this.created = firstAttempt;
        this.attempts = new AtomicInteger(attempts);
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
