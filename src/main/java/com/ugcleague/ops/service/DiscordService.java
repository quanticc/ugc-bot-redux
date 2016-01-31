package com.ugcleague.ops.service;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.obj.Invite;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.HTTP429Exception;
import sx.blah.discord.util.MessageBuilder;

import javax.annotation.PostConstruct;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Service
@Transactional
public class DiscordService {

    private static final Logger log = LoggerFactory.getLogger(DiscordService.class);

    private final LeagueProperties leagueProperties;

    private IDiscordClient client;
    private Queue<IListener<?>> queuedListeners = new ConcurrentLinkedQueue<>();
    private Queue<DiscordSubscriber> queuedSubscribers = new ConcurrentLinkedQueue<>();

    @Autowired
    public DiscordService(LeagueProperties leagueProperties) {
        this.leagueProperties = leagueProperties;
    }

    @PostConstruct
    private void configure() {
        if (leagueProperties.getDiscord().isAutologin()) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(3000); // wait a bit before firing
                    login();
                } catch (InterruptedException | DiscordException e) {
                    log.warn("Could not autologin discord bot", e);
                }
            });
        }
    }

    public void login() throws DiscordException {
        LeagueProperties.Discord discord = leagueProperties.getDiscord();
        String email = discord.getEmail();
        String password = discord.getPassword();
        client = new ClientBuilder().withLogin(email, password).login();
        log.debug("Registering {} Discord event subscribers", queuedListeners.size() + queuedSubscribers.size());
        queuedListeners.forEach(listener -> client.getDispatcher().registerListener(listener));
        queuedSubscribers.forEach(subscriber -> client.getDispatcher().registerListener(subscriber));
        client.getDispatcher().registerListener(new IListener<ReadyEvent>() {
            @Override
            public void handle(ReadyEvent readyEvent) {
                log.info("*** Discord bot armed ***");
                for (String guildId : leagueProperties.getDiscord().getQuitting()) {
                    IGuild guild = client.getGuildByID(guildId);
                    if (guild != null) {
                        try {
                            tryLeave(guild);
                        } catch (HTTP429Exception e) {
                            log.warn("Could not leave guild after retrying: {}", e.toString());
                        }
                    }
                }
                List<IGuild> guildList = client.getGuilds();
                for (IGuild guild : guildList) {
                    log.info("{}", guildString(guild, client.getOurUser()));
                }
                for (String inviteCode : leagueProperties.getDiscord().getInvites()) {
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
        });
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L))
    private void tryLeave(IGuild guild) throws HTTP429Exception {
        guild.deleteOrLeaveGuild();
    }

    public boolean isMaster(String id) {
        return leagueProperties.getDiscord().getMasters().contains(id);
    }

    public boolean isMaster(IUser user) {
        return isMaster(user.getID());
    }

    public void send(String channelNameRegex, String message) {
        client.getGuilds().stream()
            .flatMap(g -> g.getChannels().stream())
            .filter(ch -> ch.getName().matches(channelNameRegex))
            .forEach(channel -> {
                try {
                    trySendMessage(channel, message);
                } catch (MissingPermissionsException e) {
                    log.warn("No permission to perform action: {}", e.toString());
                } catch (HTTP429Exception e) {
                    log.warn("Too many requests. Slow down!", e);
                }
            });
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L))
    private void trySendMessage(IChannel channel, String message) throws MissingPermissionsException, HTTP429Exception {
        channel.sendMessage(message);
    }

    public IDiscordClient getClient() {
        return client;
    }

    // utilities

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
        String permissions = channel.getModifiedPermissions(user).toString();
        return "Channel ["
            + "id='" + id
            + "', name='" + name
            + "', topic='" + topic
            + "', permissions=" + permissions
            + "']";
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

    public void subscribe(IListener<?> listener) {
        if (client != null && client.isReady()) {
            client.getDispatcher().registerListener(listener);
        } else {
            // queue if the client is not ready yet
            queuedListeners.add(listener);
        }
    }

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

    public MessageBuilder message() {
        return new MessageBuilder(client);
    }

    public MessageBuilder channelMessage(String channelId) throws DiscordException {
        IChannel channel = client.getChannelByID(channelId);
        if (channel != null) {
            return message().withChannel(channel);
        } else {
            throw new DiscordException("Channel '" + channelId + "' does not exists");
        }
    }

    public MessageBuilder channelMessage(IChannel channel) {
        Objects.requireNonNull(channel, "Channel must not be null");
        return message().withChannel(channel);
    }

    public MessageBuilder privateMessage(String userId) throws Exception {
        IUser user = client.getUserByID(userId);
        IPrivateChannel pch = client.getOrCreatePMChannel(user);
        return new MessageBuilder(client).withChannel(pch);
    }

    public MessageBuilder privateMessage(IUser user) throws Exception {
        IPrivateChannel pch = client.getOrCreatePMChannel(user);
        return new MessageBuilder(client).withChannel(pch);
    }

    @Retryable(include = {HTTP429Exception.class}, backoff = @Backoff(1000))
    public void sendFile(IChannel channel, File file) throws HTTP429Exception, IOException, MissingPermissionsException {
        channel.sendFile(file);
    }

    public void sendFilePrivately(String userId, File file) throws Exception {
        IUser user = client.getUserByID(userId);
        sendFilePrivately(user, file);
    }

    @Retryable(include = {HTTP429Exception.class}, backoff = @Backoff(1000))
    public void sendFilePrivately(IUser user, File file) throws Exception {
        IPrivateChannel pch = client.getOrCreatePMChannel(user);
        pch.sendFile(file);
    }

    public boolean hasSupportRole(IUser user) {
        Set<IRole> roleSet = new HashSet<>();
        LeagueProperties.Discord.Support support = leagueProperties.getDiscord().getSupport();
        for (String gid : support.getGuilds()) {
            roleSet.addAll(user.getRolesForGuild(gid));
        }
        return roleSet.stream().anyMatch(r -> support.getRoles().contains(r.getID()));
    }

    public IUser getMasterUser() {
        return client.getUserByID(leagueProperties.getDiscord().getMasters().get(0));
    }
}
