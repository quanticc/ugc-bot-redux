package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.StatusWrapper;
import com.ugcleague.ops.service.util.GitProperties;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;
import org.springframework.xml.xpath.XPathOperations;
import org.w3c.dom.Element;
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.Image;

import javax.annotation.PostConstruct;
import javax.xml.transform.Source;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static com.ugcleague.ops.util.DateUtil.formatHuman;
import static java.util.Arrays.asList;

@Service
public class DiscordQueryService {

    private static final Logger log = LoggerFactory.getLogger(DiscordQueryService.class);

    private final CommandService commandService;
    private final DiscordService discordService;
    private final RestOperations restTemplate;
    private final XPathOperations xPathTemplate;

    private OptionSpec<String> profileNameSpec;
    private OptionSpec<String> profileAvatarSpec;
    private OptionSpec<String> profileGameSpec;
    private GitProperties gitProperties;
    private OptionSpec<Integer> rateNumberSpec;
    private OptionSpec<Integer> rateWaitSpec;
    private OptionSpec<Boolean> rateStatusSpec;
    private Command rateCommand;

    @Autowired
    public DiscordQueryService(CommandService commandService, DiscordService discordService,
                               RestOperations restTemplate, XPathOperations xPathTemplate) {
        this.commandService = commandService;
        this.discordService = discordService;
        this.restTemplate = restTemplate;
        this.xPathTemplate = xPathTemplate;
    }

    @PostConstruct
    private void configure() {
        commandService.register(CommandBuilder.equalsTo(".beep info")
            .description("Get Discord information about the bot").unrestricted().originReplies()
            .command(this::executeInfoCommand).build());
        commandService.register(CommandBuilder.equalsTo(".beep clear")
            .description("Remove all of bot's messages from this channel").unrestricted()
            .command(this::executeClearCommand).build());
        commandService.register(CommandBuilder.equalsTo(".beep boop")
            .description("Test command please ignore").unrestricted()
            .command(this::executePMCommand).build());
        commandService.register(CommandBuilder.equalsTo(".beep load")
            .description("Load more messages in this channel").master().experimental()
            .command((message, optionSet) -> {
                message.getChannel().getMessages().load(50);
                return "";
            }).build());
        commandService.register(CommandBuilder.equalsTo(".beep exit")
            .description("Exit discord").master().experimental()
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
        commandService.register(CommandBuilder.equalsTo(".beep gc")
            .description("Run GC").master().command((message, optionSet) -> {
                CompletableFuture.runAsync(System::gc);
                return "";
            }).build());
        commandService.register(CommandBuilder.equalsTo(".beep guilds")
            .description("Display the currently saved guilds").master().command((message, optionSet) -> {
                List<IGuild> guildList = discordService.getClient().getGuilds();
                StringBuilder builder = new StringBuilder();
                for (IGuild guild : guildList) {
                    builder.append(DiscordService.guildString(guild, discordService.getClient().getOurUser())).append("\n");
                }
                return builder.toString();
            }).build());
        commandService.register(CommandBuilder.equalsTo(".cat")
            .description("(=ↀωↀ=)✧").support().originReplies().command((message, optionSet) -> {
                String url = "http://thecatapi.com/api/images/get?format=xml";
                CompletableFuture.supplyAsync(() -> {
                    Source source = restTemplate.getForObject(url, Source.class);
                    List<String> images = xPathTemplate.evaluate("//image", source, (node, i) -> {
                        Element image = (Element) node;
                        return image.getElementsByTagName("url").item(0).getTextContent();
                    });
                    if (images != null && !images.isEmpty()) {
                        return images.get(0);
                    } else {
                        return "";
                    }
                }).thenAccept(s -> {
                    if (!s.isEmpty()) {
                        try {
                            discordService.sendMessage(message.getChannel(), s);
                        } catch (DiscordException | MissingPermissionsException | InterruptedException e) {
                            log.warn("Could not send cat response: {}", e.toString());
                        }
                    }
                });
                return "";
            }).build());
    }

    private void initRateTestCommand() {
        OptionParser parser = newParser();
        rateNumberSpec = parser.acceptsAll(asList("n", "number"), "number of messages to send")
            .withRequiredArg().ofType(Integer.class).defaultsTo(10);
        rateWaitSpec = parser.acceptsAll(asList("w", "wait"), "waiting milliseconds before each message")
            .withRequiredArg().ofType(Integer.class).defaultsTo(250);
        rateStatusSpec = parser.acceptsAll(asList("s", "status"), "display progress as status mode (successive edits)")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        rateCommand = CommandBuilder.startsWith(".test")
            .description("Test API rate limits").master().experimental().queued().parser(parser)
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
        OptionParser parser = newParser();
        profileNameSpec = parser.acceptsAll(asList("n", "name"), "change bot's name").withRequiredArg();
        profileAvatarSpec = parser.acceptsAll(asList("a", "avatar"), "change bot's avatar").withRequiredArg();
        profileGameSpec = parser.acceptsAll(asList("g", "game"), "change bot's game").withRequiredArg();
        commandService.register(CommandBuilder.startsWith(".beep profile")
            .description("Edit this bot's profile").master().parser(parser)
            .command(this::executeProfileCommand).build());
    }

    private String executeProfileCommand(IMessage message, OptionSet o) {
        if (o.has("?")) {
            return null;
        }
        Optional<String> name = Optional.ofNullable(o.valueOf(profileNameSpec));
        Optional<Image> avatar = Optional.ofNullable(o.valueOf(profileAvatarSpec)).map(s -> Image.forUrl("jpeg", s));
        Optional<String> game = Optional.ofNullable(o.valueOf(profileGameSpec));
        IDiscordClient client = discordService.getClient();
        client.updatePresence(client.getOurUser().getPresence().equals(Presences.IDLE), game);
        try {
            if (name.isPresent()) {
                discordService.changeUsername(name.get());
            }
        } catch (DiscordException | InterruptedException e) {
            log.warn("Could not change account info", e);
        }
        try {
            if (avatar.isPresent()) {
                discordService.changeAvatar(avatar.get());
            }
        } catch (DiscordException | InterruptedException e) {
            log.warn("Could not change account info", e);
        }
        return "";
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
