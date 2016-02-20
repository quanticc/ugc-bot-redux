package com.ugcleague.ops.service;

import com.codahale.metrics.*;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.service.discord.command.SplitMessage;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import com.ugcleague.ops.service.util.MetricNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.api.*;
import sx.blah.discord.handle.EventSubscriber;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.DiscordDisconnectedEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.obj.Invite;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.HTTP429Exception;
import sx.blah.discord.util.Image;

import javax.annotation.PostConstruct;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

@Service
@Transactional
public class DiscordService implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(DiscordService.class);
    private static final int LENGTH_LIMIT = 2000;

    private final LeagueProperties properties;
    private final HealthCheckRegistry healthCheckRegistry;
    private final Queue<IListener<?>> queuedListeners = new ConcurrentLinkedQueue<>();
    private final Queue<DiscordSubscriber> queuedSubscribers = new ConcurrentLinkedQueue<>();
    private volatile IDiscordClient client;
    private final AtomicBoolean reconnect = new AtomicBoolean(true);
    private final Histogram responseTime;
    private final Counter restartCounter;
    private volatile DiscordDisconnectedEvent lastDisconnectEvent = null;
    private volatile ZonedDateTime lastRestartTime = null;

    @Autowired
    public DiscordService(LeagueProperties properties, MetricRegistry metricRegistry,
                          HealthCheckRegistry healthCheckRegistry) {
        this.properties = properties;
        this.healthCheckRegistry = healthCheckRegistry;
        this.responseTime = metricRegistry.register(MetricNames.DISCORD_WS_RESPONSE_HISTOGRAM,
            new Histogram(new UniformReservoir()));
        metricRegistry.register(MetricNames.DISCORD_WS_RESPONSE, (Gauge<Long>) this::getMeanResponseTime);
        metricRegistry.register(MetricNames.DISCORD_USERS_JOINED, (Gauge<Long>) this::getUserCount);
        metricRegistry.register(MetricNames.DISCORD_USERS_CONNECTED, (Gauge<Long>) this::getConnectedUserCount);
        metricRegistry.register(MetricNames.DISCORD_USERS_ONLINE, (Gauge<Long>) this::getOnlineUserCount);
        this.restartCounter = metricRegistry.register(MetricNames.DISCORD_WS_RESTARTS, new Counter());
    }

    @PostConstruct
    private void configure() {
        healthCheckRegistry.register(MetricNames.HEALTH_DISCORD_WS, new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                long mean = (long) responseTime.getSnapshot().getMean();
                long restarts = restartCounter.getCount();
                if (restarts > 0) {
                    return Result.unhealthy(String.format("%d restarts, last one on %s (%s). Mean response time: %dms",
                        restarts, lastRestartTime, lastDisconnectEvent.getReason().toString(), mean));
                } else {
                    return Result.healthy("Mean response time: " + mean + "ms");
                }
            }
        });
        if (properties.getDiscord().isAutologin()) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(3000); // wait a bit before firing
                    login();
                } catch (InterruptedException | DiscordException e) {
                    log.warn("Could not connect discord bot", e);
                }
            });
        }
    }

    public void login() throws DiscordException {
        LeagueProperties.Discord discord = properties.getDiscord();
        client = new ClientBuilder()
            .withLogin(discord.getEmail(), discord.getPassword())
            .withTimeout(discord.getTimeoutDelay())
            .withPingTimeout(discord.getMaxMissedPings())
            .login();
        log.debug("Registering {} Discord event subscribers", queuedListeners.size() + queuedSubscribers.size());
        queuedListeners.forEach(listener -> client.getDispatcher().registerListener(listener));
        queuedSubscribers.forEach(subscriber -> client.getDispatcher().registerListener(subscriber));
        client.getDispatcher().registerListener(this);
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        log.info("*** Discord bot armed ***");
        for (String guildId : properties.getDiscord().getQuitting()) {
            IGuild guild = client.getGuildByID(guildId);
            if (guild != null) {
                try {
                    leaveGuild(guild);
                } catch (InterruptedException e) {
                    log.warn("Could not leave guild after retrying: {}", e.toString());
                } catch (DiscordException e) {
                    log.warn("Discord exception", e);
                }
            }
        }
        List<IGuild> guildList = client.getGuilds();
        for (IGuild guild : guildList) {
            log.info("{}", guildString(guild, client.getOurUser()));
        }
        for (String inviteCode : properties.getDiscord().getInvites()) {
            Invite invite = (Invite) client.getInviteForCode(inviteCode);
            try {
                Invite.InviteResponse response = invite.details();
                if (client.getGuildByID(response.getGuildID()) == null) {
                    log.info("Accepting invite to {} ({}) @ {} ({})",
                        response.getChannelName(), response.getChannelID(), response.getGuildName(), response.getGuildID());
                    invite.accept();
                    IGuild guild = client.getGuildByID(response.getGuildID());
                    if (guild != null) {
                        log.info("{}", guildString(guild, client.getOurUser()));
                    }
                } else {
                    log.info("Invite already accepted: {}", inviteCode);
                }
            } catch (Exception e) {
                log.warn("Could not accept invite {}: {}", inviteCode, e.toString());
            }
        }
    }

    @EventSubscriber
    public void onDisconnect(DiscordDisconnectedEvent event) {
        if (reconnect.get()) {
            log.info("Reconnecting bot: {}", event.getReason().toString());
            restartCounter.inc();
            lastDisconnectEvent = event;
            lastRestartTime = ZonedDateTime.now();
            try {
                login();
            } catch (DiscordException e) {
                log.warn("Failed to reconnect bot", e);
            }
        }
    }

    public void logout(boolean thenReconnect) {
        reconnect.set(thenReconnect);
        try {
            client.logout();
        } catch (HTTP429Exception | DiscordException e) {
            log.warn("Logout failed", e);
        }
    }

    public IDiscordClient getClient() {
        return client;
    }

    public boolean isOwnUser(IUser user) {
        return user.getID().equals(client.getOurUser().getID());
    }

    public IUser getMasterUser() {
        return client.getUserByID(properties.getDiscord().getMaster());
    }

    /**
     * Register this listener for Discord4J events
     *
     * @param listener the discord event listener
     */
    public void subscribe(IListener<?> listener) {
        if (client != null && client.isReady()) {
            client.getDispatcher().registerListener(listener);
        } else {
            // queue if the client is not ready yet
            queuedListeners.add(listener);
        }
    }

    /**
     * Register this subscriber as a Discord4J annotation-based event listener
     *
     * @param subscriber the discord subscriber
     */
    public void subscribe(DiscordSubscriber subscriber) {
        if (client != null && client.isReady()) {
            client.getDispatcher().registerListener(subscriber);
        } else {
            // queue if the client is not ready yet
            queuedSubscribers.add(subscriber);
        }
    }

    public void unsubscribe(IListener<?> listener) {
        client.getDispatcher().unregisterListener(listener);
        queuedListeners.remove(listener);
    }

    public void unsubscribe(DiscordSubscriber subscriber) {
        client.getDispatcher().unregisterListener(subscriber);
        queuedSubscribers.remove(subscriber);
    }

    private void sleep(long millis) throws InterruptedException {
        log.info("Backing off for {} ms due to rate limits", millis);
        Thread.sleep(Math.max(1, millis));
    }

    @Async
    public void leaveGuild(IGuild guild) throws DiscordException, InterruptedException {
        while (true) {
            try {
                guild.leaveGuild();
                return;
            } catch (HTTP429Exception e) {
                sleep(e.getRetryDelay());
            }
        }
    }

    @Async
    public CompletableFuture<IMessage> sendMessage(String channelId, String content) throws DiscordException, MissingPermissionsException, InterruptedException {
        IChannel channel = client.getChannelByID(channelId);
        IMessage response = null;
        while (response == null) {
            response = blockingSendMessage(channel, content);
        }
        return CompletableFuture.completedFuture(response);
    }

    @Async
    public CompletableFuture<IMessage> sendMessage(IChannel channel, String content) throws DiscordException, MissingPermissionsException, InterruptedException {
        IMessage response = null;
        while (response == null) {
            response = blockingSendMessage(channel, content);
        }
        return CompletableFuture.completedFuture(response);
    }

    public CompletableFuture<IMessage> sendPrivateMessage(String userId, String content) throws DiscordException, MissingPermissionsException, InterruptedException {
        IUser user = client.getUserByID(userId);
        return sendPrivateMessage(user, content);
    }

    @Async
    public CompletableFuture<IMessage> sendPrivateMessage(IUser user, String content) throws DiscordException, MissingPermissionsException, InterruptedException {
        IMessage response = null;
        while (response == null) {
            try {
                response = blockingSendMessage(client.getOrCreatePMChannel(user), content);
            } catch (Exception e) {
                log.warn("Could not create PM channel", e);
                throw new DiscordException("Could not create PM channel");
            }
        }
        return CompletableFuture.completedFuture(response);
    }

    public IMessage blockingSendMessage(IChannel channel, String message) throws MissingPermissionsException, DiscordException, InterruptedException {
        IMessage response = null;
        try {
            if (message.length() > LENGTH_LIMIT) {
                SplitMessage splitMessage = new SplitMessage(message);
                List<String> splits = splitMessage.split(LENGTH_LIMIT);
                for (String split : splits) {
                    response = channel.sendMessage(split);
                }
            } else {
                response = channel.sendMessage(message);
            }
        } catch (HTTP429Exception e) {
            sleep(e.getRetryDelay());
        }
        return response;
    }

    @Async
    public CompletableFuture<IMessage> sendFile(IChannel channel, File file) throws IOException, MissingPermissionsException, DiscordException, InterruptedException {
        IMessage response = null;
        while (response == null) {
            try {
                response = channel.sendFile(file);
            } catch (HTTP429Exception e) {
                sleep(e.getRetryDelay());
            }
        }
        return CompletableFuture.completedFuture(response);
    }

    @Async
    public CompletableFuture<IMessage> sendFilePrivately(IUser user, File file) throws DiscordException, IOException, MissingPermissionsException, InterruptedException {
        IMessage response = null;
        while (response == null) {
            try {
                response = client.getOrCreatePMChannel(user).sendFile(file);
            } catch (HTTP429Exception e) {
                sleep(e.getRetryDelay());
            } catch (Exception e) {
                log.warn("Could not create PM channel", e);
                throw new DiscordException("Could not create PM channel");
            }
        }
        return CompletableFuture.completedFuture(response);
    }

    @Async
    public CompletableFuture<IMessage> editMessage(IMessage message, String content) throws DiscordException, MissingPermissionsException, InterruptedException {
        IMessage response = null;
        while (response == null) {
            try {
                response = message.edit(content);
            } catch (HTTP429Exception e) {
                sleep(e.getRetryDelay());
            }
        }
        return CompletableFuture.completedFuture(response);
    }

    @Async
    public void deleteMessage(IMessage message) throws DiscordException, MissingPermissionsException, InterruptedException {
        while (true) {
            try {
                message.delete();
                return;
            } catch (HTTP429Exception e) {
                sleep(e.getRetryDelay());
            }
        }
    }

    @Async
    public void changeUsername(String name) throws DiscordException, InterruptedException {
        while (true) {
            try {
                client.changeUsername(name);
                return;
            } catch (HTTP429Exception e) {
                sleep(e.getRetryDelay());
            }
        }
    }

    @Async
    public void changeAvatar(Image avatar) throws DiscordException, InterruptedException {
        while (true) {
            try {
                client.changeAvatar(avatar);
                return;
            } catch (HTTP429Exception e) {
                sleep(e.getRetryDelay());
            }
        }
    }

    @Async
    public CompletableFuture<List<DiscordStatus.Maintenance>> getActiveMaintenances() throws DiscordException, InterruptedException {
        DiscordStatus.Maintenance[] response = null;
        while (response == null) {
            try {
                response = DiscordStatus.getActiveMaintenances();
            } catch (HTTP429Exception e) {
                sleep(e.getRetryDelay());
            }
        }
        return CompletableFuture.completedFuture(asList(response));
    }

    @Async
    public CompletableFuture<List<DiscordStatus.Maintenance>> getUpcomingMaintenances() throws DiscordException, InterruptedException {
        DiscordStatus.Maintenance[] response = null;
        while (response == null) {
            try {
                response = DiscordStatus.getUpcomingMaintenances();
            } catch (HTTP429Exception e) {
                sleep(e.getRetryDelay());
            }
        }
        return CompletableFuture.completedFuture(asList(response));
    }

    public Long getUserCount() {
        if (client == null || !client.isReady()) {
            return 0L;
        }
        return client.getGuilds().stream().flatMap(g -> g.getUsers().stream()).count();
    }

    public Long getConnectedUserCount() {
        if (client == null || !client.isReady()) {
            return 0L;
        }
        return client.getGuilds().stream().flatMap(g -> g.getUsers().stream())
            .filter(u -> !u.getPresence().equals(Presences.OFFLINE)).count();
    }

    public Long getOnlineUserCount() {
        if (client == null || !client.isReady()) {
            return 0L;
        }
        return client.getGuilds().stream().flatMap(g -> g.getUsers().stream())
            .filter(u -> u.getPresence().equals(Presences.ONLINE)).count();
    }

    public long getMeanResponseTime() {
        return (long) responseTime.getSnapshot().getMean();
    }

    @Scheduled(cron = "*/10 * * * * *")
    void checkResponseTime() { // scheduled each 10 seconds
        if (client != null && client.isReady()) {
            long ms = client.getResponseTime();
            responseTime.update(ms);
        }
    }

    public static String guildString(IGuild guild, IUser user) {
        String id = guild.getID();
        String name = guild.getName();
        IUser owner = guild.getOwner();
        List<IRole> roles = guild.getRoles();
        List<IChannel> channels = guild.getChannels();
        return "Guild ["
            + "id='" + id
            + "', name='" + name
            + "', owner='" + userString(owner)
            + "', roles=[" + roles.stream().map(DiscordService::roleString).collect(Collectors.joining(", "))
            + "], channels=[" + channels.stream().map(c -> channelString(c, user)).collect(Collectors.joining(", "))
            + "]]";
    }

    public static String roleString(IRole role) {
        String id = role.getID();
        String name = role.getName();
        int position = role.getPosition();
        Color color = role.getColor();
        String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        String permissions = role.getPermissions().toString();
        return "Role ["
            + "id='" + id
            + "', name='" + name
            + "', position=" + position
            + ", color=" + hex
            + ", permissions=" + permissions
            + "']";
    }

    public static String channelString(IChannel channel, IUser user) {
        String id = channel.getID();
        String name = channel.getName();
        String topic = channel.getTopic();
        String roleOverrides = channel.getRoleOverrides().entrySet().stream().map(DiscordService::permOverrideEntryToString).collect(Collectors.joining(", "));
        String userOverrides = channel.getUserOverrides().entrySet().stream().map(DiscordService::permOverrideEntryToString).collect(Collectors.joining(", "));
        String userModifiedPermissions = channel.getModifiedPermissions(user).toString();
        return "Channel ["
            + "id='" + id + '\''
            + ", name='" + name + '\''
            + ", topic='" + topic + '\''
            + ", roleOverrides=" + roleOverrides
            + ", userOverrides=" + userOverrides
            + ", userModifiedPermissions=" + userModifiedPermissions
            //+ ", roleModifiedPermissionsPerRole=" + channel.getGuild().getRoles().stream()
            //.map(r -> "{" + r.getName() + " -> " + channel.getModifiedPermissions(r).toString() + "}").collect(Collectors.joining(", "))
            + "']";
    }

    private static String permOverrideEntryToString(Map.Entry<String, IChannel.PermissionOverride> e) {
        return "{" + e.getKey() + " -> allow=" + e.getValue().allow() + ", deny=" + e.getValue().deny() + "}";
    }

    public static String userString(IUser user) {
        String id = user.getID();
        String name = user.getName();
        Presences status = user.getPresence();
        return "User ["
            + "id='" + id
            + "', name='" + name
            + "', status='" + status
            + "']";
    }
}
