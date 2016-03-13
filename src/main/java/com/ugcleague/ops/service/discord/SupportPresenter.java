package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.DiscordUser;
import com.ugcleague.ops.domain.document.Publisher;
import com.ugcleague.ops.domain.document.Subscription;
import com.ugcleague.ops.domain.document.UserSubscription;
import com.ugcleague.ops.repository.mongo.PublisherRepository;
import com.ugcleague.ops.service.DiscordCacheService;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.PermissionService;
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
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static java.util.Arrays.asList;

/**
 * Commands to handle support ping or "PM on incoming user message" feature.
 * <ul>
 * <li>sub</li>
 * <li>unsub</li>
 * <li>sub on</li>
 * <li>sub off</li>
 * <li>sub status</li>
 * </ul>
 */
@Service
@Transactional
public class SupportPresenter implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SupportPresenter.class);

    private final DiscordService discordService;
    private final CommandService commandService;
    private final PermissionService permissionService;
    private final DiscordCacheService cacheService;
    private final PublisherRepository publisherRepository;
    private final Map<String, ZonedDateTime> lastMessage = new ConcurrentHashMap<>();

    private OptionSpec<String> subToSpec;
    private OptionSpec<String> unsubFromSpec;
    private OptionSpec<Void> subOnSpec;
    private OptionSpec<Void> subOffSpec;
    private OptionSpec<String> subNonOptionSpec;
    private OptionSpec<Void> subStatusSpec;
    private OptionSpec<String> supportEnableSpec;
    private OptionSpec<String> supportDisableSpec;
    private OptionSpec<Void> supportListSpec;

    @Autowired
    public SupportPresenter(DiscordService discordService, CommandService commandService,
                            PermissionService permissionService, DiscordCacheService cacheService,
                            PublisherRepository publisherRepository) {
        this.discordService = discordService;
        this.commandService = commandService;
        this.permissionService = permissionService;
        this.cacheService = cacheService;
        this.publisherRepository = publisherRepository;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);

        initSubCommand();
        initUnsubCommand();
        initManageCommand();
    }

    private void initSubCommand() {
        OptionParser parser = newParser();
        Map<String, String> aliases = newAliasesMap();
        aliases.put("to", "--to");
        aliases.put("on", "--on");
        aliases.put("off", "--off");
        aliases.put("status", "--status");
        subToSpec = parser.acceptsAll(asList("c", "channels", "t", "to"), "Support channels to join")
            .withRequiredArg().withValuesSeparatedBy(",").defaultsTo("tf2").describedAs("supportId");
        subOnSpec = parser.accepts("on", "Enable support PMs during the given hours");
        subOffSpec = parser.accepts("off", "Disable support PMs during the given hours");
        subStatusSpec = parser.accepts("status", "Display your current sub status");
        subNonOptionSpec = parser.nonOptions("Exactly two arguments, separated by a space, containing a range of time." +
            " Examples of permitted time expressions are \"midnight\", \"8am UTC+12\" or \"16:00 PST\". Expressions must" +
            " be wrapped within quotes if containing spaces so they are picked up as a single argument.");
        commandService.register(CommandBuilder.anyMatch(".sub")
            .description("Subscribe to support channel messages sent by regular users " +
                "(max 1 PM per user per hour)")
            .support().parser(parser).optionAliases(aliases).command(this::sub).build());
    }

    private void initUnsubCommand() {
        OptionParser parser = newParser();
        Map<String, String> aliases = newAliasesMap();
        aliases.put("from", "--from");
        unsubFromSpec = parser.acceptsAll(asList("c", "channels", "f", "from"), "Support channels to leave")
            .withRequiredArg().withValuesSeparatedBy(",").defaultsTo("tf2").describedAs("supportId");
        commandService.register(CommandBuilder.anyMatch(".unsub")
            .description("Unsubscribe from support channel messages")
            .support().parser(parser).optionAliases(aliases).command(this::unsub).build());
    }

    private void initManageCommand() {
        /*
        .support enable <name> [in <channel>]
        .support disable <name> [in <channel>]
        .support list
         */
        OptionParser parser = newParser();
        Map<String, String> aliases = newAliasesMap();
        aliases.put("enable", "--enable");
        aliases.put("disable", "--disable");
        aliases.put("list", "--list");
        supportEnableSpec = parser.acceptsAll(asList("e", "enable"), "Enable support pings from this channel")
            .withRequiredArg();
        supportDisableSpec = parser.acceptsAll(asList("d", "disable"), "Disable support pings from this channel")
            .withRequiredArg();
        supportListSpec = parser.acceptsAll(asList("l", "list"), "List current support ping channels");
        commandService.register(CommandBuilder.anyMatch(".support")
            .description("Manage support ping system")
            .support().originReplies().parser(parser).optionAliases(aliases).command(this::manage).build());
    }

    //// Commands

    private String manage(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (optionSet.has(supportListSpec)) {
            StringBuilder response = new StringBuilder();
            for (Publisher publisher : publisherRepository.findAll()) {
                String channel = Optional.ofNullable(discordService.getClient().getChannelByID(publisher.getChannelId()))
                    .map(IChannel::mention).orElse(publisher.getChannelId());
                if (channel != null) {
                    response.append("[**").append(publisher.getId()).append("**] ").append(publisher.isEnabled() ? "Enabled" : "Disabled")
                        .append(" from ").append(channel).append(" to ").append(publisher.getId()).append("\n");
                }
            }
            return response.toString();
        }

        if (optionSet.has(supportEnableSpec)) {
            String key = optionSet.valueOf(supportEnableSpec);
            Optional<Publisher> optional = publisherRepository.findById(key);
            if (optional.isPresent() && optional.get().isEnabled()) {
                return "Support PMs already enabled from " + message.getChannel().mention() + " to `" + key + "`";
            }
            Publisher publisher = optional.orElseGet(() -> newPublisher(key, message.getChannel().getID()));
            publisher.setEnabled(true);
            publisherRepository.save(publisher);
            return "Support PMs enabled from " + message.getChannel().mention() + " to `" + key + "`";
        }

        if (optionSet.has(supportDisableSpec)) {
            String key = optionSet.valueOf(supportDisableSpec);
            Optional<Publisher> optional = publisherRepository.findById(key);
            if (optional.isPresent() && !optional.get().isEnabled()) {
                return "Support PMs already disabled from " + message.getChannel().mention() + " to `" + key + "`";
            }
            Publisher publisher = optional.orElseGet(() -> newPublisher(key, message.getChannel().getID()));
            publisher.setEnabled(false);
            publisherRepository.save(publisher);
            return "Support PMs disabled from " + message.getChannel().mention() + " to `" + key + "`";
        }

        return null;
    }

    private Publisher newPublisher(String id, String channelId) {
        Publisher p = new Publisher();
        p.setId(id);
        p.setChannelId(channelId);
        return p;
    }

    private String sub(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }
        if (optionSet.has(subStatusSpec)) {
            return subStatus(message);
        }
        boolean on = optionSet.has(subOnSpec);
        boolean off = optionSet.has(subOffSpec);
        if (on && off) {
            return "Must use `on` or `off` only once";
        }
        StringBuilder response = new StringBuilder();
        // non options are exclusively used for time expressions
        List<String> nonOptions = optionSet.valuesOf(subNonOptionSpec);
        List<String> channels = optionSet.valuesOf(subToSpec);
        if (nonOptions.isEmpty() || (!on && !off)) {
            // if no options were specified, do not allow --on or --off
            if (on || off) {
                return "Must be used with two time expressions";
            } else {
                for (String key : channels) {
                    response.append(subscribeUser(message.getAuthor(), key));
                }
            }
        } else {
            // when specifying options, behavior depends on whether on or off was used
            // don't allow on and off simultaneously or none of them
            return subWithTimeFrame(message, channels, nonOptions, on);
        }
        return response.toString();
    }

    private String unsub(IMessage message, OptionSet optionSet) {
        StringBuilder response = new StringBuilder();
        for (String key : optionSet.valuesOf(unsubFromSpec)) {
            response.append(unsubscribeUser(message.getAuthor(), key));
        }
        return response.toString();
    }

    private String subStatus(IMessage message) {
        IUser author = message.getAuthor();
        StringBuilder response = new StringBuilder();
        for (Publisher publisher : publisherRepository.findAll()) {
            List<UserSubscription> matching = publisher.getUserSubscriptions().stream()
                .filter(s -> s.getUser().getId().equals(author.getID())).collect(Collectors.toList());
            boolean atLeastOneEnabled = matching.stream().anyMatch(Subscription::isEnabled);
            if (!matching.isEmpty() && atLeastOneEnabled) {
                response.append("[**").append(publisher.getId()).append("**] Enabled subscription(s):\n");
                for (UserSubscription sub : matching) {
                    boolean enabled = sub.isEnabled();
                    Subscription.Mode mode = sub.getMode();
                    ZonedDateTime start = sub.getStart();
                    ZonedDateTime finish = sub.getFinish();
                    if (enabled) {
                        if (mode == Subscription.Mode.ALWAYS || start == null || finish == null) {
                            response.append("Receiving PMs all day\n");
                        } else if (mode == Subscription.Mode.TIME_INCLUSIVE) {
                            response.append("Receiving PMs between **")
                                .append(formatTime(start)).append("** and **").append(formatTime(finish)).append("**\n");
                        } else {
                            response.append("You will *not* receive PMs between **")
                                .append(formatTime(start)).append("** and **").append(formatTime(finish)).append("**\n");
                        }
                    }
                }
                response.append("This means that, at this moment, you **").append(isActive(matching) ? "would" : "would not")
                    .append("** receive PMs.\n");
            }
        }
        if (response.length() == 0) {
            response.append("You are not subscribed to support PMs\n");
        }
        appendFooter(response);
        return response.toString();
    }

    //// Lower level actions

    private String subscribeUser(IUser user, String publisherName) {
        StringBuilder response = new StringBuilder();
        Optional<Publisher> publisher = getPublisher(publisherName);
        if (!publisher.isPresent()) {
            log.warn("Publisher does not exist: {}", publisherName);
            String available = publisherRepository.findAll().stream()
                .map(Publisher::getId).collect(Collectors.joining(", "));
            return "Invalid support channel. Available IDs: " + available + "\n";
        }
        DiscordUser discordUser = cacheService.getOrCreateUser(user);
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(discordUser);
        subscription.setEnabled(true);
        publisher.get().getUserSubscriptions().removeIf(s -> s.getUser().getId().equals(user.getID()));
        publisher.get().getUserSubscriptions().add(subscription);
        publisherRepository.save(publisher.get());
        log.info("[SupportPing] User subscribed to {}: {}", publisherName, DiscordService.userString(user));
        response.append("*Preferences updated:* Receiving **").append(publisherName).append("** support PMs\n");
        appendFooter(response);
        return response.toString();
    }

    private String unsubscribeUser(IUser user, String publisherName) {
        StringBuilder response = new StringBuilder();
        Optional<Publisher> publisher = getPublisher(publisherName);
        if (!publisher.isPresent()) {
            log.warn("Publisher does not exist: {}", publisherName);
            String available = publisherRepository.findAll().stream()
                .map(Publisher::getId).collect(Collectors.joining(", "));
            return "Invalid support channel. Available IDs: " + available + "\n";
        }
        List<UserSubscription> matching = publisher.get().getUserSubscriptions().stream()
            .filter(s -> s.getUser().getId().equals(user.getID())).collect(Collectors.toList());
        for (UserSubscription subscription : matching) {
            subscription.setEnabled(false);
        }
        if (!matching.isEmpty()) {
            publisherRepository.save(publisher.get());
            log.info("[SupportPing] User unsubscribed from {}: {}", publisherName, DiscordService.userString(user));
            response.append("*Preferences updated:* No longer receiving **").append(publisherName).append("** support PMs\n");
        } else {
            response.append("You are not subscribed to the **").append(publisherName).append("** support channel\n");
            appendFooter(response);
        }
        return response.toString();
    }

    private void appendFooter(StringBuilder response) {
        response.append("\nEnter `.sub` to receive TF2 support PMs all day\n")
            .append("Enter `.sub on` or `.sub off` to receive or block PMs within a time range\n")
            .append("Enter `.unsub` to shutdown all TF2 support PMs\n")
            .append("Enter `.sub status` to get your current configuration\n");
    }

    private String subWithTimeFrame(IMessage message, List<String> channels, List<String> timeExpressions, boolean enabled) {
        if (timeExpressions.size() != 2) {
            return "You must enter **exactly two** arguments, wrap your expressions within quotes if they use spaces";
        }
        String startTimex = timeExpressions.get(0);
        String finishTimex = timeExpressions.get(1);
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
                for (String key : channels) {
                    Optional<Publisher> publisher = getPublisher(key);
                    if (!publisher.isPresent()) {
                        String available = publisherRepository.findAll().stream()
                            .map(Publisher::getId).collect(Collectors.joining(", "));
                        response.append("Invalid support channel. Available IDs: ").append(available).append("\n");
                        continue;
                    }
                    IUser author = message.getAuthor();
                    DiscordUser discordUser = cacheService.getOrCreateUser(author);
                    UserSubscription subscription = new UserSubscription();
                    subscription.setUser(discordUser);
                    subscription.setEnabled(true);
                    subscription.setStart(start);
                    subscription.setFinish(finish);
                    publisher.get().getUserSubscriptions().removeIf(s -> s.getUser().getId().equals(author.getID()));
                    response.append("*Preferences updated. Current settings:*\n");
                    if (enabled) {
                        subscription.setMode(Subscription.Mode.TIME_INCLUSIVE);
                        response.append("Receiving ").append(key).append(" PMs between **")
                            .append(formatTime(start)).append("** and **").append(formatTime(finish)).append("**\n");
                    } else {
                        subscription.setMode(Subscription.Mode.TIME_EXCLUSIVE);
                        response.append("You will *not* receive ").append(key).append(" PMs between **")
                            .append(formatTime(start)).append("** and **").append(formatTime(finish)).append("**\n");
                    }
                    publisher.get().getUserSubscriptions().add(subscription);
                    publisherRepository.save(publisher.get());
                    log.info("[SupportPing] User subscribed to {} with range: {} {} from {} to {}", key,
                        DiscordService.userString(author), enabled ? "on" : "off", start, finish);
                }
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

    //// Publishing

    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent event) {
        IMessage m = event.getMessage();
        if (!discordService.isOwnUser(m.getAuthor())) {
            for (Publisher publisher : publisherRepository.findByChannelId(event.getMessage().getChannel().getID())) {
                // check if this user can trigger the publish event in this channel
                if (permissionService.canPerform("support.publish", m.getAuthor(), m.getChannel())) {
                    publishSupportEvent(m, publisher.getId());
                }
            }
        }
    }

    private void publishSupportEvent(IMessage m, String publisherName) {
        ZonedDateTime now = m.getTimestamp().atZone(ZoneId.systemDefault());
        ZonedDateTime last = lastMessage.computeIfAbsent(m.getAuthor().getID(),
            k -> Instant.EPOCH.atZone(ZoneId.systemDefault()));
        lastMessage.put(m.getAuthor().getID(), now);
        if (last.isBefore(now.minusHours(1))) {
            // ping subscribers at most once per hour per user
            Optional<Publisher> publisher = getPublisher(publisherName);
            if (publisher.isPresent()) {
                List<UserSubscription> subs = publisher.get().getUserSubscriptions().stream()
                    .filter(this::isActive).collect(Collectors.toList());
                Set<DiscordUser> pinged = new HashSet<>();
                for (UserSubscription sub : subs) {
                    if (!pinged.contains(sub.getUser())) {
                        try {
                            discordService.sendPrivateMessage(sub.getUser().getId(), buildPingMessage(m));
                        } catch (Exception e) {
                            log.warn("Could not send PM to subscriber: {}", e.toString());
                        }
                        pinged.add(sub.getUser());
                    }
                }
            } else {
                log.warn("Could not publish support event, publisher does not exist: {}", publisherName);
            }
        }
    }

    private Optional<Publisher> getPublisher(String key) {
        return publisherRepository.findById(key);
    }

    private boolean isActive(List<UserSubscription> subs) {
        return subs.stream().anyMatch(this::isActive);
    }

    private boolean isActive(UserSubscription sub) {
        // if we reach this method is because the Subscriber IS currently subscribed to the publisher
        // so the fallback behavior should be the opposite of the current value of Subscriber#getEnabled
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime start = sub.getStart();
        ZonedDateTime finish = sub.getFinish();
        boolean enabled = sub.isEnabled();
        Subscription.Mode mode = sub.getMode();
        if (!enabled) {
            return false;
        }
        if (start == null || finish == null) {
            log.warn("Defaulting to always ON mode due to missing start or finish date: {}", sub);
            return true;
        }
        if (mode == Subscription.Mode.ALWAYS) {
            return true;
        }
        Period delta = Period.between(start.toLocalDate(), now.toLocalDate());
        if (now.isAfter(start.plus(delta)) && now.isBefore(finish.plus(delta))) {
            return mode == Subscription.Mode.TIME_INCLUSIVE;
        } else {
            return mode == Subscription.Mode.TIME_EXCLUSIVE;
        }
    }

    private String buildPingMessage(IMessage m) {
        return String.format("[%s] %s: %s", m.getChannel().mention(), m.getAuthor().mention(), m.getContent());
    }
}
