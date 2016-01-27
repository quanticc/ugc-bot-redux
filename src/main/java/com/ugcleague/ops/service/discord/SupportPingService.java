package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.Publisher;
import com.ugcleague.ops.domain.Subscriber;
import com.ugcleague.ops.repository.PublisherRepository;
import com.ugcleague.ops.repository.SubscriberRepository;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionSet;
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
import java.util.Optional;
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
    private final CommandService commandService;

    private Map<String, LocalDateTime> lastMessage = new ConcurrentHashMap<>();

    @Autowired
    public SupportPingService(LeagueProperties properties, DiscordService discordService,
                              PublisherRepository publisherRepository, SubscriberRepository subscriberRepository,
                              CommandService commandService) {
        this.properties = properties;
        this.discordService = discordService;
        this.publisherRepository = publisherRepository;
        this.subscriberRepository = subscriberRepository;
        this.commandService = commandService;
        this.support = properties.getDiscord().getSupport();
    }

    @PostConstruct
    private void configure() {
        commandService.register(CommandBuilder.equalsTo(".sub")
            .description("Subscribe to support channel messages sent by regular users. " +
                "(Max 1 PM per user per hour)")
            .permission("support").command(this::executeSubCommand).build());
        commandService.register(CommandBuilder.equalsTo(".unsub")
            .description("Unsubscribe from support channel messages.")
            .permission("support").command(this::executeUnsubCommand).build());
        discordService.subscribe(new IListener<MessageReceivedEvent>() {

            @Override
            public void handle(MessageReceivedEvent event) {
                IMessage m = event.getMessage();
                if (!isOwnUser(m.getAuthor())
                    && !discordService.hasSupportRole(m.getAuthor())
                    && isSupportChannel(m.getChannel())) {
                    // publish messages from non-admins and excluding bot's own messages
                    publishSupportEvent(m);
                }
            }
        });
    }

    private String executeSubCommand(IMessage message, OptionSet optionSet) {
        return subscribeUser(message.getAuthor());
    }

    private String executeUnsubCommand(IMessage message, OptionSet optionSet) {
        return unsubscribeUser(message.getAuthor());
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
        return String.format("%s needs help at %s: %s", m.getAuthor().mention(), m.getChannel().mention(), m.getContent());
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

    private String subscribeUser(IUser user) {
        if (discordService.isMaster(user) || discordService.hasSupportRole(user)) {
            Publisher publisher = getPublisher();
            Subscriber subscriber = subscriberRepository.findByUserId(user.getID()).orElseGet(() -> newSubscriber(user));
            Set<Subscriber> subs = publisher.getSubscribers();
            if (subs.stream().anyMatch(s -> s.getUserId().equals(user.getID()))) {
                return "You are already subscribed. Type .unsub to stop receiving messages";
            } else {
                subs.add(subscriber);
                subscriberRepository.save(subscriber);
                publisherRepository.save(publisher);
                log.info("[SupportPing] User subscribed: {}", DiscordService.userString(user));
                return "Now receiving support messages, type .unsub to stop receiving messages";
            }
        }
        return "";
    }

    private String unsubscribeUser(IUser user) {
        Optional<Subscriber> o = subscriberRepository.findByUserId(user.getID());
        if (o.isPresent()) {
            Subscriber sub = o.get();
            Publisher publisher = getPublisher();
            Set<Subscriber> subs = publisher.getSubscribers();
            if (subs.stream().anyMatch(s -> s.getUserId().equals(user.getID()))) {
                subs.remove(sub);
                publisherRepository.save(publisher);
                log.info("[SupportPing] User unsubscribed: {}", DiscordService.userString(user));
                return "You are no longer receiving support messages";
            } else {
                return "You are not subscribed to the support channels. Type .sub to subscribe";
            }
        }
        return "";
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
        subscriber.setName(user.getName());
        return subscriber;
    }
}
