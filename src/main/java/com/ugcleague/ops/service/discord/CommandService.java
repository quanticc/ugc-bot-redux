package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.*;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.mina.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    private static final int LENGTH_LIMIT = 2000;

    private final LeagueProperties leagueProperties;
    private final DiscordService discordService;
    private final Set<Command> commandList = new ConcurrentHashSet<>();
    private final Gatekeeper gatekeeper = new Gatekeeper();

    private OptionSpec<String> helpNonOptionSpec;

    @Autowired
    public CommandService(LeagueProperties leagueProperties, DiscordService discordService) {
        this.leagueProperties = leagueProperties;
        this.discordService = discordService;
    }

    @PostConstruct
    private void configure() {
        OptionParser parser = new OptionParser();
        helpNonOptionSpec = parser.nonOptions("command to get help about").ofType(String.class);
        commandList.add(CommandBuilder.combined(".beep help").description("Show help about commands")
            .command(this::showCommandList).permission(0).parser(parser).build());
        discordService.subscribe(new IListener<MessageReceivedEvent>() {

            @Override
            public void handle(MessageReceivedEvent event) {
                IMessage m = event.getMessage();
                String content = m.getContent();
                Optional<Command> match = commandList.stream().filter(c -> c.matches(content)).findFirst();
                if (match.isPresent()) {
                    Command command = match.get();
                    if (canExecute(m.getAuthor(), command)) {
                        // cut away the "command" portion of the message
                        String args = content.substring(content.indexOf(command.getKey()) + command.getKey().length());
                        args = args.startsWith(" ") ? args.split(" ", 2)[1] : null;
                        // add gatekeeper logic
                        if (command.isQueued()) {
                            CommandJob job = new CommandJob(command, m, args);
                            String key = m.getAuthor().getID();
                            if (gatekeeper.getQueuedJobCount(key) > 0) {
                                answerPrivately(m, "**[Gatekeeper]** Please wait until your previous command finishes.");
                            } else {
                                answerPrivately(m, "**[Gatekeeper]** Your command was queued and will begin shortly.");
                                CompletableFuture<String> future = gatekeeper.queue(key, job);
                                future.thenAccept(response -> handleResponse(m, command, response));
                            }
                        } else {
                            try {
                                log.debug("User {} executing command {} with args: {}", format(m.getAuthor()),
                                    command.getKey(), args);
                                String response = command.execute(m, args);
                                handleResponse(m, command, response);
                                // ignore empty responses (no action)
                            } catch (OptionException e) {
                                log.warn("Invalid call: {}", e.toString());
                                printHelp(m, command);
                            }
                        }
                    } else {
                        log.debug("User {} has no permission to run {} (requires level {})", format(m.getAuthor()),
                            command.getKey(), command.getPermissionLevel());
                    }
                }
            }
        });
    }

    private void handleResponse(IMessage m, Command command, String response) {
        if (response == null) {
            // if the response is null, print the help
            printHelp(m, command);
        } else if (!response.isEmpty()) {
            int channelLevel = 0;
//            String channelId = m.getChannel().getID();
//            String props = leagueProperties.getDiscord().getChannels().get(channelId);
//            if (props != null) {
//                CommandPermission perm = CommandPermission.valueOf(props.toUpperCase());
//                if (perm != null) {
//                    channelLevel = perm.getLevel();
//                } else {
//                    log.warn("Channel {} has an invalid permission key: {}", channelId, props);
//                }
//            }
            if (command.getPermissionLevel() <= channelLevel) {
                answer(m, response);
            } else {
                answerPrivately(m, response);
            }
        }
    }

    private String format(IUser user) {
        return user.getName() + " " + user.toString();
    }

    private boolean canExecute(IUser user, Command command) {
        return getCommandPermission(user).getLevel() >= command.getPermissionLevel();
    }

    private CommandPermission getCommandPermission(IUser user) {
        if (discordService.isMaster(user)) {
            return CommandPermission.MASTER;
        } else if (discordService.hasSupportRole(user)) {
            return CommandPermission.SUPPORT;
        } else {
            return CommandPermission.NONE;
        }
    }

    private String showCommandList(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(helpNonOptionSpec);
        if (nonOptions.isEmpty()) {
            return "*Available Commands*\n" + commandList.stream()
                .filter(c -> canExecute(m.getAuthor(), c))
                .sorted(Comparator.naturalOrder())
                .map(c -> padRight("**" + c.getKey() + "**", 20) + "\t\t" + c.getDescription())
                .collect(Collectors.joining("\n"));
        } else {
            List<Command> requested = commandList.stream()
                .filter(c -> isRequested(nonOptions, c.getKey().substring(1)))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
            StringBuilder builder = new StringBuilder();
            for (Command command : requested) {
                appendHelp(builder, command);
            }
            return builder.toString();
        }
    }

    private boolean isRequested(List<String> nonOptions, String substring) {
        return nonOptions.contains(substring);
    }

    public String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

    public StringBuilder appendHelp(StringBuilder b, Command c) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            c.getParser().formatHelpWith(new CustomHelpFormatter(140, 5));
            c.getParser().printHelpOn(stream);
            b.append("• Help for **")
                .append(c.getKey()).append("**: ").append(c.getDescription()).append("\n")
                .append(new String(stream.toByteArray(), "UTF-8")).append("\n");
        } catch (Exception e) {
            b.append("Could not show help for **").append(c.getKey().substring(1)).append("**\n");
            log.warn("Could not show help: {}", e.toString());
        }
        return b;
    }

    /**
     * Sends help about a command to a user via PM.
     *
     * @param m the original message
     * @param c the command executed
     */
    public void printHelp(IMessage m, Command c) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            c.getParser().formatHelpWith(new CustomHelpFormatter(140, 5));
            c.getParser().printHelpOn(stream);
            discordService.privateMessage(m.getAuthor()).appendContent("• Help for **")
                .appendContent(c.getKey()).appendContent("**: ").appendContent(c.getDescription()).appendContent("\n")
                .appendContent(new String(stream.toByteArray(), "UTF-8")).send();
        } catch (Exception e) {
            log.warn("Could not show help: {}", e.toString());
        }
    }

    public void register(Command command) {
        log.info("Registering command: {}", command);
        commandList.add(command);
    }

    public void unregister(Command command) {
        log.info("Removing command: {}", command);
        commandList.remove(command);
    }

    public void answer(IMessage message, String answer) {
        if (answer.length() > LENGTH_LIMIT) {
            SplitMessage s = new SplitMessage(answer);
            s.split(LENGTH_LIMIT).forEach(str -> answer(message, str));
        } else {
            discordService.channelMessage(message.getChannel()).appendContent(answer).send();
        }
    }

    public void answerPrivately(IMessage message, String answer) {
        if (answer.length() > LENGTH_LIMIT) {
            SplitMessage s = new SplitMessage(answer);
            s.split(LENGTH_LIMIT).forEach(str -> answerPrivately(message, str));
        } else {
            try {
                discordService.privateMessage(message.getAuthor()).appendContent(answer).send();
            } catch (Exception e) {
                log.warn("Could not answer privately to user: {}. Response was: {}", e.toString(), answer);
            }
        }
    }
}
