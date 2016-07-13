package com.ugcleague.ops.service.discord.util;

import sx.blah.discord.handle.audio.IAudioProvider;

public class SilenceProvider implements IAudioProvider {

    private final long delay;
    private volatile long elapsed = 0L;

    public SilenceProvider(long delay) {
        this.delay = delay;
    }

    @Override
    public boolean isReady() {
        return elapsed < delay;
    }

    @Override
    public byte[] provide() {
        elapsed += 20;
        return new byte[0];
    }
}
