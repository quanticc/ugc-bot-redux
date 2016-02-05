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
import sx.blah.discord.handle.EventSubscriber;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.DiscordDisconnectedEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.obj.Invite;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.HTTP429Exception;

import javax.annotation.PostConstruct;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Service
@Transactional
public class DiscordService implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(DiscordService.class);

    private final LeagueProperties leagueProperties;
    private final Queue<IListener<?>> queuedListeners = new ConcurrentLinkedQueue<>();
    private final Queue<DiscordSubscriber> queuedSubscribers = new ConcurrentLinkedQueue<>();
    private volatile IDiscordClient client;

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
                    log.warn("Could not connect discord bot", e);
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
        client.getDispatcher().registerListener(this);
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        log.info("*** Discord bot armed ***");
        for (String guildId : leagueProperties.getDiscord().getQuitting()) {
            IGuild guild = client.getGuildByID(guildId);
            if (guild != null) {
                try {
                    leaveGuild(guild);
                } catch (HTTP429Exception e) {
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

    @EventSubscriber
    public void onDisconnect(DiscordDisconnectedEvent event) {
        log.info("Reconnecting bot");
        try {
            login();
        } catch (DiscordException e) {
            log.warn("Failed to reconnect bot", e);
        }
    }

    public IDiscordClient getClient() {
        return client;
    }

    public IUser getMasterUser() {
        return client.getUserByID(leagueProperties.getDiscord().getMasters().get(0));
    }

    public boolean isMaster(String id) {
        return leagueProperties.getDiscord().getMasters().contains(id);
    }

    public boolean isMaster(IUser user) {
        return isMaster(user.getID());
    }

    public boolean isOwnUser(IUser user) {
        return user.getID().equals(client.getOurUser().getID());
    }

    public boolean hasSupportRole(IUser user) {
        Set<IRole> roleSet = new HashSet<>();
        LeagueProperties.Discord.Support support = leagueProperties.getDiscord().getSupport();
        for (String gid : support.getGuilds()) {
            roleSet.addAll(user.getRolesForGuild(gid));
        }
        return roleSet.stream().anyMatch(r -> support.getRoles().contains(r.getID()));
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

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L, maxDelay = 10000L))
    private void leaveGuild(IGuild guild) throws HTTP429Exception, DiscordException {
        guild.deleteOrLeaveGuild();
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L, maxDelay = 10000L))
    public IMessage sendMessage(String channelId, String content) throws HTTP429Exception, DiscordException, MissingPermissionsException {
        IChannel channel = client.getChannelByID(channelId);
        return channel.sendMessage(content);
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L, maxDelay = 10000L))
    public IMessage sendMessage(IChannel channel, String content) throws HTTP429Exception, DiscordException, MissingPermissionsException {
        return channel.sendMessage(content);
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L, maxDelay = 10000L))
    public IMessage sendPrivateMessage(String userId, String content) throws HTTP429Exception, DiscordException, MissingPermissionsException {
        IUser user = client.getUserByID(userId);
        return client.getOrCreatePMChannel(user).sendMessage(content);
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L, maxDelay = 10000L))
    public IMessage sendPrivateMessage(IUser user, String content) throws HTTP429Exception, DiscordException, MissingPermissionsException {
        return client.getOrCreatePMChannel(user).sendMessage(content);
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L, maxDelay = 10000L))
    public IMessage sendFile(IChannel channel, File file) throws HTTP429Exception, IOException, MissingPermissionsException, DiscordException {
        return channel.sendFile(file);
    }

    public IMessage sendFilePrivately(String userId, File file) throws DiscordException, HTTP429Exception, IOException, MissingPermissionsException {
        IUser user = client.getUserByID(userId);
        return sendFilePrivately(user, file);
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L, maxDelay = 10000L))
    public IMessage sendFilePrivately(IUser user, File file) throws HTTP429Exception, DiscordException, IOException, MissingPermissionsException {
        return client.getOrCreatePMChannel(user).sendFile(file);
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L, maxDelay = 10000L))
    public IMessage editMessage(IMessage message, String content) throws HTTP429Exception, DiscordException, MissingPermissionsException {
        return message.edit(content);
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L, maxDelay = 10000L))
    public void deleteMessage(IMessage message) throws HTTP429Exception, DiscordException, MissingPermissionsException {
        message.delete();
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
}
