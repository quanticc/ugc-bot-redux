package com.ugcleague.ops.service.discord;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.ugcleague.ops.domain.document.ChannelSubscription;
import com.ugcleague.ops.domain.document.Publisher;
import com.ugcleague.ops.domain.document.Subscription;
import com.ugcleague.ops.event.*;
import com.ugcleague.ops.repository.mongo.PublisherRepository;
import com.ugcleague.ops.service.DiscordCacheService;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ugcleague.ops.util.DateUtil.formatRelative;
import static java.util.Arrays.asList;

/**
 * Manages announcement handling.
 * <ul>
 * <li>announce start</li>
 * <li>announce stop</li>
 * <li>announce test</li>
 * </ul>
 */
@Service
@Transactional
public class AnnouncePresenter {

    private static final Logger log = LoggerFactory.getLogger(AnnouncePresenter.class);
    private static final String nonOptDesc = "Announcer name";

    private final DiscordService discordService;
    private final PublisherRepository publisherRepository;
    private final DiscordCacheService cacheService;
    private final CommandService commandService;
    private final SettingsService settingsService;

    private OptionSpec<String> startNonOptionSpec;
    private OptionSpec<String> stopNonOptionSpec;
    private OptionSpec<String> testNonOptionSpec;
    private OptionSpec<String> testMessageSpec;

    @Autowired
    public AnnouncePresenter(DiscordService discordService, PublisherRepository publisherRepository,
                             DiscordCacheService cacheService, CommandService commandService,
                             SettingsService settingsService) {
        this.discordService = discordService;
        this.publisherRepository = publisherRepository;
        this.cacheService = cacheService;
        this.commandService = commandService;
        this.settingsService = settingsService;
    }

    @PostConstruct
    private void configure() {
        initStartAnnounceCommand();
        initStopAnnounceCommand();
        initTestAnnounceCommand();
    }

    private void initTestAnnounceCommand() {
        // .announce test -m "message" (non-option: publishers)
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        testNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        testMessageSpec = parser.acceptsAll(asList("m", "message"), "message to publish").withRequiredArg();
        commandService.register(CommandBuilder.startsWith(".announce test")
            .description("Send a test announcement").master()
            .parser(parser).command(this::executeTestAnnounceCommand).build());
    }

    private void initStartAnnounceCommand() {
        // .announce start (non-option: publishers)
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        startNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".announce start")
            .description("Enable multi-purpose announcements to this channel").master()
            .parser(parser).command(this::executeStartAnnounceCommand).build());
    }

    private void initStopAnnounceCommand() {
        // .announce stop (non-option: publishers)
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        stopNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".announce stop")
            .description("Disable an already subscribed announcer from this channel").master()
            .parser(parser).command(this::executeStopAnnounceCommand).build());
    }

    private String executeTestAnnounceCommand(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(testNonOptionSpec);
        if (!o.has("?") && !nonOptions.isEmpty()) {
            String message = Optional.ofNullable(o.valueOf(testMessageSpec)).orElse("Test message");
            for (String announcer : nonOptions) {
                announce(announcer, message);
            }
            return "";
        }
        return null;
    }

    private String executeStartAnnounceCommand(IMessage m, OptionSet o) {
        if (!o.has("?")) {
            StringBuilder message = new StringBuilder();
            for (String opt : o.valuesOf(startNonOptionSpec)) {
                String line = subscribeAnnouncer(m.getAuthor(), opt, m.getChannel());
                if (line != null) {
                    message.append(line).append("\n");
                }
            }
            return message.toString();
        }
        return null;
    }

    private String executeStopAnnounceCommand(IMessage m, OptionSet o) {
        if (!o.has("?")) {
            StringBuilder message = new StringBuilder();
            for (String opt : o.valuesOf(stopNonOptionSpec)) {
                String line = unsubscribeAnnouncer(m.getAuthor(), opt, m.getChannel());
                if (line != null) {
                    message.append(line).append("\n");
                }
            }
            return message.toString();
        }
        return null;
    }

    private String subscribeAnnouncer(IUser author, String announcer, IChannel channel) {
        if (!announcer.isEmpty()) {
            Optional<Publisher> publisher = publisherRepository.findById(announcer);
            if (!publisher.isPresent()) {
                log.warn("Publisher not found by name: {}", announcer);
                return "Not a valid announcer name";
            }
            List<ChannelSubscription> matching = publisher.get().getChannelSubscriptions().stream()
                .filter(s -> s.getChannel().getId().equals(channel.getID())).collect(Collectors.toList());
            if (matching.isEmpty()) {
                ChannelSubscription subscription = new ChannelSubscription();
                subscription.setChannel(cacheService.getOrCreateChannel(channel));
                subscription.setEnabled(true);
                subscription.setMode(Subscription.Mode.ALWAYS);
                publisher.get().getChannelSubscriptions().add(subscription);
                publisherRepository.save(publisher.get());
                log.debug("Subscribing channel {} ({}) to announcer {}", channel.getName(), channel.getID(), announcer);
                if (channel.isPrivate()) {
                    return ":satellite: I'll PM you about **" + announcer + "**";
                } else {
                    return String.format("Subscription of %s to **%s** successful", channel.mention(), announcer);
                }
            } else {
                return "The channel is already receiving announcements about **" + announcer + "**";
            }
        } else {
            log.debug("Attempted to subscribe announcer: {}", author);
        }
        return null;
    }

    private String unsubscribeAnnouncer(IUser author, String announcer, IChannel channel) {
        if (!announcer.isEmpty()) {
            Optional<Publisher> publisher = publisherRepository.findById(announcer);
            if (!publisher.isPresent()) {
                log.warn("Publisher not found by name: {}", announcer);
                return "Not a valid announcer name";
            }
            List<ChannelSubscription> matching = publisher.get().getChannelSubscriptions().stream()
                .filter(s -> s.getChannel().getId().equals(channel.getID())).collect(Collectors.toList());
            if (matching.isEmpty()) {
                return "That channel is not receiving announcements about **" + announcer + "**";
            } else {
                for (ChannelSubscription subscription : matching) {
                    subscription.setEnabled(false);
                }
                publisherRepository.save(publisher.get());
                return String.format("Cancelled subscription of %s to **%s**", channel.mention(), announcer);
            }
        } else {
            log.debug("Attempted to subscribe announcer: {}", author);
        }
        return null;
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
        return String.format("%s published %s", latestEntryTitle, formatRelative(syndFeed.getPublishedDate().toInstant()));
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

    @EventListener
    private void onServerDeath(GameServerDeathEvent event) {
        String list = event.getSource().entrySet().stream()
            .filter(e -> e.getValue().getAttempts().get() > 5)
            .map(e -> String.format("• **%s** (%s) is unresponsive since %s",
                e.getKey().getShortName(), e.getKey().getAddress(),
                formatRelative(e.getValue().getCreated())))
            .collect(Collectors.joining("\n"));
        announce("issues", "*Game Server Status*\n" + list);
    }

    public void announce(String publisherName, String message) {
        publisherRepository.findById(publisherName).ifPresent(pub -> {
            Map<String, SettingsService.AnnounceData> latest = settingsService.getSettings().getLastAnnounce();
            if (latest.get(publisherName).getMessage().equals(message)) {
                log.debug("Not publishing identical announcement to {}", publisherName);
            } else {
                latest.put(publisherName, new SettingsService.AnnounceData(message));
                Set<ChannelSubscription> subs = pub.getChannelSubscriptions();
                subs.stream().filter(Subscription::isEnabled).forEach(sub -> {
                    try {
                        IDiscordClient client = discordService.getClient();
                        IChannel channel = client.getChannelByID(sub.getChannel().getId());
                        if (channel != null) {
                            log.debug("Making an announcement from {} to {}", publisherName, channel.getName());
                            commandService.answerToChannel(channel, "**[" + publisherName + "]** " + message);
                        } else {
                            log.warn("Could not find a channel with id {} to send our {} message", sub.getChannel().getId(), publisherName);
                        }
                    } catch (Exception e) {
                        log.warn("Could not send message to '{}': {}", publisherName, e.toString());
                    }
                });
            }
        });
    }
}
