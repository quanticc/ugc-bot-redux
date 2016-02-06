package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.domain.RemoteFile;
import com.ugcleague.ops.service.GameServerService;
import com.ugcleague.ops.service.SyncGroupService;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.util.FileShareTask;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.ugcleague.ops.util.DateUtil.formatHuman;
import static com.ugcleague.ops.util.DateUtil.formatRelativeBetweenNowAnd;
import static com.ugcleague.ops.util.Util.humanizeBytes;
import static com.ugcleague.ops.util.Util.padRight;
import static java.util.Arrays.asList;

/**
 * Collection of operations to retrieve files from a game server's FTP server. Exposes commands to search, list, filter
 * and retrieve certain file types.
 */
@Service
@Transactional
public class GameServerFtpService {

    private static final Logger log = LoggerFactory.getLogger(GameServerFtpService.class);

    private final CommandService commandService;
    private final GameServerService gameServerService;
    private final SyncGroupService syncGroupService;

    private OptionParser parser;
    private OptionSpec<String> getNonOptionSpec;
    private OptionSpec<String> getServerSpec;
    private OptionSpec<String> getFilenameFilterSpec;
    private OptionSpec<String> getAfterFilterSpec;
    private OptionSpec<String> getBeforeFilterSpec;
    private Command getLogsCommand;
    private Command getStvCommand;
    private OptionSpec<Boolean> getExactDateSpec;

    @Autowired
    public GameServerFtpService(CommandService commandService, GameServerService gameServerService, SyncGroupService syncGroupService) {
        this.commandService = commandService;
        this.gameServerService = gameServerService;
        this.syncGroupService = syncGroupService;
    }

    @PostConstruct
    private void configure() {
        // .get <stv|logs> -s <key> [--filter <key>] [--after <time>] [--before <time>] <file1> [file2...]
        initCommonParser();
        initGetLogsCommand();
        initGetStvCommand();
    }

    private void initCommonParser() {
        parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        getNonOptionSpec = parser.nonOptions("exact filenames to download from the server, separated by spaces").ofType(String.class);
        getServerSpec = parser.acceptsAll(asList("s", "server"), "a GS server identifier (dal5, chi3), name (\"Miami 4\" **with** quotes) or IP address")
            .withRequiredArg().required();
        getFilenameFilterSpec = parser.acceptsAll(asList("f", "filename"), "only display files containing this filename").withRequiredArg();
        getAfterFilterSpec = parser.acceptsAll(asList("a", "after"), "only display files last modified after this date").withRequiredArg();
        getBeforeFilterSpec = parser.acceptsAll(asList("b", "before"), "only display files last modified before this date").withRequiredArg();
        getExactDateSpec = parser.accepts("absolute-date", "display last modified date in absolute terms")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
    }

    private void initGetLogsCommand() {
        getLogsCommand = commandService.register(CommandBuilder.combined(".get logs")
            .description("List or retrieve log files from a game server").permission("support").permissionReplies().mention()
            .parser(parser).command(this::executeGetLogs).queued().persistStatus().build());
    }

    private void initGetStvCommand() {
        getStvCommand = commandService.register(CommandBuilder.combined(".get stv")
            .description("List or retrieve SourceTV demos from a game server").permission("support").permissionReplies().mention()
            .parser(parser).command(this::executeGetStv).queued().persistStatus().build());
    }

    private String executeGetLogs(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?") || !optionSet.hasOptions()) {
            return null;
        }
        return commonGet(getLogsCommand, message, optionSet, "/orangebox/tf/logs", ".log");
    }

    private String executeGetStv(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?") || !optionSet.hasOptions()) {
            return null;
        }
        return commonGet(getStvCommand, message, optionSet, "/orangebox/tf", ".dem");
    }

    private String commonGet(Command command, IMessage message, OptionSet optionSet, String dir, String endsWith) {
        String key = optionSet.valueOf(getServerSpec); // can match multiple, but we will only use the first
        List<GameServer> servers = gameServerService.findServers(key);
        StringBuilder response = new StringBuilder();
        if (servers.isEmpty()) {
            return "No servers found matching **" + key + "**";
        } else if (servers.size() > 1) {
            response.append("**").append(key).append("** matched multiple servers, using the first one only\n");
        }
        GameServer server = servers.get(0);
        response.append("Files from **").append(server.getShortName())
            .append("** server (").append(server.getAddress()).append(")\n");
        // determine if we are listing or downloading files
        List<String> files = optionSet.valuesOf(getNonOptionSpec);
        Optional<String> filename = Optional.ofNullable(optionSet.valueOf(getFilenameFilterSpec));
        Optional<String> after = Optional.ofNullable(optionSet.valueOf(getAfterFilterSpec));
        Optional<String> before = Optional.ofNullable(optionSet.valueOf(getBeforeFilterSpec));
        if (files.isEmpty()) {
            // List mode
            response.append(filename.map(f -> "Files containing **" + f + "**").orElse("*Use `-f <filename>` to filter by filename*")).append("\n");
            // parse after/before filters
            Optional<ZonedDateTime> afterTime = after.map(this::parseTimeDate);
            Optional<ZonedDateTime> beforeTime = before.map(this::parseTimeDate);
            if (afterTime.isPresent() || beforeTime.isPresent()) {
                if (afterTime.isPresent() && beforeTime.isPresent()) {
                    String t1 = afterTime.get().toString();
                    String t2 = beforeTime.get().toString();
                    if (afterTime.get().isAfter(beforeTime.get())) {
                        response.append("You enter the ranges wrong so I'm fixing your request for you.\n");
                        t1 = beforeTime.get().toString();
                        t2 = afterTime.get().toString();
                    }
                    response.append("Showing files modified between ").append(t1).append(" and ").append(t2).append("\n");
                } else {
                    if (afterTime.isPresent()) {
                        response.append("Showing files modified after ").append(afterTime.get()).append("\n");
                    } else {
                        response.append("Showing files modified before ").append(beforeTime.get()).append("\n");
                    }
                }
            } else {
                response.append("*Use `--after <date>` and `--before <date>` to filter by modified date*\n");
            }
            // construct the filter
            Predicate<RemoteFile> filter = remoteFile ->
                remoteFile.getFilename().endsWith(endsWith)
                    && (!filename.isPresent() || remoteFile.getFilename().contains(filename.get()))
                    && (!after.isPresent() || isAfter(afterTime.get(), remoteFile.getModified()))
                    && (!before.isPresent() || isBefore(beforeTime.get(), remoteFile.getModified()));
            List<RemoteFile> remoteFiles = syncGroupService.getFileList(server, dir, filter);
            if (remoteFiles.isEmpty()) {
                response.append("No files found with the given filters");
            } else {
                boolean absolute = optionSet.has(getExactDateSpec) ? optionSet.valueOf(getExactDateSpec) : false;
                int width = Math.max(remoteFiles.size() > 0 ? remoteFiles.get(0).getFilename().length() : 25, 25);
                response.append("*To download files, add their exact names to the end of the command*\n")
                    .append(padRight("*Filename*", width)).append("\t\t*Size*\t\t\t*Last modified*\n");
                for (RemoteFile file : remoteFiles) {
                    response.append(String.format("**%s**\t\t%s\t\t%s\n", file.getFilename(),
                        humanizeBytes(file.getSize()), absolute ? file.getModified() :
                            formatRelativeBetweenNowAnd(file.getModified().toInstant())));
                }
            }
            commandService.deleteStatusFrom(message);
        } else {
            // filters will be ignored
            if (filename.isPresent() || after.isPresent() || before.isPresent()) {
                response.append("Filters are ignored when downloading files\n");
            }
            // TODO: handle --force download mode
            Predicate<RemoteFile> filter = remoteFile -> remoteFile.getFilename().endsWith(endsWith)
                && files.stream().anyMatch(s -> remoteFile.getFilename().contains(s))
                && remoteFile.getSharedUrl() == null;
            syncGroupService.shareToDropbox(server, dir, filter)
                .thenAccept(t -> handleGetResult(t, message, command));
            commandService.statusReplyFrom(message, command, "Files are being downloaded in the background, please wait...");
        }
        return response.toString();
    }

    private void handleGetResult(FileShareTask shareTask, IMessage message, Command command) {
        // let the user know of the shared urls
        StringBuilder response = new StringBuilder();
        Optional<String> optionalUrl = shareTask.getBatchSharedUrl();
        long elapsed = shareTask.getEnd() == 0 ? System.currentTimeMillis() - shareTask.getStart() : shareTask.getEnd() - shareTask.getStart();
        commandService.statusReplyFrom(message, command, "Done. Operation took " + formatHuman(Duration.ofMillis(elapsed)) + "\n");
        List<RemoteFile> successful = shareTask.getSuccessful();
        List<RemoteFile> failed = shareTask.getRequested().stream()
            .filter(r -> !successful.contains(r))
            .collect(Collectors.toList());
        if (!failed.isEmpty()) {
            response.append("Failed: ").append(failed.stream()
                .map(RemoteFile::getFilename).collect(Collectors.joining(", ")))
                .append("\n");
        }
        if (optionalUrl.isPresent()) {
            response.append("Packed all files into: ").append(optionalUrl.get()).append("\n");
        } else {
            response.append(successful.stream()
                .filter(f -> f.getSharedUrl() != null)
                .map(syncGroupService::saveRemoteFile)
                .map(RemoteFile::getSharedUrl)
                .collect(Collectors.joining("\n")))
                .append("\n");
        }
        commandService.tryReplyFrom(message, command, response.toString());
        //commandService.deleteStatusFrom(message); // remove status
    }

    private ZonedDateTime parseTimeDate(String s) {
        List<Date> parsed = new PrettyTimeParser().parse(s); // never null, can be empty
        if (!parsed.isEmpty()) {
            Date first = parsed.get(0);
            return ZonedDateTime.ofInstant(first.toInstant(), ZoneId.systemDefault());
        }
        log.warn("Could not parse a valid date from input: {}", s);
        return null;
    }

    private boolean isBefore(ZonedDateTime threshold, ZonedDateTime actual) {
        return actual != null && (threshold == null || actual.isBefore(threshold));
    }

    private boolean isAfter(ZonedDateTime threshold, ZonedDateTime actual) {
        return actual != null && (threshold == null || actual.isAfter(threshold));
    }
}
