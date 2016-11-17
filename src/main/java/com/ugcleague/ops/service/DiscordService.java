package com.ugcleague.ops.service;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.document.Incident;
import com.ugcleague.ops.event.IncidentCreatedEvent;
import com.ugcleague.ops.service.discord.command.SplitMessage;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.events.IListener;
import sx.blah.discord.handle.impl.events.*;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.*;
import sx.blah.discord.util.Image;

import javax.annotation.PostConstruct;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional
public class DiscordService implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(DiscordService.class);
    private static final int LENGTH_LIMIT = 2000;

    private final LeagueProperties properties;
    private final ApplicationEventPublisher publisher;
    private final Executor taskExecutor;
    private final Queue<IListener<?>> queuedListeners = new ConcurrentLinkedQueue<>();
    private final Queue<DiscordSubscriber> queuedSubscribers = new ConcurrentLinkedQueue<>();
    private volatile IDiscordClient client;

    @Autowired
    public DiscordService(LeagueProperties properties, ApplicationEventPublisher publisher, Executor taskExecutor) {
        this.properties = properties;
        this.publisher = publisher;
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    private void configure() {
        if (properties.getDiscord().isAutologin()) {
            RequestBuffer.request(() -> {
                try {
                    login();
                } catch (DiscordException | InterruptedException e) {
                    log.error("Could not connect discord bot", e);
                }
            });
        }
    }

    private ClientBuilder newClientBuilder() {
        LeagueProperties.Discord discord = properties.getDiscord();
        ClientBuilder builder = new ClientBuilder()
            .withTimeout(discord.getTimeoutDelay())
            .withPingTimeout(discord.getMaxMissedPings())
            .setMaxReconnectAttempts(discord.getMaxReconnects());
        if (discord.getToken() != null) {
            return builder.withToken(discord.getToken());
        } else {
            throw new IllegalArgumentException("Must configure a bot token");
        }
    }

    public void login() throws DiscordException, InterruptedException, RateLimitException {
        log.debug("Logging in to Discord");
        if (client != null) {
            if (client.getShards().isEmpty()) {
                client.login();
            }
        } else {
            client = newClientBuilder().login();
            log.debug("Registering Discord event listeners");
            queuedListeners.forEach(listener -> {
                log.debug("Registering {}", listener.getClass().getCanonicalName());
                client.getDispatcher().registerListener(listener);
            });
            queuedSubscribers.forEach(subscriber -> {
                log.debug("Registering {}", subscriber.getClass().getCanonicalName());
                client.getDispatcher().registerListener(subscriber);
            });
            client.getDispatcher().registerListener(this);
        }
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        log.info("*** Discord bot armed ***");
        List<IGuild> guildList = client.getGuilds();
        for (IGuild guild : guildList) {
            log.info("{}", guildString(guild, client.getOurUser()));
        }
    }

    @EventSubscriber
    public void onGuildCreate(GuildCreateEvent event) {
        log.info("{}", guildString(event.getGuild(), client.getOurUser()));
    }

    @EventSubscriber
    public void onReconnectSuccess(ReconnectSuccessEvent event) {
        log.info("*** Discord bot reconnect succeeded ***");
        publisher.publishEvent(new IncidentCreatedEvent(newRestartIncident("Reconnected bot to Discord")));
    }

    @EventSubscriber
    public void onReconnectFailure(ReconnectFailureEvent event) {
        log.warn("*** Discord bot reconnect failed after {} attempt{} ***", event.getCurAttempt(), event.getCurAttempt() == 1 ? "" : "s");
    }

    @EventSubscriber
    public void onDisconnect(DisconnectedEvent event) {
        log.warn("*** Discord bot disconnected due to {} ***", event.getReason());
        String reason = StringUtils.capitalise(event.getReason().toString());
        publisher.publishEvent(new IncidentCreatedEvent(newRestartIncident("Disconnected due to " + reason)));
    }

    private Incident newRestartIncident(String reason) {
        Incident incident = new Incident();
        incident.setGroup(IncidentService.DISCORD_RESTART);
        incident.setName(reason);
        incident.setCreatedAt(ZonedDateTime.now());
        return incident;
    }

    public void logout() {
        try {
            client.logout();
        } catch (DiscordException e) {
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
            log.debug("Subscribing {}", listener.getClass().getCanonicalName());
            client.getDispatcher().registerListener(listener);
        }
        queuedListeners.add(listener);
    }

    /**
     * Register this subscriber as a Discord4J annotation-based event listener
     *
     * @param subscriber the discord subscriber
     */
    public void subscribe(DiscordSubscriber subscriber) {
        if (client != null && client.isReady()) {
            log.debug("Subscribing {}", subscriber.getClass().getCanonicalName());
            client.getDispatcher().registerListener(subscriber);
        }
        queuedSubscribers.add(subscriber);
    }

    public void unsubscribe(IListener<?> listener) {
        client.getDispatcher().unregisterListener(listener);
        queuedListeners.remove(listener);
    }

    public void unsubscribe(DiscordSubscriber subscriber) {
        client.getDispatcher().unregisterListener(subscriber);
        queuedSubscribers.remove(subscriber);
    }

    private void sleep(long millis, String bucket) throws InterruptedException {
        log.info("Backing off for {} ms due to rate limits on {}", millis, bucket);
        Thread.sleep(Math.max(1, millis));
    }

    @Async
    public void leaveGuild(IGuild guild) throws DiscordException, InterruptedException {
        while (true) {
            try {
                guild.leaveGuild();
                return;
            } catch (RateLimitException e) {
                sleep(e.getRetryDelay(), e.getBucket());
            }
        }
    }

    @Async
    public CompletableFuture<IMessage> sendMessage(String channelId, String content) throws DiscordException, MissingPermissionsException, InterruptedException {
        IChannel channel = client.getChannelByID(channelId);
        IMessage response = null;
        while (response == null) {
            response = blockingSendMessage(channel, content, false);
        }
        return CompletableFuture.completedFuture(response);
    }

    public CompletableFuture<IMessage> sendMessage(IChannel channel, String content) throws DiscordException, MissingPermissionsException, InterruptedException {
        return sendMessage(channel, content, false);
    }

    @Async
    public CompletableFuture<IMessage> sendMessage(IChannel channel, String content, boolean tts) throws DiscordException, MissingPermissionsException, InterruptedException {
        IMessage response = null;
        while (response == null) {
            response = blockingSendMessage(channel, content, tts);
        }
        return CompletableFuture.completedFuture(response);
    }

    public CompletableFuture<IMessage> sendPrivateMessage(String userId, String content) throws DiscordException, MissingPermissionsException, InterruptedException {
        IUser user = client.getUserByID(userId);
        return sendPrivateMessage(user, content, false);
    }

    public CompletableFuture<IMessage> sendPrivateMessage(IUser user, String content) throws DiscordException, MissingPermissionsException, InterruptedException {
        return sendPrivateMessage(user, content, false);
    }

    @Async
    public CompletableFuture<IMessage> sendPrivateMessage(IUser user, String content, boolean tts) throws DiscordException, MissingPermissionsException, InterruptedException {
        IMessage response = null;
        while (response == null) {
            try {
                response = blockingSendMessage(client.getOrCreatePMChannel(user), content, tts);
            } catch (Exception e) {
                log.warn("Could not create PM channel", e);
                throw new DiscordException("Could not create PM channel");
            }
        }
        return CompletableFuture.completedFuture(response);
    }

    public IMessage blockingSendMessage(IChannel channel, String message, boolean tts) throws MissingPermissionsException, DiscordException, InterruptedException {
        IMessage response = null;
        try {
            if (message.length() > LENGTH_LIMIT) {
                SplitMessage splitMessage = new SplitMessage(message);
                List<String> splits = splitMessage.split(LENGTH_LIMIT);
                for (String split : splits) {
                    response = channel.sendMessage(split);
                }
            } else {
                response = channel.sendMessage(message, tts);
            }
        } catch (RateLimitException e) {
            sleep(e.getRetryDelay(), e.getBucket());
        }
        return response;
    }

    @Async
    public CompletableFuture<IMessage> sendFile(IChannel channel, File file) throws IOException, MissingPermissionsException, DiscordException, InterruptedException {
        IMessage response = null;
        while (response == null) {
            try {
                response = channel.sendFile(file);
            } catch (RateLimitException e) {
                sleep(e.getRetryDelay(), e.getBucket());
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
            } catch (RateLimitException e) {
                sleep(e.getRetryDelay(), e.getBucket());
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
            } catch (RateLimitException e) {
                sleep(e.getRetryDelay(), e.getBucket());
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
            } catch (RateLimitException e) {
                sleep(e.getRetryDelay(), e.getBucket());
            }
        }
    }

    public void deleteMessage(IMessage message, int timeout, TimeUnit unit) {
        CompletableFuture.runAsync(() -> {
            try {
                unit.sleep(timeout);
            } catch (InterruptedException ex) {
                log.warn("Could not perform cleanup: {}", ex.toString());
            }
        }).thenRun(() -> RequestBuffer.request(() -> {
            try {
                message.delete();
            } catch (MissingPermissionsException | DiscordException e) {
                log.warn("Failed to delete message", e);
            }
            return null;
        }));
    }


    @Async
    public void changeUsername(String name) throws DiscordException, InterruptedException {
        while (true) {
            try {
                client.changeUsername(name);
                return;
            } catch (RateLimitException e) {
                sleep(e.getRetryDelay(), e.getBucket());
            }
        }
    }

    @Async
    public void changeAvatar(Image avatar) throws DiscordException, InterruptedException {
        while (true) {
            try {
                client.changeAvatar(avatar);
                return;
            } catch (RateLimitException e) {
                sleep(e.getRetryDelay(), e.getBucket());
            }
        }
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

    public static String guildString(IGuild guild, IUser user) {
        String id = guild.getID();
        String name = guild.getName();
        IUser owner = guild.getOwner();
        List<IRole> roles = guild.getRoles();
        List<IChannel> channels = guild.getChannels();
        return "Guild **" + name + "** <" + id + "> owned by " + owner.getName() +
            " <" + owner.getID() + ">\n**Roles**\n" +
            roles.stream().map(DiscordService::roleString).collect(Collectors.joining("\n")) +
            "\n**Channels**\n" +
            channels.stream().map(c -> channelString(c, user)).collect(Collectors.joining("\n")) +
            "\n";
    }

    public static String roleString(IRole role) {
        String id = role.getID();
        String name = role.getName();
        int position = role.getPosition();
        Color color = role.getColor();
        String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        String permissions = role.getPermissions().toString();
        return "(#" + position + ") " + name + " <" + id + "> (" + hex + ") " + permissions;
    }

    public static String channelString(IChannel channel, IUser user) {
        String id = channel.getID();
        String name = channel.getName();
        String userModifiedPermissions = channel.getModifiedPermissions(user).toString();
        return "#" + name + " <" + id + "> where bot has permissions to: " + userModifiedPermissions;
    }

    public static String userString(IUser user) {
        String id = user.getID();
        String name = user.getName();
        return name + " <" + id + ">";
    }
}
