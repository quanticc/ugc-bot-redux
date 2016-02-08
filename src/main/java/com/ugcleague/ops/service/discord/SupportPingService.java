package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.Publisher;
import com.ugcleague.ops.domain.Subscriber;
import com.ugcleague.ops.repository.PublisherRepository;
import com.ugcleague.ops.repository.SubscriberRepository;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.EventSubscriber;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.ugcleague.ops.service.discord.CommandService.newParser;

@Service
@Transactional
public class SupportPingService implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SupportPingService.class);
    private static final String KEY = "support";

    private final DiscordService discordService;
    private final PermissionService permissionService;
    private final PublisherRepository publisherRepository;
    private final SubscriberRepository subscriberRepository;
    private final CommandService commandService;

    private Map<String, LocalDateTime> lastMessage = new ConcurrentHashMap<>();
    private OptionSpec<String> subNonOptionSpec;

    @Autowired
    public SupportPingService(LeagueProperties properties, DiscordService discordService,
                              PermissionService permissionService, PublisherRepository publisherRepository,
                              SubscriberRepository subscriberRepository, CommandService commandService) {
        this.permissionService = permissionService;
        this.discordService = discordService;
        this.publisherRepository = publisherRepository;
        this.subscriberRepository = subscriberRepository;
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
        commandService.register(CommandBuilder.equalsTo(".sub")
            .description("Subscribe to support channel messages sent by regular users " +
                "(max 1 PM per user per hour)")
            .support().command(this::executeSubCommand).build());
        commandService.register(CommandBuilder.equalsTo(".unsub")
            .description("Unsubscribe from support channel messages")
            .support().command(this::executeUnsubCommand).build());
        OptionParser parser = newParser();
        subNonOptionSpec = parser.nonOptions("Exactly two arguments, separated by a space, containing a range of time." +
            " Examples of permitted time expressions are \"midnight\", \"8am UTC+12\" or \"16:00 PST\". Expressions must" +
            " be wrapped within quotes if containing spaces so they are picked up as a single argument.")
            .ofType(String.class);
        commandService.register(CommandBuilder.combined(".sub on")
            .description("Enable support PMs during the given hours")
            .support().parser(parser).command(this::subOnTimeCommand).build());
        commandService.register(CommandBuilder.combined(".sub off")
            .description("Disable support PMs during the given hours")
            .support().parser(parser).command(this::subOffTimeCommand).build());
        commandService.register(CommandBuilder.equalsTo(".sub status")
            .description("Get my current configuration about support PMs")
            .support().command(this::subStatusCommand).build());
    }

    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent event) {
        IMessage m = event.getMessage();
        if (!discordService.isOwnUser(m.getAuthor())
            && permissionService.canPerform("support.publish", m.getAuthor(), m.getChannel())) {
            publishSupportEvent(m);
        }
    }

    private String subStatusCommand(IMessage message, OptionSet optionSet) {
        Publisher publisher = getPublisher();
        IUser author = message.getAuthor();
        Subscriber subscriber = subscriberRepository.findByUserId(author.getID()).orElseGet(() -> newSubscriber(author));
        Set<Subscriber> subs = publisher.getSubscribers();
        StringBuilder response = new StringBuilder();
        boolean subscribed = subs.stream().anyMatch(s -> s.getUserId().equals(author.getID()));
        if (subscribed) {
            Boolean enabled = subscriber.getEnabled();
            ZonedDateTime start = subscriber.getStart();
            ZonedDateTime finish = subscriber.getFinish();
            response.append("*Current settings:*\n");
            if (enabled == null || start == null || finish == null) {
                response.append("Receiving support PMs all day\n");
            } else if (enabled) {
                response.append("Receiving support PMs between **")
                    .append(formatTime(start)).append("** and **").append(formatTime(finish)).append("**\n");
            } else {
                response.append("You will *not* receive support PMs between **")
                    .append(formatTime(start)).append("** and **").append(formatTime(finish)).append("**\n");
            }
            response.append("This means that, at this moment, you **").append(hasPingsEnabled(subscriber) ? "would" : "would not")
                .append("** receive support PMs.\n");
        } else {
            response.append("You are not subscribed to support PMs\n");
        }
        appendFooter(response);
        return response.toString();
    }

    private String subOnTimeCommand(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(subNonOptionSpec);
        if (optionSet.has("?")) {
            return null;
        }
        // if no non-options are given, treat as .unsub
        if (nonOptions.isEmpty()) {
            return executeSubCommand(message, optionSet);
        }
        return subTimeCommand(message, optionSet, true);
    }

    private String subOffTimeCommand(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(subNonOptionSpec);
        if (optionSet.has("?")) {
            return null;
        }
        // if no non-options are given, treat as .unsub
        if (nonOptions.isEmpty()) {
            return executeUnsubCommand(message, optionSet);
        }
        return subTimeCommand(message, optionSet, false);
    }

    private String subTimeCommand(IMessage message, OptionSet optionSet, boolean enabled) {
        List<String> nonOptions = optionSet.valuesOf(subNonOptionSpec);
        if (nonOptions.size() != 2) {
            return "You must enter **exactly two** arguments, wrap your expressions within quotes if they use spaces";
        }
        String startTimex = nonOptions.get(0);
        String finishTimex = nonOptions.get(1);
        ZonedDateTime start = parseTimeDate(startTimex);
        ZonedDateTime finish = parseTimeDate(finishTimex);
        StringBuilder response = new StringBuilder();
        if (start == null || finish == null) {
            if (start == null) {
                response.append(":no_entry_sign: Starting time is invalid\n");
            }
            if (finish == null) {
                response.append(":no_entry_sign: Finishing time is invalid\n");
            }
            response.append("Remember you can also use complex time expressions like \"midnight\", \"8am UTC+12\" or \"16:00 PST\"\n")
                .append("Don't forget to wrap expressions with spaces within quotes!\n");
        } else {
            if (finish.isBefore(start)) {
                finish = finish.plusDays(1);
            }
            if (finish.isAfter(start.plusDays(1))) {
                response.append(":no_entry_sign:  You've specified a time frame that's longer than 24 hours");
            } else {
                Publisher publisher = getPublisher();
                IUser author = message.getAuthor();
                Subscriber subscriber = subscriberRepository.findByUserId(author.getID()).orElseGet(() -> newSubscriber(author));
                Set<Subscriber> subs = publisher.getSubscribers();
                subscriber.setEnabled(enabled);
                subscriber.setStart(start);
                subscriber.setFinish(finish);
                response.append("*Preferences updated. Current settings:*\n");
                if (enabled) {
                    response.append("Receiving support PMs between **")
                        .append(formatTime(start)).append("** and **").append(formatTime(finish)).append("**\n");
                } else {
                    response.append("You will *not* receive support PMs between **")
                        .append(formatTime(start)).append("** and **").append(formatTime(finish)).append("**\n");
                }
                subs.add(subscriber);
                subscriberRepository.save(subscriber);
                publisherRepository.save(publisher);
                log.info("[SupportPing] User subscribed with range: {} {} from {} to {}",
                    DiscordService.userString(author), enabled ? "on" : "off", start, finish);
            }
        }
        appendFooter(response);
        return response.toString();
    }

    private String formatTime(ZonedDateTime time) {
        String clock = ":clock" + (time.getHour() % 12 == 0 ? 12 : time.getHour() % 12) + (time.getMinute() >= 30 ? "30: " : ": ");
        return clock + time.format(DateTimeFormatter.ofPattern("HH:mm z"));
    }

    private ZonedDateTime parseTimeDate(String s) {
        List<Date> parsed = new PrettyTimeParser().parse(s); // never null, can be empty
        if (!parsed.isEmpty()) {
            Date first = parsed.get(0);
            return ZonedDateTime.ofInstant(first.toInstant(), ZoneId.systemDefault());
        }
        log.warn("Could not parse a valid date from input: {}", s);
        return null;
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
            getPublisher().getSubscribers().stream().filter(this::hasPingsEnabled).forEach(sub -> {
                try {
                    discordService.sendPrivateMessage(sub.getUserId(), buildPingMessage(m));
                } catch (Exception e) {
                    log.warn("Could not send PM to subscriber: {}", e.toString());
                }
            });
        }
    }

    private boolean hasPingsEnabled(Subscriber sub) {
        // if we reach this method is because the Subscriber IS currently subscribed to the publisher
        // so the fallback behavior should be the opposite of the current value of Subscriber#getEnabled
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime start = sub.getStart();
        ZonedDateTime finish = sub.getFinish();
        Boolean enabled = sub.getEnabled();
        if (enabled == null || start == null || finish == null) {
            return true;
        }
        Period delta = Period.between(start.toLocalDate(), now.toLocalDate());
        if (now.isAfter(start.plus(delta)) && now.isBefore(finish.plus(delta))) {
            return enabled;
        } else {
            return !enabled;
        }
    }

    private String buildPingMessage(IMessage m) {
        return String.format("%s needs help at %s: %s", m.getAuthor().mention(), m.getChannel().mention(), m.getContent());
    }

    private String subscribeUser(IUser user) {
        StringBuilder response = new StringBuilder();
        Publisher publisher = getPublisher();
        Subscriber subscriber = subscriberRepository.findByUserId(user.getID()).orElseGet(() -> newSubscriber(user));
        Set<Subscriber> subs = publisher.getSubscribers();
        if (subs.stream().anyMatch(s -> s.getUserId().equals(user.getID()))) {
            // assume that user wants to remove previous settings
            subscriber.setEnabled(null);
            subscriber.setStart(null);
            subscriber.setFinish(null);
            subscriberRepository.save(subscriber);
        } else {
            subs.add(subscriber);
            subscriberRepository.save(subscriber);
            publisherRepository.save(publisher);
            log.info("[SupportPing] User subscribed: {}", DiscordService.userString(user));
        }
        response.append("*Preferences updated:* Receiving support PMs during the day\n");
        appendFooter(response);
        return response.toString();
    }

    private String unsubscribeUser(IUser user) {
        Optional<Subscriber> o = subscriberRepository.findByUserId(user.getID());
        StringBuilder response = new StringBuilder();
        if (o.isPresent()) {
            Subscriber sub = o.get();
            Publisher publisher = getPublisher();
            Set<Subscriber> subs = publisher.getSubscribers();
            if (subs.stream().anyMatch(s -> s.getUserId().equals(user.getID()))) {
                subs.remove(sub);
                publisherRepository.save(publisher);
                log.info("[SupportPing] User unsubscribed: {}", DiscordService.userString(user));
                response.append("*Preferences updated:* No longer receiving support PMs\n");
            } else {
                response.append("You are not subscribed to the support channel\n");
                appendFooter(response);
            }
        }
        return response.toString();
    }

    private void appendFooter(StringBuilder response) {
        response.append("*Enter `.sub` to receive support PMs all day*\n")
            .append("*Enter `.sub on` or `.sub off` to receive or block PMs within a time range*\n")
            .append("*Enter `.unsub` to shutdown all support PMs*\n")
            .append("*Enter `.sub status` to get your current configuration*\n");
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
