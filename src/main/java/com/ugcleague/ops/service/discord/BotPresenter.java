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
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static com.ugcleague.ops.util.DateUtil.formatHuman;
import static java.util.Arrays.asList;

/**
 * Commands to control general operations of the Discord bot.
 * <ul>
 * <li>beep info</li>
 * <li>beep unsay</li>
 * <li>beep exit</li>
 * <li>beep retry</li>
 * <li>beep gc</li>
 * <li>beep guilds</li>
 * <li>beep profile</li>
 * </ul>
 */
@Service
public class BotPresenter {

    private static final Logger log = LoggerFactory.getLogger(BotPresenter.class);

    private final CommandService commandService;
    private final DiscordService discordService;

    private OptionSpec<String> profileNameSpec;
    private OptionSpec<String> profileAvatarSpec;
    private OptionSpec<String> profileGameSpec;
    private GitProperties gitProperties;
    private OptionSpec<String> unsayNonOptionSpec;

    @Autowired
    public BotPresenter(CommandService commandService, DiscordService discordService) {
        this.commandService = commandService;
        this.discordService = discordService;
    }

    @PostConstruct
    private void configure() {
        commandService.register(CommandBuilder.equalsTo(".beep info")
            .description("Get Discord information about the bot").unrestricted().originReplies()
            .command(this::info).build());
        OptionParser parser = newParser();
        unsayNonOptionSpec = parser.nonOptions("# of messages to delete").ofType(String.class);
        commandService.register(CommandBuilder.anyMatch(".beep unsay")
            .description("Remove bot's last messages").unrestricted()
            .parser(parser).command(this::unsay).build());
        commandService.register(CommandBuilder.equalsTo(".beep exit")
            .description("Exit discord").master()
            .command((message, optionSet) -> {
                discordService.logout(false);
                return "";
            }).build());
        commandService.register(CommandBuilder.equalsTo(".beep retry")
            .description("Exit discord").master()
            .command((message, optionSet) -> {
                discordService.logout(true);
                return "";
            }).build());
        initProfileCommand();
        commandService.register(CommandBuilder.equalsTo(".beep gc")
            .description("Instruct JVM to run GC").master().command((message, optionSet) -> {
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
    }

    private void initProfileCommand() {
        OptionParser parser = newParser();
        profileNameSpec = parser.acceptsAll(asList("n", "name"), "change bot's name").withRequiredArg();
        profileAvatarSpec = parser.acceptsAll(asList("a", "avatar"), "change bot's avatar").withRequiredArg();
        profileGameSpec = parser.acceptsAll(asList("g", "game"), "change bot's game").withRequiredArg();
        commandService.register(CommandBuilder.startsWith(".beep profile")
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
            c.getMessages().stream().limit(limit).filter(message -> message.getAuthor().getID()
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
            .append("\nCheck the available commands with `.beep help`");
        return builder.toString();
    }
}