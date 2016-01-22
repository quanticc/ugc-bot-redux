package com.ugcleague.ops.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.Publisher;
import com.ugcleague.ops.domain.Subscriber;
import com.ugcleague.ops.event.*;
import com.ugcleague.ops.repository.PublisherRepository;
import com.ugcleague.ops.repository.SubscriberRepository;
import com.ugcleague.ops.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.MessageBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AnnouncerService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncerService.class);
    private static final String SUPPORT = "support";
    private static final String CHANNEL = "channel";

    private final DiscordService discordService;
    private final LeagueProperties properties;
    private final PublisherRepository publisherRepository;
    private final SubscriberRepository subscriberRepository;

    @Autowired
    public AnnouncerService(DiscordService discordService, LeagueProperties properties,
                            PublisherRepository publisherRepository, SubscriberRepository subscriberRepository) {
        this.discordService = discordService;
        this.properties = properties;
        this.publisherRepository = publisherRepository;
        this.subscriberRepository = subscriberRepository;
    }

    @EventListener
    private void onReady(DiscordReadyEvent event) {
        discordService.subscribe(new IListener<MessageReceivedEvent>() {

            @Override
            public void handle(MessageReceivedEvent event) {
                IMessage m = event.getMessage();
                if (m.getContent().startsWith("+announce ")) {
                    String announcer = m.getContent().split(" ", 2)[1];
                    subscribeAnnouncer(m.getAuthor(), announcer, m.getChannel());
                } else if (m.getContent().startsWith("-announce ")) {
                    String announcer = m.getContent().split(" ", 2)[1];
                    unsubscribeAnnouncer(m.getAuthor(), announcer, m.getChannel());
                }
            }
        });
    }

    private void subscribeAnnouncer(IUser author, String announcer, IChannel channel) {
        String key = announcer.trim();
        if ((discordService.isMaster(author) || discordService.hasSupportRole(author)) && !key.isEmpty() && !key.equals(SUPPORT)) {
            Publisher publisher = publisherRepository.findByName(key).orElseGet(() -> newPublisher(key));
            Subscriber subscriber;
            if (channel.isPrivate()) {
                subscriber = subscriberRepository.findByUserId(author.getID())
                    .orElseGet(() -> newUserSubscriber(author));
            } else {
                subscriber = subscriberRepository.findByUserIdAndName(channel.getID(), CHANNEL)
                    .orElseGet(() -> newChannelSubscriber(channel));
            }
            Set<Subscriber> subs = publisher.getSubscribers();
            if (subs.stream().anyMatch(s -> s.getUserId().equals(channel.getID()) && s.getName().equals(CHANNEL))) {
                try {
                    discordService.privateMessage(author)
                        .appendContent("That channel is already receiving announcements about ")
                        .appendContent(announcer, MessageBuilder.Styles.BOLD).send();
                } catch (Exception e) {
                    log.warn("Could not send PM to {}: {}", author, e.toString());
                }
            } else {
                subs.add(subscriber);
                subscriberRepository.save(subscriber);
                publisherRepository.save(publisher);
                try {
                    if (channel.isPrivate()) {
                        discordService.privateMessage(author.getID())
                            .appendContent(":satellite: I'll PM you about ")
                            .appendContent(announcer, MessageBuilder.Styles.BOLD).send();
                    } else {
                        discordService.channelMessage(channel.getID())
                            .appendContent(":satellite: I'll let this channel know about ")
                            .appendContent(announcer, MessageBuilder.Styles.BOLD).send();
                    }
                } catch (Exception e) {
                    log.warn("Could not send channel message to {}: {}", channel, e.toString());
                }
            }
        } else {
            log.debug("Attempted to subscribe announcer: {}", author);
        }
    }

    private void unsubscribeAnnouncer(IUser author, String announcer, IChannel channel) {
        String key = announcer.trim();
        if ((discordService.isMaster(author) || discordService.hasSupportRole(author)) && !key.isEmpty() && !key.equals(SUPPORT)) {
            subscriberRepository.findByUserIdAndName(channel.getID(), CHANNEL).ifPresent(sub -> {
                Publisher publisher = publisherRepository.findByName(key).orElseGet(() -> newPublisher(key));
                Set<Subscriber> subs = publisher.getSubscribers();
                if (subs.stream().anyMatch(s -> s.getUserId().equals(channel.getID()) && s.getName().equals(CHANNEL))) {
                    subs.remove(sub);
                    publisherRepository.save(publisher);
                    try {
                        discordService.channelMessage(channel.getID())
                            .appendContent(":speak_no_evil: I'll stop sending messages about ")
                            .appendContent(announcer, MessageBuilder.Styles.BOLD)
                            .appendContent(" to this channel").send();
                    } catch (Exception e) {
                        log.warn("Could not send channel message to {}: {}", channel, e.toString());
                    }
                } else {
                    try {
                        discordService.privateMessage(author)
                            .appendContent("That channel is not receiving announcements about ")
                            .appendContent(announcer, MessageBuilder.Styles.BOLD).send();
                    } catch (Exception e) {
                        log.warn("Could not send channel message to {}: {}", channel, e.toString());
                    }
                }
            });
        } else {
            log.debug("Attempted to unsubscribe announcer: {}", author);
        }
    }

    private Publisher newPublisher(String name) {
        Publisher publisher = new Publisher();
        publisher.setName(name);
        return publisher;
    }

    private Subscriber newChannelSubscriber(IChannel channel) {
        Subscriber subscriber = new Subscriber();
        subscriber.setUserId(channel.getID());
        subscriber.setName(CHANNEL);
        return subscriber;
    }

    private Subscriber newUserSubscriber(IUser user) {
        Subscriber subscriber = new Subscriber();
        subscriber.setUserId(user.getID());
        subscriber.setName("user:" + user.getName());
        return subscriber;
    }

    @EventListener
    private void onFeedUpdate(FeedUpdatedEvent event) {
        SyndFeed syndFeed = event.getSource();
        announce("updates", buildFeedString(syndFeed));
    }

    private String buildFeedString(SyndFeed syndFeed) {
        // title published on date
        String latestEntryTitle = "News item";
        Optional<SyndEntry> o = syndFeed.getEntries().stream().findFirst();
        if (o.isPresent()) {
            latestEntryTitle = o.get().getTitle();
        }
        return String.format("%s published **%s**", latestEntryTitle,
            DateUtil.formatRelative(Duration.between(Instant.now(), syndFeed.getPublishedDate().toInstant())));
    }

    @EventListener
    private void onUpdateStarted(GameUpdateStartedEvent event) {
        announce("updates", "Preparing to update UGC game servers");
    }

    @EventListener
    private void onUpdateCompleted(GameUpdateCompletedEvent event) {
        announce("updates", ":ok_hand: TF2 game servers updated to v" + event.getVersion());
    }

    @EventListener
    private void onUpdateDelayed(GameUpdateDelayedEvent event) {
        String list = event.getServers().stream()
                .map(s -> String.format("_%s_", s.getName()))
                .collect(Collectors.joining(", "));
        announce("issues", "Server updates are taking a bit too long. Pending: " + list);
    }

    private void announce(String publisherName, String message) {
        publisherRepository.findByName(publisherName).ifPresent(pub -> {
            Set<Subscriber> subs = pub.getSubscribers();
            subs.stream().forEach(sub -> {
                try {
                    if (sub.getName().equals(CHANNEL)) {
                        discordService.channelMessage(sub.getUserId())
                            .appendContent("<" + publisherName +"> ")
                            .appendContent(message).send();
                    } else {
                        discordService.privateMessage(sub.getUserId())
                            .appendContent("<" + publisherName +"> ")
                            .appendContent(message).send();
                    }
                } catch (Exception e) {
                    log.warn("Could not send message to '{}': {}", publisherName, e.toString());
                }
            });
        });
    }
}
