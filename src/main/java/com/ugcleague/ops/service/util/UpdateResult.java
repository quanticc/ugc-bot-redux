package com.ugcleague.ops.service.util;

import lombok.Data;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Data
public class UpdateResult {
    private final AtomicInteger attempts = new AtomicInteger(0);
    private final AtomicReference<Instant> lastRconAnnounce = new AtomicReference<>(Instant.EPOCH);
}
