package com.ugcleague.ops.service.util;

import com.codahale.metrics.MetricRegistry;
import com.ugcleague.ops.domain.document.GameServer;

public class MetricNames {

    public static final String DISCORD_USERS_JOINED = "discord.users.joined";
    public static final String DISCORD_USERS_CONNECTED = "discord.users.connected";
    public static final String DISCORD_USERS_ONLINE = "discord.users.online";
    public static final String DISCORD_WS_RESTARTS = "discord.ws.restarts";
    public static final String HEALTH_DISCORD_WS = "Discord.WebSocket";

    public static String gameServerPing(GameServer server) {
        return MetricRegistry.name("gs", "ping", server.getShortName());
    }

    public static String gameServerPlayers(GameServer server) {
        return MetricRegistry.name("gs", "players", server.getShortName());
    }

    private MetricNames() {

    }
}
