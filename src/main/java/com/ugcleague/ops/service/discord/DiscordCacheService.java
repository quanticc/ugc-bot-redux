package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.DiscordAttachment;
import com.ugcleague.ops.domain.DiscordChannel;
import com.ugcleague.ops.domain.DiscordMessage;
import com.ugcleague.ops.domain.DiscordUser;
import com.ugcleague.ops.repository.DiscordAttachmentRepository;
import com.ugcleague.ops.repository.DiscordChannelRepository;
import com.ugcleague.ops.repository.DiscordMessageRepository;
import com.ugcleague.ops.repository.DiscordUserRepository;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.EventSubscriber;
import sx.blah.discord.handle.impl.events.*;
import sx.blah.discord.handle.obj.*;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class DiscordCacheService implements DiscordSubscriber, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DiscordCacheService.class);

    private final DiscordService discordService;
    private final DiscordUserRepository userRepository;
    private final DiscordMessageRepository messageRepository;
    private final DiscordAttachmentRepository attachmentRepository;
    private final DiscordChannelRepository channelRepository;

    @Autowired
    public DiscordCacheService(DiscordService discordService, DiscordUserRepository userRepository,
                               DiscordMessageRepository messageRepository, DiscordAttachmentRepository attachmentRepository,
                               DiscordChannelRepository channelRepository) {
        this.discordService = discordService;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.channelRepository = channelRepository;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        IDiscordClient client = event.getClient();
        IUser ourUser = client.getOurUser();
        DiscordUser me = userRepository.findByDiscordUserId(ourUser.getID()).orElseGet(() -> newDiscordUser(ourUser));
        me.setConnected(ZonedDateTime.now());
        userRepository.saveAndFlush(me);
        userRepository.findCurrentlyConnected().parallelStream()
            .map(u -> validateConnected(client, u))
            .filter(u -> u != null)
            .forEach(userRepository::save);
        log.info("Now recording certain events. Channels: {}, Users: {}, Messages: {}, Attachments: {}",
            channelRepository.count(), userRepository.count(), messageRepository.count(), attachmentRepository.count());
    }

    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent event) {
        // for now we will only log command messages
        // TODO remove attachment repository
        // TODO track join/leave state in multiple guilds
        IMessage message = event.getMessage();
        String content = message.getContent();
        if (content.startsWith(".")) {
            DiscordMessage msg = newMessage(message);
            channelRepository.saveAndFlush(msg.getChannel());
            userRepository.saveAndFlush(msg.getAuthor());
            messageRepository.saveAndFlush(msg);
            attachmentRepository.save(msg.getAttachments());
        }
    }

    @EventSubscriber
    public void onMessageUpdated(MessageUpdateEvent event) {
        IMessage oldMessage = event.getOldMessage();
        IMessage newMessage = event.getNewMessage();
        if (oldMessage.getContent().startsWith(".")) {
            Optional<DiscordMessage> o = messageRepository.findByDiscordMessageId(oldMessage.getID());
            if (o.isPresent()) {
                DiscordMessage current;
                if (o.isPresent()) {
                    DiscordMessage previous = o.get();
                    Set<DiscordAttachment> previousAttachments = new LinkedHashSet<>(previous.getAttachments());
                    current = updateMessage(previous, newMessage);
                    Set<DiscordAttachment> currentAttachments = new LinkedHashSet<>(current.getAttachments());
                    previousAttachments.removeIf(currentAttachments::contains);
                    attachmentRepository.delete(previousAttachments);
                } else {
                    current = newMessage(newMessage);
                }
                channelRepository.saveAndFlush(current.getChannel());
                userRepository.saveAndFlush(current.getAuthor());
                messageRepository.saveAndFlush(current);
                attachmentRepository.save(current.getAttachments());
            }
        }
    }

    @EventSubscriber
    public void onMessageDeleted(MessageDeleteEvent event) {
        IMessage message = event.getMessage();
        if (message.getContent().startsWith(".")) {
            messageRepository.findByDiscordMessageId(message.getID()).ifPresent(msg -> {
                msg.setDeleted(true);
                messageRepository.save(msg);
            });
        }
    }

    @EventSubscriber
    public void onUserJoin(UserJoinEvent event) {
        LocalDateTime timestamp = event.getJoinTime();
        IUser user = event.getUser();
        DiscordUser u = userRepository.findByDiscordUserId(user.getID()).orElseGet(() -> newDiscordUser(user));
        if (u.getJoined() == null) {
            u.setJoined(timestamp.atZone(ZoneId.systemDefault()));
        }
        userRepository.save(u);
    }

    @EventSubscriber
    public void onUserUpdate(UserUpdateEvent event) {
        IUser oldUser = event.getOldUser();
        IUser newUser = event.getNewUser();
        DiscordUser u = userRepository.findByDiscordUserId(oldUser.getID()).orElseGet(() -> newDiscordUser(oldUser));
        u.setName(newUser.getName());
        userRepository.save(u);
    }

//    @EventSubscriber
//    public void onUserLeave(UserLeaveEvent event) {
//        IGuild guild = event.getGuild();
//        IUser user = event.getUser();
//    }

    @EventSubscriber
    public void onPresenceUpdate(PresenceUpdateEvent event) {
        IUser user = event.getUser();
        Presences oldStatus = event.getOldPresence();
        Presences newStatus = event.getNewPresence();
        if (oldStatus == Presences.OFFLINE && newStatus == Presences.ONLINE) {
            DiscordUser u = userRepository.findByDiscordUserId(user.getID()).orElseGet(() -> newDiscordUser(user));
            u.setConnected(ZonedDateTime.now());
            userRepository.save(u);
        } else if (oldStatus == Presences.ONLINE && newStatus == Presences.OFFLINE) {
            DiscordUser u = userRepository.findByDiscordUserId(user.getID()).orElseGet(() -> newDiscordUser(user));
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

        DiscordUser discordUser = userRepository.findByDiscordUserId(author.getID())
            .orElseGet(() -> newDiscordUser(author));
        DiscordChannel discordChannel = channelRepository.findByDiscordChannelId(channel.getID())
            .orElseGet(() -> newDiscordChannel(channel));
        Set<DiscordAttachment> discordAttachments = message.getAttachments().stream()
            .map(a -> newDiscordAttachment(msg, a)).collect(Collectors.toSet());

        msg.setDiscordMessageId(message.getID());
        msg.setAuthor(discordUser);
        msg.setChannel(discordChannel);
        msg.setContent(message.getContent());
        msg.setAttachments(discordAttachments);
        msg.setTimestamp(message.getTimestamp().atZone(ZoneId.systemDefault()));
        return msg;
    }

    private DiscordAttachment newDiscordAttachment(DiscordMessage owner, IMessage.Attachment attachment) {
        DiscordAttachment a = new DiscordAttachment();
        a.setDiscordAttachmentId(attachment.getId());
        a.setFilename(attachment.getFilename());
        a.setFilesize(attachment.getFilesize());
        a.setUrl(attachment.getUrl());
        a.setOwner(owner);
        return a;
    }

    private DiscordChannel newDiscordChannel(IChannel channel) {
        DiscordChannel c = new DiscordChannel();
        c.setDiscordChannelId(channel.getID());
        c.setIsPrivate(channel.isPrivate());
        c.setName(channel.getName());
        if (!channel.isPrivate()) {
            c.setParentGuildId(channel.getGuild().getID());
        }
        return c;
    }

    private DiscordUser validateConnected(IDiscordClient client, DiscordUser u) {
        IUser user = client.getUserByID(u.getDiscordUserId()); // @Nullable
        if (user == null || user.getPresence().equals(Presences.OFFLINE)) {
            return checkout(u);
        }
        return null; // therefore it will be removed from the stream
    }

    private DiscordUser newDiscordUser(IUser user) {
        DiscordUser u = new DiscordUser();
        u.setDiscordUserId(user.getID());
        u.setName(user.getName());
        return u;
    }

    @Override
    public void destroy() throws Exception {
        log.info("Disposing of cache service");
        IUser ourUser = discordService.getClient().getOurUser();
        DiscordUser me = userRepository.findByDiscordUserId(ourUser.getID()).orElseGet(() -> newDiscordUser(ourUser));
        checkout(me);
        userRepository.save(me);
    }

    private DiscordUser checkout(DiscordUser u) {
        u.setDisconnected(ZonedDateTime.now());
        u.setTotalUptime(u.getTotalUptime() + Duration.between(u.getConnected(), u.getDisconnected()).toMillis());
        return u;
    }
}
