package com.ugcleague.ops.service.util;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class UpdateResult {

    private final AtomicInteger attempts;
    private final AtomicReference<Instant> lastRconAnnounce;

    public UpdateResult() {
        this(0, Instant.EPOCH);
    }

    public UpdateResult(int attempts, Instant lastRconAnnounce) {
        this.attempts = new AtomicInteger(attempts);
        this.lastRconAnnounce = new AtomicReference<>(lastRconAnnounce);
    }

    public AtomicInteger getAttempts() {
        return attempts;
    }

    public AtomicReference<Instant> getLastRconAnnounce() {
        return lastRconAnnounce;
    }
}
