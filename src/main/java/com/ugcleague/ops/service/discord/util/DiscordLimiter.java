package com.ugcleague.ops.service.discord.util;

import com.google.common.util.concurrent.RateLimiter;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiscordLimiter {

    private static final RateLimiter GLOBAL = RateLimiter.create(5);
    private static final Map<String, RateLimiter> SERVER = new ConcurrentHashMap<>();
    private static final RateLimiter DM = RateLimiter.create(1);
    private static final RateLimiter DELETE = RateLimiter.create(1);

    public static void acquire(IChannel channel) {
        if (channel.isPrivate()) {
            acquireDM();
        } else {
            acquire(channel.getGuild().getID());
        }
    }

    public static void acquire(IMessage message) {
        if (message.getChannel().isPrivate()) {
            acquireDM();
        } else {
            acquire(message.getChannel().getGuild().getID());
        }
    }

    public static void acquire(String guildId) {
        SERVER.computeIfAbsent(guildId, k -> RateLimiter.create(1)).acquire();
        GLOBAL.acquire();
    }

    public static void acquireDM() {
        DM.acquire();
    }

    public static void acquireDelete() {
        DELETE.acquire();
    }

    private DiscordLimiter() {

    }
}
