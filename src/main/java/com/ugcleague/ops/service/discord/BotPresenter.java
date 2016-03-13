package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.util.GitProperties;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.Presences;
import sx.blah.discord.util.Image;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.Charset;
import java.time.Duration;
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
        initExamplesCommand();
    }

    private void initExamplesCommand() {
        commandService.register(CommandBuilder.equalsTo(".examples")
            .description("Show a list of examples").support().privateReplies()
            .command((m, o) -> "**TF2/Dota Support Pings**\n" +
                "• Subscribe to TF2 support PMs only from 10am to 10pm: `.sub on 10am 10pm`\n" +
                "• Subscribe to Dota support PMs: `.sub to dota`\n" +
                "• Remove subscription with `.unsub` or `.unsub from dota`\n" +
                "• Disable it only during a period of time using `.sub off midnight 9am`\n" +
                "• Get more details about these commands using `.help sub`\n" +
                "**Logs/SourceTV file search and download**\n" +
                "• Find demos from a server matching a name over a certain time period: " +
                "`.get stv from chi5 since \"10 hours ago\" like \"pl_upward\"`\n" +
                "• Can also be used for .log files with `.get logs from dal4 since \"last week\"`\n" +
                "• Use the response to get an exact filename and download it: " +
                "`.get stv from chi2 auto-20160307-0029-pl_badwater_pro_v9.dem`\n" +
                "**Utility commands**\n" +
                "• Get info of a member of this server: `.userinfo beepboop`\n" +
                "• Get info from a SteamID or URL: `.steam [U:1:51827133]`\n" +
                "• Check status command output against UGC rosters: `.check <paste status output>`\n" +
                "• Run a command on a UGC game server: `.rcon \"changelevel cp_steel\" mia6`\n" +
                "• List of available servers: `.servers`\n").build());
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
                count = Math.max(1, Math.min(100, Integer.parseInt(arg)));
            }
        }
        IChannel channel = message.getChannel();
        boolean result = channel.getMessages().load(count);
        log.debug("Could load {} messages from channel {} ({}) into cache: {}",
            count, channel.getName(), channel.getID(), result);
        return ""; // silent
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

    private String unsay(IMessage m, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(unsayNonOptionSpec);
        if (optionSet.has("?")) {
            return null;
        }
        long limit = 1;
        if (!nonOptions.isEmpty()) {
            String arg = nonOptions.get(0);
            if (arg.matches("[0-9]+")) {
                limit = Long.parseLong(arg);
            }
        }
        deleteLastMessages(m, limit);
        return "";
    }

    private void deleteLastMessages(IMessage m, long limit) {
        IDiscordClient client = discordService.getClient();
        IChannel c = client.getChannelByID(m.getChannel().getID());
        if (c != null) {
            c.getMessages().stream().filter(message -> message.getAuthor().getID()
                .equalsIgnoreCase(client.getOurUser().getID())).limit(limit).forEach(message -> {
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
            .append("\nCheck the available commands with `.help` or `.examples`");
        return builder.toString();
    }
}
