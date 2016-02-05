package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.util.GitProperties;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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
import java.util.EnumSet;
import java.util.Optional;

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
        initProfileCommand();
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

        IMessage oldest = channel.getMessages().get(0);
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
        if (!o.has("?")) {
            Optional<String> name = Optional.ofNullable(o.valueOf(profileNameSpec));
            Optional<String> avatar = Optional.ofNullable(o.valueOf(profileAvatarSpec));
            Optional<String> game = Optional.ofNullable(o.valueOf(profileGameSpec));
            try {
                tryChangeAccountInfo(name, avatar, game);
            } catch (HTTP429Exception e) {
                log.warn("Could not change account info: {}", e.toString());
            } catch (DiscordException e) {
                log.warn("Discord exception", e);
            }
            IDiscordClient client = discordService.getClient();
            client.updatePresence(client.getOurUser().getPresence().equals(Presences.IDLE), game);
        }
        return null;
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L))
    private void tryChangeAccountInfo(Optional<String> name, Optional<String> avatar, Optional<String> game) throws HTTP429Exception, DiscordException {
        IDiscordClient client = discordService.getClient();
        Optional<IDiscordClient.Image> image = avatar.map(s -> IDiscordClient.Image.forUrl("jpeg", s));
        client.changeAccountInfo(name, Optional.empty(), Optional.empty(), image);
    }

    private String executeClearCommand(IMessage m, OptionSet optionSet) {
        IDiscordClient client = discordService.getClient();
        IChannel c = client.getChannelByID(m.getChannel().getID());
        if (c != null) {
            c.getMessages().stream().filter(message -> message.getAuthor().getID()
                .equalsIgnoreCase(client.getOurUser().getID())).forEach(message -> {
                try {
                    tryDelete(message);
                } catch (MissingPermissionsException e) {
                    log.warn("No permission to perform action: {}", e.toString());
                } catch (HTTP429Exception e) {
                    log.warn("Too many requests. Slow down!", e);
                } catch (DiscordException e) {
                    log.warn("Discord exception", e);
                }
            });
        }
        return "";
    }

    @Retryable(include = {HTTP429Exception.class}, maxAttempts = 10, backoff = @Backoff(delay = 1000L))
    private void tryDelete(IMessage message) throws MissingPermissionsException, HTTP429Exception, DiscordException {
        message.delete();
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
