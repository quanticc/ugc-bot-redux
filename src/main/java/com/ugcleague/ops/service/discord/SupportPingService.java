package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.Publisher;
import com.ugcleague.ops.domain.Subscriber;
import com.ugcleague.ops.repository.PublisherRepository;
import com.ugcleague.ops.repository.SubscriberRepository;
import com.ugcleague.ops.service.DiscordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class SupportPingService {

    private static final Logger log = LoggerFactory.getLogger(SupportPingService.class);
    private static final String KEY = "support";

    private final LeagueProperties properties;
    private final DiscordService discordService;
    private final PublisherRepository publisherRepository;
    private final SubscriberRepository subscriberRepository;
    private final LeagueProperties.Discord.Support support;

    private Map<String, LocalDateTime> lastMessage = new ConcurrentHashMap<>();

    @Autowired
    public SupportPingService(LeagueProperties properties, DiscordService discordService,
                              PublisherRepository publisherRepository, SubscriberRepository subscriberRepository) {
        this.properties = properties;
        this.discordService = discordService;
        this.publisherRepository = publisherRepository;
        this.subscriberRepository = subscriberRepository;
        this.support = properties.getDiscord().getSupport();
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(new IListener<MessageReceivedEvent>() {

            @Override
            public void handle(MessageReceivedEvent event) {
                IMessage m = event.getMessage();
                if (m.getContent().equals(".sub")) {
                    subscribeUser(m.getAuthor());
                } else if (m.getContent().equals(".unsub")) {
                    unsubscribeUser(m.getAuthor());
                } else if (m.getContent().equals(".start")) {
                    if (isMasterUser(m.getAuthor())) {
                        String chId = m.getChannel().getID();
                        if (!support.getChannels().contains(chId)) {
                            support.getChannels().add(chId);
                        }
                    }
                } else if (m.getContent().equals(".stop")) {
                    if (isMasterUser(m.getAuthor())) {
                        String chId = m.getChannel().getID();
                        support.getChannels().remove(chId);
                    }
                } else if (!isOwnUser(m.getAuthor())
                    && !discordService.hasSupportRole(m.getAuthor())
                    && isSupportChannel(m.getChannel())) {
                    // publish messages from non-admins and excluding bot's own messages
                    publishSupportEvent(m);
                }
            }
        });
    }

    private void publishSupportEvent(IMessage m) {
        LocalDateTime now = m.getTimestamp();
        LocalDateTime last = lastMessage.computeIfAbsent(m.getAuthor().getID(), k -> LocalDateTime.MIN);
        lastMessage.put(m.getAuthor().getID(), now);
        if (last.isBefore(now.minusHours(1))) {
            // ping subscribers at most once per hour per user
            Publisher publisher = getPublisher();
            for (Subscriber sub : publisher.getSubscribers()) {
                try {
                    discordService.privateMessage(sub.getUserId()).withContent(buildPingMessage(m)).send();
                } catch (Exception e) {
                    log.warn("Could not send PM to subscriber: {}", e.toString());
                }
            }
        }
    }

    private String buildPingMessage(IMessage m) {
        return String.format("Hey! %s is looking for help at %s: %s", m.getAuthor().mention(), m.getChannel().mention(), m.getContent());
    }

    private boolean isSupportChannel(IChannel channel) {
        return support.getChannels().contains(channel.getID());
    }

    private boolean isMasterUser(IUser user) {
        return properties.getDiscord().getMasters().contains(user.getID());
    }

    private boolean isOwnUser(IUser user) {
        return user.getID().equals(discordService.getClient().getOurUser().getID());
    }

    private void subscribeUser(IUser user) {
        if (discordService.hasSupportRole(user)) {
            Publisher publisher = getPublisher();
            Subscriber subscriber = subscriberRepository.findByUserId(user.getID()).orElseGet(() -> newSubscriber(user));
            Set<Subscriber> subs = publisher.getSubscribers();
            if (subs.stream().anyMatch(s -> s.getUserId().equals(user.getID()))) {
                try {
                    discordService.privateMessage(user)
                        .withContent("You are already subscribed. Enter .unsub to cancel subscription").send();
                } catch (Exception e) {
                    log.warn("Could not send PM to {}: {}", user, e.toString());
                }
            } else {
                subs.add(subscriber);
                subscriberRepository.save(subscriber);
                publisherRepository.save(publisher);
                try {
                    discordService.privateMessage(user)
                        .withContent("Now receiving support messages, enter .unsub to cancel subscription").send();
                } catch (Exception e) {
                    log.warn("Could not send PM to {}: {}", user, e.toString());
                }
            }
        }
    }

    private void unsubscribeUser(IUser user) {
        subscriberRepository.findByUserId(user.getID()).ifPresent(sub -> {
            Publisher publisher = getPublisher();
            Set<Subscriber> subs = publisher.getSubscribers();
            if (subs.stream().anyMatch(s -> s.getUserId().equals(user.getID()))) {
                subs.remove(sub);
                publisherRepository.save(publisher);
                try {
                    discordService.privateMessage(user)
                        .withContent("You are no longer receiving support messages").send();
                } catch (Exception e) {
                    log.warn("Could not send PM to {}: {}", user, e.toString());
                }
            } else {
                try {
                    discordService.privateMessage(user)
                        .withContent("You are not subscribed to the support channels").send();
                } catch (Exception e) {
                    log.warn("Could not send PM to {}: {}", user, e.toString());
                }
            }
        });
    }

    private Publisher getPublisher() {
        return publisherRepository.findByName(KEY).orElseGet(() -> newPublisher(KEY));
    }

    private Publisher newPublisher(String name) {
        Publisher publisher = new Publisher();
        publisher.setName(name);
        return publisher;
    }

    private Subscriber newSubscriber(IUser user) {
        Subscriber subscriber = new Subscriber();
        subscriber.setUserId(user.getID());
        subscriber.setName("user:" + user.getName());
        return subscriber;
    }
}
