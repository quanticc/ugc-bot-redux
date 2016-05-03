package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.document.DiscordChannel;
import com.ugcleague.ops.domain.document.DiscordGuild;
import com.ugcleague.ops.domain.document.DiscordMessage;
import com.ugcleague.ops.domain.document.DiscordUser;
import com.ugcleague.ops.repository.mongo.DiscordChannelRepository;
import com.ugcleague.ops.repository.mongo.DiscordGuildRepository;
import com.ugcleague.ops.repository.mongo.DiscordMessageRepository;
import com.ugcleague.ops.repository.mongo.DiscordUserRepository;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import com.ugcleague.ops.service.discord.util.DiscordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.*;
import sx.blah.discord.handle.obj.*;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DiscordCacheService implements DiscordSubscriber, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DiscordCacheService.class);

    private final DiscordService discordService;
    private final DiscordUserRepository userRepository;
    private final DiscordMessageRepository messageRepository;
    private final DiscordChannelRepository channelRepository;
    private final DiscordGuildRepository guildRepository;

    @Autowired
    public DiscordCacheService(DiscordService discordService, DiscordUserRepository userRepository,
                               DiscordMessageRepository messageRepository, DiscordChannelRepository channelRepository,
                               DiscordGuildRepository guildRepository) {
        this.discordService = discordService;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.channelRepository = channelRepository;
        this.guildRepository = guildRepository;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        IDiscordClient client = event.getClient();
        IUser ourUser = client.getOurUser();
        DiscordUser me = userRepository.findById(ourUser.getID()).orElseGet(() -> new DiscordUser(ourUser));
        me.setLastConnect(ZonedDateTime.now());
        userRepository.save(me);
        userRepository.findCurrentlyConnected().parallelStream()
            .map(u -> validateConnected(client, u))
            .filter(u -> u != null)
            .forEach(userRepository::save);
        log.info("Servers: {}, Channels: {}, Users: {}, Messages: {}",
            guildRepository.count(), channelRepository.count(), userRepository.count(), messageRepository.count());
    }

    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent event) {
        // for now we will only log command messages
        IMessage message = event.getMessage();
        String content = message.getContent();
        if (content.startsWith(".")) {
            saveAll(newMessage(message));
        }
    }

    @EventSubscriber
    public void onMessageUpdated(MessageUpdateEvent event) {
        IMessage oldMessage = event.getOldMessage();
        IMessage newMessage = event.getNewMessage();
        if (oldMessage == null || oldMessage.getContent() == null) {
            log.warn("Old message was deleted or removed from cache: {}",
                DiscordUtil.toString(event.getNewMessage()));
        } else if (oldMessage.getContent().startsWith(".")) {
            Optional<DiscordMessage> o = messageRepository.findById(oldMessage.getID());
            if (o.isPresent()) {
                saveAll(o.isPresent() ? updateMessage(o.get(), newMessage) : newMessage(newMessage));
            }
        }
    }

    private void saveAll(DiscordMessage message) {
        channelRepository.save(message.getChannel());
        if (!message.getChannel().isPrivate()) {
            DiscordGuild guild = message.getChannel().getGuild();
            guild.getChannels().add(message.getChannel());
            guildRepository.save(guild);
        }
        userRepository.save(message.getAuthor());
        messageRepository.save(message);
        channelRepository.save(message.getChannel());
        userRepository.save(message.getAuthor());
    }

    @EventSubscriber
    public void onMessageDeleted(MessageDeleteEvent event) {
        IMessage message = event.getMessage();
        if (message != null && message.getContent() != null && message.getContent().startsWith(".")) {
            messageRepository.findById(message.getID()).ifPresent(msg -> {
                msg.setDeleted(true);
                messageRepository.save(msg);
            });
        }
    }

    @EventSubscriber
    public void onUserJoin(UserJoinEvent event) {
        IUser user = event.getUser();
        DiscordUser u = userRepository.findById(user.getID()).orElseGet(() -> new DiscordUser(user));
        userRepository.save(u);
    }

    @EventSubscriber
    public void onUserUpdate(UserUpdateEvent event) {
        IUser oldUser = event.getOldUser();
        IUser newUser = event.getNewUser();
        DiscordUser u = userRepository.findById(oldUser.getID()).orElseGet(() -> new DiscordUser(oldUser));
        u.setName(newUser.getName());
        userRepository.save(u);
    }

    @EventSubscriber
    public void onUserLeave(UserLeaveEvent event) {
        IUser user = event.getUser();
        DiscordUser u = userRepository.findById(user.getID()).orElseGet(() -> new DiscordUser(user));
        userRepository.save(u);
    }

    @EventSubscriber
    public void onPresenceUpdate(PresenceUpdateEvent event) {
        IUser user = event.getUser();
        Presences oldStatus = event.getOldPresence();
        Presences newStatus = event.getNewPresence();
        if (oldStatus == Presences.OFFLINE && newStatus == Presences.ONLINE) {
            DiscordUser u = userRepository.findById(user.getID()).orElseGet(() -> new DiscordUser(user));
            u.setLastConnect(ZonedDateTime.now());
            userRepository.save(u);
        } else if (oldStatus == Presences.ONLINE && newStatus == Presences.OFFLINE) {
            DiscordUser u = userRepository.findById(user.getID()).orElseGet(() -> new DiscordUser(user));
            checkout(u);
            userRepository.save(u);
        }
    }

    private DiscordMessage newMessage(IMessage message) {
        return updateMessage(new DiscordMessage(), message);
    }

    private DiscordMessage updateMessage(DiscordMessage msg, IMessage message) {
        IUser author = message.getAuthor();
        IChannel channel = message.getChannel();

        DiscordUser discordUser = userRepository.findById(author.getID())
            .orElseGet(() -> new DiscordUser(author));
        DiscordChannel discordChannel = channelRepository.findById(channel.getID())
            .orElseGet(() -> newDiscordChannel(channel));

        if (!channel.isPrivate()) {
            IGuild parent = channel.getGuild();
            DiscordGuild guild = guildRepository.findById(channel.getGuild().getID()).orElseGet(() -> newDiscordGuild(parent));
            guildRepository.save(guild);
            discordChannel.setGuild(guild);
        }

        msg.setId(message.getID());
        msg.setAuthor(discordUser);
        msg.setChannel(discordChannel);
        msg.setContent(message.getContent());
        msg.setTimestamp(message.getTimestamp().atZone(ZoneId.systemDefault()));
        return msg;
    }

    private DiscordChannel newDiscordChannel(IChannel channel) {
        DiscordChannel c = new DiscordChannel();
        c.setId(channel.getID());
        c.setPrivate(channel.isPrivate());
        c.setName(channel.getName());
        return c;
    }

    private DiscordGuild newDiscordGuild(IGuild guild) {
        DiscordGuild g = new DiscordGuild();
        g.setId(guild.getID());
        g.setName(guild.getName());
        return g;
    }

    private DiscordUser validateConnected(IDiscordClient client, DiscordUser u) {
        IUser user = client.getUserByID(u.getId()); // can be null
        if (user == null || user.getPresence().equals(Presences.OFFLINE)) {
            return checkout(u);
        }
        return null;
    }

    @Override
    public void destroy() throws Exception {
        log.info("Disposing of cache service");
        IUser ourUser = discordService.getClient().getOurUser();
        DiscordUser me = userRepository.findById(ourUser.getID()).orElseGet(() -> new DiscordUser(ourUser));
        checkout(me);
        userRepository.save(me);
    }

    private DiscordUser checkout(DiscordUser u) {
        u.setLastDisconnect(ZonedDateTime.now());
        return u;
    }

    public List<DiscordGuild> findAllGuilds() {
        return guildRepository.findAll();
    }

    public List<DiscordChannel> findAllChannels() {
        return channelRepository.findAll();
    }

    public List<DiscordUser> findAllUsers() {
        return userRepository.findAll();
    }

    public Optional<DiscordUser> findUserById(String id) {
        return userRepository.findById(id);
    }

    public DiscordUser getOrCreateUser(IUser user) {
        DiscordUser u = userRepository.findById(user.getID()).orElseGet(() -> new DiscordUser(user));
        if (u.getName() == null) {
            u.setName(user.getName());
        }
        return userRepository.save(u);
    }

    public DiscordChannel getOrCreateChannel(IChannel channel) {
        DiscordChannel c = channelRepository.findById(channel.getID()).orElseGet(() -> new DiscordChannel(channel));
        if (c.getName() == null) {
            c.setName(c.getName());
            c.setPrivate(c.isPrivate());
            if (!channel.isPrivate()) {
                IGuild parent = channel.getGuild();
                DiscordGuild guild = guildRepository.findById(channel.getGuild().getID()).orElseGet(() -> newDiscordGuild(parent));
                guildRepository.save(guild);
                c.setGuild(guild);
            }
        }
        return channelRepository.save(c);
    }

    public DiscordUser saveUser(DiscordUser u) {
        return userRepository.save(u);
    }

    public Optional<DiscordGuild> findGuildById(String id) {
        return guildRepository.findById(id);
    }

    public DiscordGuild saveGuild(DiscordGuild g) {
        return guildRepository.save(g);
    }

    public Optional<DiscordChannel> findChannelById(String id) {
        return channelRepository.findById(id);
    }

    public DiscordChannel saveChannel(DiscordChannel ch) {
        return channelRepository.save(ch);
    }
}
