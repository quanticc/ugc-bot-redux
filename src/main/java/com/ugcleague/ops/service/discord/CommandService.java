package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.command.CommandPermission;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    private static final int LENGTH_LIMIT = 2000;

    private final DiscordService discordService;
    private final Set<Command> commandList = new ConcurrentHashSet<>();

    @Autowired
    public CommandService(DiscordService discordService) {
        this.discordService = discordService;
    }

    @PostConstruct
    private void configure() {
        OptionParser parser = new OptionParser();
        parser.posixlyCorrect(true);
        commandList.add(CommandBuilder.equalsTo(".help").description("Show this help")
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
                        try {
                            log.debug("User {} executing command {} with args: {}", format(m.getAuthor()),
                                command.getKey(), args);
                            String response = command.execute(m, args);
                            if (response != null && !response.isEmpty()) {
                                if (command.getPermissionLevel() == 0) {
                                    // level 0 commands are output to the public
                                    answer(m, response);
                                } else {
                                    answerPrivately(m, response);
                                }
                            } else {
                                // if the response is null, print the help
                                printHelp(m, command);
                            }
                        } catch (OptionException e) {
                            log.warn("Invalid call: {}", e.toString());
                            printHelp(m, command);
                        }
                    } else {
                        log.debug("User {} has no permission to run {} (requires level {})", format(m.getAuthor()),
                            command.getKey(), command.getPermissionLevel());
                    }
                }
            }
        });
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
        return "*Available Commands*\n" + commandList.stream()
            .filter(c -> canExecute(m.getAuthor(), c))
            .map(c -> padRight("**" + c.getKey() + "**", 20) + "\t\t" + c.getDescription())
            .collect(Collectors.joining("\n"));
    }

    public String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

    /**
     * Sends help about a command to a user via PM.
     *
     * @param m the original message
     * @param c the command executed
     */
    public void printHelp(IMessage m, Command c) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            c.getParser().formatHelpWith(new BuiltinHelpFormatter(80, 2));
            c.getParser().printHelpOn(stream);
            discordService.privateMessage(m.getAuthor()).appendContent("Help for **")
                .appendContent(c.getKey()).appendContent("**\n```\n")
                .appendContent(new String(stream.toByteArray(), "UTF-8")).appendContent("\n```").send();
        } catch (Exception e) {
            log.warn("Could not show help: {}", e.toString());
        }
    }

    public void register(Command command) {
        commandList.add(command);
    }

    public void unregister(Command command) {
        commandList.remove(command);
    }

    public void answer(IMessage message, String answer) {
        if (answer.length() > LENGTH_LIMIT) {
            for (int i = 0; i < answer.length() / LENGTH_LIMIT; i++) {
                int end = Math.min(answer.length(), (i + 1) * LENGTH_LIMIT);
                answer(message, answer.substring(i * LENGTH_LIMIT, end));
            }
        } else {
            discordService.channelMessage(message.getChannel()).appendContent(answer).send();
        }
    }

    public void answerPrivately(IMessage message, String answer) {
        if (answer.length() > LENGTH_LIMIT) {
            for (int i = 0; i < answer.length() / LENGTH_LIMIT; i++) {
                int end = Math.min(answer.length(), (i + 1) * LENGTH_LIMIT);
                answerPrivately(message, answer.substring(i * LENGTH_LIMIT, end));
            }
        } else {
            try {
                discordService.privateMessage(message.getAuthor()).appendContent(answer).send();
            } catch (Exception e) {
                log.warn("Could not answer privately to user: {}. Response was: {}", e.toString(), answer);
            }
        }
    }
}
