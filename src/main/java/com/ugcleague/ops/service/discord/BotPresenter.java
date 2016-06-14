package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.DiscordUtil;
import com.ugcleague.ops.service.util.GitProperties;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.*;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static com.ugcleague.ops.util.DateUtil.formatHuman;
import static java.util.Arrays.asList;

/**
 * Commands to control general operations of the Discord bot.
 * <ul>
 * <li>info</li>
 * <li>unsay</li>
 * <li>beep shutdown</li>
 * <li>beep logout</li>
 * <li>beep retry</li>
 * <li>beep gc</li>
 * <li>guilds</li>
 * <li>profile</li>
 * </ul>
 */
@Service
public class BotPresenter {

    private static final Logger log = LoggerFactory.getLogger(BotPresenter.class);

    private final CommandService commandService;
    private final DiscordService discordService;
    private final Executor taskExecutor;

    private OptionSpec<String> profileNameSpec;
    private OptionSpec<String> profileAvatarSpec;
    private OptionSpec<String> profileGameSpec;
    private GitProperties gitProperties;
    private OptionSpec<String> unsayNonOptionSpec;
    private OptionSpec<String> execNonOptionSpec;
    private OptionSpec<String> loadNonOptionSpec;

    @Autowired
    public BotPresenter(CommandService commandService, DiscordService discordService, Executor taskExecutor) {
        this.commandService = commandService;
        this.discordService = discordService;
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    private void configure() {
        commandService.register(CommandBuilder.equalsTo(".info")
            .description("Get Discord information about the bot").unrestricted().originReplies()
            .command(this::info).build());
        initLoadCommand();
        initUnsayCommand();
        initExecCommand();
        commandService.register(CommandBuilder.equalsTo(".beep shutdown")
            .description("Exit the application").master()
            .command((message, optionSet) -> {
                Runtime.getRuntime().exit(0);
                return "";
            }).build());
        commandService.register(CommandBuilder.equalsTo(".beep logout")
            .description("Logout from discord").master()
            .command((message, optionSet) -> {
                discordService.logout(false);
                return "";
            }).build());
        commandService.register(CommandBuilder.equalsTo(".beep retry")
            .description("Reconnect to Discord").master()
            .command((message, optionSet) -> {
                discordService.logout(true);
                return "";
            }).build());
        initProfileCommand();
        commandService.register(CommandBuilder.equalsTo(".beep gc")
            .description("Instruct JVM to run GC").master().command((message, optionSet) -> {
                CompletableFuture.runAsync(System::gc, taskExecutor);
                return "";
            }).build());
        commandService.register(CommandBuilder.equalsTo(".guilds")
            .description("Display the currently saved guilds").master().command((message, optionSet) -> {
                List<IGuild> guildList = discordService.getClient().getGuilds();
                StringBuilder builder = new StringBuilder();
                for (IGuild guild : guildList) {
                    builder.append(DiscordService.guildString(guild, discordService.getClient().getOurUser())).append("\n");
                }
                return builder.toString();
            }).build());
    }

    private void initLoadCommand() {
        OptionParser parser = newParser();
        loadNonOptionSpec = parser.nonOptions("# of new messages to load").ofType(String.class);
        commandService.register(CommandBuilder.anyMatch(".load")
            .description("Cache more messages from this channel").support()
            .parser(parser).command(this::load).build());
    }

    private String load(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(loadNonOptionSpec).stream().distinct().collect(Collectors.toList());
        if (optionSet.has("?")) {
            return null;
        }
        int count = 50;
        if (!nonOptions.isEmpty()) {
            String arg = nonOptions.get(0);
            if (arg.matches("[0-9]+")) {
                count = Math.max(1, Math.min(100, tryParseInt(arg)));
            }
        }
        IChannel channel = message.getChannel();
        boolean result = false;
        while (!result) {
            try {
                result = channel.getMessages().load(count);
            } catch (RateLimitException e) {
                try {
                    sleep(e.getRetryDelay(), e.getBucket());
                } catch (InterruptedException ex) {
                    log.warn("Interrupted while waiting for retry delay");
                    return "";
                }
            }
        }
        log.debug("Cached {} messages from channel {} ({})",
            count, channel.getName(), channel.getID());
        return ""; // silent
    }

    private int tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void sleep(long millis, String bucket) throws InterruptedException {
        log.info("Backing off for {} ms due to rate limits on {}", millis, bucket);
        Thread.sleep(Math.max(1, millis));
    }

    private void initUnsayCommand() {
        OptionParser parser = newParser();
        unsayNonOptionSpec = parser.nonOptions("# of messages to delete").ofType(String.class);
        commandService.register(CommandBuilder.anyMatch(".unsay")
            .description("Remove bot's last messages").unrestricted()
            .parser(parser).command(this::unsay).build());
    }

    private void initExecCommand() {
        OptionParser parser = newParser();
        execNonOptionSpec = parser.nonOptions("Command to run").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".exec")
            .description("Invoke a command line process").master().queued().parser(parser)
            .command(this::exec).build());
    }

    private String exec(IMessage message, OptionSet optionSet) {
        List<String> commands = optionSet.valuesOf(execNonOptionSpec);
        if (optionSet.has("?") || commands.isEmpty()) {
            return null;
        }
        ProcessBuilder builder = new ProcessBuilder(commands);
        //StringBuilder lines = new StringBuilder();
        String line;
        try {
            Process process = builder.start();
            try (BufferedReader input = newProcessReader(process)) {
                while ((line = input.readLine()) != null) {
                    log.debug("[{}] {}", builder.command().get(0), line);
                    //lines.append("[").append(builder.command().get(0)).append("] ").append(line).append("\n");
                }
            }
        } catch (IOException e) {
            log.warn("Failed to start process: {}", e.toString());
            return "Failed to run command";
        }
        return "Done";
    }

    private BufferedReader newProcessReader(Process p) {
        return new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.forName("UTF-8")));
    }

    private void initProfileCommand() {
        OptionParser parser = newParser();
        profileNameSpec = parser.acceptsAll(asList("n", "name"), "change bot's name").withRequiredArg();
        profileAvatarSpec = parser.acceptsAll(asList("a", "avatar"), "change bot's avatar").withRequiredArg();
        profileGameSpec = parser.acceptsAll(asList("g", "game"), "change bot's game").withRequiredArg();
        commandService.register(CommandBuilder.startsWith(".profile")
            .description("Edit this bot's profile").master().parser(parser)
            .command(this::editProfile).build());
    }

    private String editProfile(IMessage message, OptionSet o) {
        if (o.has("?")) {
            return null;
        }
        Optional<String> name = Optional.ofNullable(o.valueOf(profileNameSpec));
        Optional<Image> avatar = Optional.ofNullable(o.valueOf(profileAvatarSpec)).map(s -> Image.forUrl("jpeg", s));
        Optional<String> game = Optional.ofNullable(o.valueOf(profileGameSpec));
        IDiscordClient client = discordService.getClient();
        if (game.isPresent()) {
            client.changeStatus(Status.game(game.get()));
        }
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

    private String unsay(IMessage m, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(unsayNonOptionSpec);
        if (optionSet.has("?")) {
            return null;
        }
        int limit = 1;
        if (!nonOptions.isEmpty()) {
            String arg = nonOptions.get(0);
            if (arg.matches("[0-9]+")) {
                try {
                    limit = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    log.warn("{} tried to input {} as limit", DiscordUtil.toString(m.getAuthor()), arg);
                }
            }
        }
        deleteLastMessages(m, limit);
        return "";
    }

    private void deleteLastMessages(IMessage m, int limit) {
        int maxDepth = 1000;
        limit = Math.min(maxDepth, limit);
        IDiscordClient client = discordService.getClient();
        IChannel c = client.getChannelByID(m.getChannel().getID());
        if (c != null) {
            log.info("Preparing to delete last {} bot messages to channel {}", limit, c.getName());
            int cap = c.getMessages().getCacheCapacity();
            c.getMessages().setCacheCapacity(MessageList.UNLIMITED_CAPACITY);
            int deleted = 0;
            int i = 0;
            List<IMessage> toDelete = new ArrayList<>();
            while (deleted < limit && i < maxDepth) {
                try {
                    IMessage msg = c.getMessages().get(i++);
                    if (msg.getAuthor().equals(client.getOurUser())) {
                        toDelete.add(msg);
                        deleted++;
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    // we reached the end apparently
                    log.warn("Could not retrieve messages to delete", e);
                    break;
                }
            }
            log.info("Searched through {} messages", i);
            if (toDelete.isEmpty()) {
                log.info("No messages to delete");
            } else {
                log.info("Preparing to delete {} messages from {}", toDelete.size(), DiscordUtil.toString(c));
                for (int x = 0; x < toDelete.size() / 100; x++) {
                    List<IMessage> subList = toDelete.subList(x * 100, Math.min(toDelete.size(), (x + 1) * 100));
                    RequestBuffer.request(() -> {
                        try {
                            c.getMessages().bulkDelete(subList);
                        } catch (MissingPermissionsException | DiscordException e) {
                            log.warn("Failed to delete message", e);
                        }
                        return null;
                    });
                }
            }
            c.getMessages().setCacheCapacity(cap);
        }
    }

    private String info(IMessage m, OptionSet o) {
        if (gitProperties == null) {
            gitProperties = new GitProperties();
        }
        StringBuilder builder = new StringBuilder();
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long uptime = rb.getUptime();
        String version = gitProperties.getProperty("git.commit.id.describe", "0.x");
        builder.append("Hello! I'm here to help with **UGC support**.\n")
            .append("Running `v").append(version).append("` with **Discord4J** library `v")
            .append(Discord4J.VERSION).append("`.\nUptime: ").append(formatHuman(Duration.ofMillis(uptime)))
            .append("\nCheck the available commands with `.help`");
        return builder.toString();
    }
}
