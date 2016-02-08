package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.*;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import com.ugcleague.ops.service.discord.util.StatusWrapper;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.handle.EventSubscriber;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.ugcleague.ops.util.Util.padRight;
import static java.util.Arrays.asList;

@Service
@Transactional
public class CommandService implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    private static final int LENGTH_LIMIT = 2000;

    private final DiscordService discordService;
    private final PermissionService permissionService;
    private final Set<Command> commandList = new ConcurrentSkipListSet<>();
    private final Gatekeeper gatekeeper = new Gatekeeper();
    private final Map<String, IMessage> invokerToStatusMap = new ConcurrentHashMap<>();

    private OptionSpec<String> helpNonOptionSpec;
    private OptionSpec<Boolean> helpFullSpec;

    @Autowired
    public CommandService(DiscordService discordService, PermissionService permissionService) {
        this.discordService = discordService;
        this.permissionService = permissionService;
    }

    @PostConstruct
    private void configure() {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        helpNonOptionSpec = parser.nonOptions("command to get help about").ofType(String.class);
        helpFullSpec = parser.acceptsAll(asList("f", "full"), "display all commands in a list with their description")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        commandList.add(CommandBuilder.combined(".beep help").description("Show help about commands")
            .command(this::showCommandList).unrestricted().parser(parser).build());
        discordService.subscribe(this);
    }

    /**
     * Retrieve a minimal parser with accepts "-?", "-h" or "--help", to use for help
     *
     * @return a new OptionParser
     */
    public static OptionParser newParser() {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        return parser;
    }

    // Help command definitions
    /////////////////////////////////

    private String showCommandList(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(helpNonOptionSpec);
        if (o.has("?")) {
            return null;
        } else if (nonOptions.isEmpty()) {
            if (o.has(helpFullSpec) && o.valueOf(helpFullSpec)) {
                return "*Commands available to you*\n" + commandList.stream()
                    .filter(c -> canExecute(c, m.getAuthor(), m.getChannel()))
                    .sorted(Comparator.naturalOrder())
                    .map(c -> padRight("**" + c.getKey() + "**", 20) + "\t\t" + c.getDescription())
                    .collect(Collectors.joining("\n"));
            } else {
                return "*Commands available to you:* " + commandList.stream()
                    .filter(c -> canExecute(c, m.getAuthor(), m.getChannel()))
                    .sorted(Comparator.naturalOrder())
                    .map(Command::getKey)
                    .collect(Collectors.joining(", "));
            }
        } else {
            List<Command> requested = commandList.stream()
                .filter(c -> isRequested(nonOptions, c.getKey().substring(1)))
                .filter(c -> canExecute(c, m.getAuthor(), m.getChannel()))
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

    public StringBuilder appendHelp(StringBuilder b, Command c) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            c.getParser().formatHelpWith(new CustomHelpFormatter(140, 5));
            c.getParser().printHelpOn(stream);
            b.append(String.format("• Help for **%s**: %s%s\n", c.getKey(), c.getDescription(),
                c.getPermission() != CommandPermission.NONE ? " (requires `" + c.getPermission() + "` permission)" : ""))
                .append(new String(stream.toByteArray(), "UTF-8")).append("\n");
        } catch (Exception e) {
            b.append("Could not show help for **").append(c.getKey().substring(1)).append("**\n");
            log.warn("Could not show help: {}", e.toString());
        }
        return b;
    }

    // Discord event subscription
    //////////////////////////////////////////////

    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent event) {
        IMessage m = event.getMessage();
        String content = m.getContent();
        Optional<Command> match = commandList.stream().filter(c -> c.matches(content)).findFirst();
        if (match.isPresent()) {
            Command command = match.get();
            if (canExecute(command, m.getAuthor(), m.getChannel())) {
                // cut away the "command" portion of the message
                String args = content.substring(content.indexOf(command.getKey()) + command.getKey().length());
                args = args.startsWith(" ") ? args.split(" ", 2)[1] : null;
                // add gatekeeper logic
                log.debug("User {} executing command {} with args: {}", format(m.getAuthor()),
                    command.getKey(), args);
                if (command.isQueued()) {
                    CommandJob job = new CommandJob(command, m, args);
                    String key = m.getAuthor().getID();
                    if (gatekeeper.getQueuedJobCount(key) > 0) {
                        tryReplyFrom(m, command, "Please wait until your previous command finishes running");
                    } else {
                        statusReplyFrom(m, command, "Your command will be executed shortly...");
                        CompletableFuture<String> future = gatekeeper.queue(key, job);
                        // handle response and then delete status command
                        future.thenAccept(response -> handleResponse(m, command, response))
                            .thenRun(() -> statusReplyFrom(m, command, (String) null));
                    }
                } else {
                    try {
                        String response = command.execute(m, args);
                        handleResponse(m, command, response);
                        // ignore empty responses (no action)
                    } catch (OptionException e) {
                        log.warn("Invalid call: {}", e.toString());
                        helpReplyFrom(m, command, e.getMessage());
                    }
                    invokerToStatusMap.remove(m.getID());
                }
            } else {
                // fail silently
                log.debug("User {} has no permission to run {} (requires {})", format(m.getAuthor()),
                    command.getKey(), command.getPermission());
            }
        } else if (m.getChannel().isPrivate() && !discordService.isOwnUser(m.getAuthor())
            && (m.getContent().startsWith(".") || m.getContent().startsWith("!")
            || m.getContent().contains("help") || m.getContent().contains("info"))) {
            log.debug("User {} might be asking privately for help: {}", format(m.getAuthor()), m.getContent());
            showCommandList(m, newParser().parse());
        }
    }

    private String format(IUser user) {
        return user.getName() + " " + user.toString();
    }

    private boolean canExecute(Command command, IUser user, IChannel channel) {
        return permissionService.canExecute(command, user, channel);
    }

    // Higher level output control
    ////////////////////////////////////////

    private void handleResponse(IMessage message, Command command, String response) {
        if (response == null) {
            helpReplyFrom(message, command); // show help on null responses
        } else if (!response.isEmpty()) {
            tryReplyFrom(message, command, response);
        } // empty responses fall through silently
    }

    public void helpReplyFrom(IMessage message, Command command) {
        helpReplyFrom(message, command, null);
    }

    public void helpReplyFrom(IMessage message, Command command, String comment) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            command.getParser().formatHelpWith(new CustomHelpFormatter(140, 5));
            command.getParser().printHelpOn(stream);
            StringBuilder response = new StringBuilder();
            if (comment != null) {
                response.append(comment).append("\n");
            }
            response.append(String.format("• Help for **%s**: %s%s%s\n", command.getKey(), command.getDescription(),
                command.getPermission() != CommandPermission.NONE ? " (requires `" + command.getPermission() + "` permission)" : "",
                command.isExperimental() ? " -- Warning, this command is **experimental** and not well tested yet." : ""))
                .append(new String(stream.toByteArray(), "UTF-8"));
            replyFrom(message, command, response.toString());
        } catch (Exception e) {
            log.warn("Could not show help: {}", e.toString());
        }
    }

    public void statusReplyFrom(IMessage message, Command command, StatusWrapper status) {
        statusReplyFrom(message, command, status.toString());
    }

    public void statusReplyFrom(IMessage message, Command command, String response) {
        IMessage statusMessage = invokerToStatusMap.get(message.getID());
        if (response == null) {
            if (statusMessage != null && !command.isPersistStatus()) {
                tryDelete(statusMessage);
                invokerToStatusMap.remove(message.getID());
                // commands with persisted status will be purged if not updated after a while
            }
        } else if (response.length() < LENGTH_LIMIT) {
            if (statusMessage == null) {
                try {
                    IMessage status = tryReplyFrom(message, command, response).get();
                    invokerToStatusMap.put(message.getID(), status);
                } catch (InterruptedException | ExecutionException e) {
                    log.warn("Interrupted wait for the initial status message");
                }
            } else {
                tryEdit(statusMessage, response);
            }
        } // ignore longer "status" responses
    }

    public void deleteStatusFrom(IMessage message) {
        // ignores persist-status setting
        IMessage statusMessage = invokerToStatusMap.get(message.getID());
        if (statusMessage != null) {
            tryDelete(statusMessage);
        }
        invokerToStatusMap.remove(message.getID());
    }

    @Scheduled(cron = "0 0 * * * *")
    void purgeStatuses() {
        // purge messages not updated for 1 hour
        invokerToStatusMap.values().removeIf(m -> LocalDateTime.now().minusHours(1).isAfter(m.getTimestamp()));
    }

    private void tryEdit(IMessage message, String response) {
        try {
            discordService.editMessage(message, response);
        } catch (InterruptedException | DiscordException | MissingPermissionsException e) {
            log.warn("Could not edit message: {}", e.toString());
        }
    }

    private void tryDelete(IMessage message) {
        try {
            discordService.deleteMessage(message);
        } catch (InterruptedException | DiscordException | MissingPermissionsException e) {
            log.warn("Could not delete message: {}", e.toString());
        }
    }

    public CompletableFuture<IMessage> tryReplyFrom(IMessage message, Command command, String response) {
        try {
            return replyFrom(message, command, response);
        } catch (InterruptedException | MissingPermissionsException | DiscordException e) {
            log.warn("Could not reply to user: {}", e.toString());
        }
        return null;
    }

    public CompletableFuture<IMessage> replyFrom(IMessage message, Command command, String response) throws InterruptedException, DiscordException, MissingPermissionsException {
        return commonReply(message, command, response, null);
    }

    public void fileReplyFrom(IMessage message, Command command, File file) throws InterruptedException, DiscordException, MissingPermissionsException {
        commonReply(message, command, null, file);
    }

    private CompletableFuture<IMessage> commonReply(IMessage message, Command command, String response, File file) throws InterruptedException, DiscordException, MissingPermissionsException {
        ReplyMode replyMode = command.getReplyMode();
        if (replyMode == ReplyMode.PRIVATE) {
            // always to private - so ignore all permission calculations
            return answerPrivately(message, response, file);
        } else if (replyMode == ReplyMode.ORIGIN) {
            // use the same channel as invocation
            return answer(message, response, command.isMention(), file);
        } else if (replyMode == ReplyMode.WITH_PERMISSION) {
            // the channel must have the needed permission, otherwise fallback to a private message
            if (permissionService.canDisplayResult(command, message.getChannel())) {
                return answer(message, response, command.isMention(), file);
            } else {
                return answerPrivately(message, response, file);
            }
        } else {
            log.warn("This command ({}) has an invalid reply-mode: {}", command.getKey(), command.getReplyMode());
            return answerPrivately(message, response, null);
        }
    }

    // Lower level output control
    ////////////////////////////////////////

    public CompletableFuture<IMessage> answerToChannel(IChannel channel, String answer) throws InterruptedException, DiscordException, MissingPermissionsException {
        return discordService.sendMessage(channel, answer);
    }

    private CompletableFuture<IMessage> answer(IMessage message, String answer, boolean mention, File file) throws InterruptedException, DiscordException, MissingPermissionsException {
        if (file != null) {
            try {
                discordService.sendFile(message.getChannel(), file);
            } catch (InterruptedException | IOException | MissingPermissionsException | DiscordException e) {
                log.warn("Could not send file to user {} in channel {}: {}",
                    message.getAuthor(), message.getChannel(), e.toString());
            }
            return null;
        } else {
            return discordService.sendMessage(message.getChannel(), (mention ? message.getAuthor().mention() + " " : "") + answer);
        }
    }

    private CompletableFuture<IMessage> answerPrivately(IMessage message, String answer, File file)
        throws InterruptedException, DiscordException, MissingPermissionsException {

        if (file != null) {
            try {
                return discordService.sendFilePrivately(message.getAuthor(), file);
            } catch (Exception e) {
                log.warn("Could not send file to user {} in channel {}: {}",
                    message.getAuthor(), message.getChannel(), e.toString());
                return null;
            }
        } else {
            return discordService.sendPrivateMessage(message.getAuthor(), answer);
        }
    }

    // Command registering operations
    /////////////////////////////////////

    public Command register(Command command) {
        String description = asList(opt(command.getMatchType(), "", " match", MatchType.STARTS_WITH),
            opt(command.getReplyMode(), "", " replies" +
                opt(command.isMention(), " with mention", "", false, true).orElse(""), null),
            opt(command.isQueued(), "queued", "", false, true),
            opt(command.isPersistStatus(), "persisted status", "", false, true),
            opt(command.isExperimental(), "experimental", "", false, true))
            .stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.joining(", "));
        log.info("Command {} [{}] {}", padRight(command.getKey(), 20),
            command.getPermission().name().charAt(0), description);
        commandList.add(command);
        return command;
    }

    private <T> Optional<String> opt(T value, String prefix, String suffix, T ignoredValue) {
        return Optional.ofNullable(value)
            .map(v -> v.equals(ignoredValue) ? null : v)
            .map(v -> prefix + v.toString() + suffix);
    }

    private <T> Optional<String> opt(T value, String prefix, String suffix, T ignoredValue, T hiddenValue) {
        return Optional.ofNullable(value)
            .map(v -> v.equals(ignoredValue) ? null : v)
            .map(v -> prefix + (v.equals(hiddenValue) ? "" : v.toString()) + suffix);
    }

    public void unregister(Command command) {
        log.info("Removing {}", command.getKey());
        commandList.remove(command);
    }
}
