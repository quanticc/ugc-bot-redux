package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.StatusWrapper;
import com.ugcleague.ops.service.util.GitProperties;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.DiscordEndpoints;
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.api.internal.DiscordUtils;
import sx.blah.discord.handle.impl.obj.Channel;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.json.responses.MessageResponse;
import sx.blah.discord.util.HTTP429Exception;
import sx.blah.discord.util.Requests;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.ugcleague.ops.util.DateUtil.formatHuman;
import static java.util.Arrays.asList;

@Service
public class DiscordQueryService {

    private static final Logger log = LoggerFactory.getLogger(DiscordQueryService.class);

    private final CommandService commandService;
    private final DiscordService discordService;

    private OptionSpec<String> profileNameSpec;
    private OptionSpec<String> profileAvatarSpec;
    private OptionSpec<String> profileGameSpec;
    private GitProperties gitProperties;
    private OptionSpec<Integer> rateNumberSpec;
    private OptionSpec<Integer> rateWaitSpec;
    private OptionSpec<Boolean> rateStatusSpec;
    private Command rateCommand;

    @Autowired
    public DiscordQueryService(CommandService commandService, DiscordService discordService) {
        this.commandService = commandService;
        this.discordService = discordService;
    }

    @PostConstruct
    private void configure() {
        commandService.register(CommandBuilder.equalsTo(".beep info")
            .description("Get Discord information about the bot").permission(0).originReplies()
            .command(this::executeInfoCommand).build());
        commandService.register(CommandBuilder.equalsTo(".beep clear")
            .description("Remove all of bot's messages from this channel").permission(0)
            .command(this::executeClearCommand).build());
        commandService.register(CommandBuilder.equalsTo(".beep boop")
            .description("Test command please ignore").permission(0)
            .command(this::executePMCommand).build());
        commandService.register(CommandBuilder.equalsTo(".beep load")
            .description("Load more messages in this channel").permission("master").experimental()
            .command((message, optionSet) -> {
                try {
                    loadChannelMessages(discordService.getClient(), (Channel) message.getChannel());
                } catch (IOException | HTTP429Exception | DiscordException e) {
                    log.warn("Could not load messages", e);
                }
                return "";
            }).build());
        commandService.register(CommandBuilder.equalsTo(".beep exit")
            .description("Exit discord").permission("master").experimental()
            .command((message, optionSet) -> {
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        log.warn("Logout interrupted");
                    }
                }).thenRun(discordService::terminate);
                return "";
            }).build());
        initRateTestCommand();
        initProfileCommand();
    }

    private void initRateTestCommand() {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        rateNumberSpec = parser.acceptsAll(asList("n", "number"), "number of messages to send")
            .withRequiredArg().ofType(Integer.class).defaultsTo(10);
        rateWaitSpec = parser.acceptsAll(asList("w", "wait"), "waiting milliseconds before each message")
            .withRequiredArg().ofType(Integer.class).defaultsTo(250);
        rateStatusSpec = parser.acceptsAll(asList("s", "status"), "display progress as status mode (successive edits)")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        rateCommand = CommandBuilder.startsWith(".test")
            .description("Test API rate limits").permission("master").experimental().queued().parser(parser)
            .command((message, optionSet) -> {
                if (optionSet.has("?")) {
                    return null;
                }
                int limit = Math.max(1, optionSet.valueOf(rateNumberSpec));
                long sleep = Math.max(1, optionSet.valueOf(rateWaitSpec));
                boolean status = optionSet.has(rateStatusSpec) ? optionSet.valueOf(rateStatusSpec) : false;
                for (int i = 0; i < limit; i++) {
                    try {
                        if (status) {
                            commandService.statusReplyFrom(message, rateCommand,
                                StatusWrapper.ofWork(i + 1, limit)
                                    .withMessage("Working in the hall at " + Instant.now().toString())
                                    .bar().text());
                        } else {
                            String msg = String.format("[%d/%d] %s", i + 1, limit, Instant.now().toString());
                            commandService.replyFrom(message, rateCommand, msg);
                        }
                        Thread.sleep(sleep);
                    } catch (DiscordException | MissingPermissionsException | InterruptedException e) {
                        log.warn("Could not send test message", e);
                    }
                }
                return "";
            }).build();
        commandService.register(rateCommand);
    }

    private String executePMCommand(IMessage m, OptionSet optionSet) {
        try {
            discordService.sendPrivateMessage(m.getAuthor(), "(╯°□°）╯︵ ┻━┻ " + randomMessage());
        } catch (Exception e) {
            log.warn("Could not send PM to user: {}", e.toString());
        }
        return "";
    }

    public static void loadChannelMessages(IDiscordClient client, Channel channel) throws IOException, HTTP429Exception, DiscordException {
        try {
            if (!(channel instanceof IPrivateChannel) && !(channel instanceof IVoiceChannel))
                DiscordUtils.checkPermissions(client, channel, EnumSet.of(Permissions.READ_MESSAGE_HISTORY));
        } catch (MissingPermissionsException e) {
            Discord4J.LOGGER.warn("Error getting messages for channel " + channel.getName() + ": {}", e.getErrorMessage());
            return;
        }

        IMessage oldest = channel.getMessages().get(channel.getMessages().size() - 1);
        String before = oldest.getID();
        String response = Requests.GET.makeRequest(DiscordEndpoints.CHANNELS + channel.getID() + "/messages?limit=50&before=" + before,
            new BasicNameValuePair("authorization", client.getToken()));
        MessageResponse[] messages = DiscordUtils.GSON.fromJson(response, MessageResponse[].class);

        for (MessageResponse message : messages) {
            channel.addMessage(DiscordUtils.getMessageFromJSON(client, channel, message));
        }
    }

    public static String randomMessage() {
        String result = "???!!!!111";
        try {
            URL url = new URL("http://whatthecommit.com/index.txt");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                result = reader.readLine();
            } catch (IOException e) {
                log.warn("Could not get a random message: {}", e.toString());
            }
        } catch (MalformedURLException ignored) {
        }
        return result;
    }

    private void initProfileCommand() {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        profileNameSpec = parser.acceptsAll(asList("n", "name"), "change bot's name").withRequiredArg();
        profileAvatarSpec = parser.acceptsAll(asList("a", "avatar"), "change bot's avatar").withRequiredArg();
        profileGameSpec = parser.acceptsAll(asList("g", "game"), "change bot's game").withRequiredArg();
        commandService.register(CommandBuilder.startsWith(".beep profile")
            .description("Edit this bot's profile").permission("master")
            .parser(parser).command(this::executeProfileCommand).build());
    }

    private String executeProfileCommand(IMessage message, OptionSet o) {
        if (o.has("?")) {
            return null;
        }
        Optional<String> name = Optional.ofNullable(o.valueOf(profileNameSpec));
        Optional<IDiscordClient.Image> avatar = Optional.ofNullable(o.valueOf(profileAvatarSpec)).map(s -> IDiscordClient.Image.forUrl("jpeg", s));
        Optional<String> game = Optional.ofNullable(o.valueOf(profileGameSpec));
        IDiscordClient client = discordService.getClient();
        client.updatePresence(client.getOurUser().getPresence().equals(Presences.IDLE), game);
        try {
            discordService.changeAccountInfo(name, avatar);
            return "Account info changed";
        } catch (DiscordException | InterruptedException e) {
            log.warn("Could not change account info", e);
            return "Could not change account info";
        }
    }

    private String executeClearCommand(IMessage m, OptionSet optionSet) {
        IDiscordClient client = discordService.getClient();
        IChannel c = client.getChannelByID(m.getChannel().getID());
        if (c != null) {
            c.getMessages().stream().filter(message -> message.getAuthor().getID()
                .equalsIgnoreCase(client.getOurUser().getID())).forEach(message -> {
                try {
                    discordService.deleteMessage(message);
                } catch (MissingPermissionsException e) {
                    log.warn("No permission to perform action: {}", e.toString());
                } catch (InterruptedException e) {
                    log.warn("Operation was interrupted");
                } catch (DiscordException e) {
                    log.warn("Discord exception", e);
                }
            });
        }
        return "";
    }

    private String executeInfoCommand(IMessage m, OptionSet o) {
        if (gitProperties == null) {
            gitProperties = new GitProperties();
        }
        StringBuilder builder = new StringBuilder();
        IUser master = discordService.getMasterUser();
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long uptime = rb.getUptime();
        String version = gitProperties.getProperty("git.commit.id.describe", "0.x");
        builder.append("Hello! I'm here to help with **UGC support**.\n")
            .append(String.format("I was built by %s using the **Discord4J** library `v%s`.\n" +
                    "Running `v%s`. Uptime: %s.\nCheck the available commands with `.beep help`",
                master.mention(), Discord4J.VERSION, version, formatHuman(Duration.ofMillis(uptime))));
        return builder.toString();
    }
}
