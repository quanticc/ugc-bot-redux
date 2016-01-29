package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.domain.ServerFile;
import com.ugcleague.ops.domain.SyncGroup;
import com.ugcleague.ops.domain.enumeration.FileGroupType;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.GameServerService;
import com.ugcleague.ops.service.ServerFileService;
import com.ugcleague.ops.service.SyncGroupService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.obj.IMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

@Service
@Transactional
public class SyncQueryService {

    private static final Logger log = LoggerFactory.getLogger(SyncQueryService.class);

    private final CommandService commandService;
    private final SyncGroupService syncGroupService;
    private final ServerFileService serverFileService;
    private final DiscordService discordService;
    private final GameServerService gameServerService;

    private OptionSpec<FileGroupType> addTypeSpec;
    private OptionSpec<String> addLocalSpec;
    private OptionSpec<String> addRemoteSpec;
    private OptionSpec<String> infoNonOptionSpec;
    private OptionSpec<String> refreshLocalSpec;
    private OptionSpec<String> refreshRemoteSpec;

    @Autowired
    public SyncQueryService(CommandService commandService, SyncGroupService syncGroupService,
                            ServerFileService serverFileService, DiscordService discordService,
                            GameServerService gameServerService) {
        this.commandService = commandService;
        this.syncGroupService = syncGroupService;
        this.serverFileService = serverFileService;
        this.discordService = discordService;
        this.gameServerService = gameServerService;
    }

    @Autowired
    private void configure() {
        initSyncListCommand();
        initSyncAddCommand();
        initSyncInfoCommand();
        initSyncEditCommand(); // TODO
        initSyncDeleteCommand(); // TODO
        initSyncRefreshCommand();
    }

    private void initSyncListCommand() {
        // .sync list
        commandService.register(CommandBuilder.equalsTo(".sync list")
            .description("List the file synchronization groups [A]").permission("support")
            .command((message, o) -> {
                StringBuilder builder = new StringBuilder();
                for (SyncGroup group : syncGroupService.findAll()) {
                    builder.append(String.format("[%d] Local: **%s** --> Remote: **%s** [%s]\n",
                        group.getId(), group.getLocalDir(), group.getRemoteDir(), group.getKind().toString()
                    ));
                }
                return builder.toString();
            }).build());
    }

    private void initSyncAddCommand() {
        // .sync add --local <local_dir> --remote <remote_dir> [--type <general|maps|cfg>]
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        String types = Arrays.asList(FileGroupType.values()).stream()
            .map(FileGroupType::toString)
            .collect(Collectors.joining(", "));
        addTypeSpec = parser.acceptsAll(asList("t", "type"), "group type: " + types)
            .withOptionalArg().ofType(FileGroupType.class).defaultsTo(FileGroupType.GENERAL);
        addLocalSpec = parser.acceptsAll(asList("l", "local"), "name of the repository to store files")
            .withRequiredArg().required();
        addRemoteSpec = parser.acceptsAll(asList("r", "remote"), "path to the remote directory")
            .withRequiredArg().required();
        commandService.register(CommandBuilder.startsWith(".sync add")
            .description("Add a file group [A]").permission("support")
            .parser(parser).command(this::syncGroupAdd).build());
    }

    private String syncGroupAdd(IMessage m, OptionSet o) {
        if (!o.has("?")) {
            String local = o.valueOf(addLocalSpec);
            String remote = o.valueOf(addRemoteSpec);
            FileGroupType type = o.valueOf(addTypeSpec);
            SyncGroup group = new SyncGroup();
            group.setLocalDir(local);
            group.setRemoteDir(remote);
            group.setKind(type);
            if (syncGroupService.findByLocalDir(local).isPresent()) {
                return "A group with this local directory already exists";
            } else {
                syncGroupService.save(group);
                return "Group **" + local + "** created successfully";
            }
        }
        return null;
    }

    private void initSyncInfoCommand() {
        // .sync info --id <group_id>
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        infoNonOptionSpec = parser.nonOptions("Numeric ID or local name of the sync groups").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".sync info")
            .description("Get info about a sync group [A]").permission("support")
            .parser(parser).command(this::syncGroupInfo).build());
    }

    private String syncGroupInfo(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(infoNonOptionSpec);
        if (!o.has("?") && !nonOptions.isEmpty()) {
            StringBuilder response = new StringBuilder();
            for (String key : nonOptions) {
                Optional<SyncGroup> optional = (key.matches("[0-9]+") ?
                    syncGroupService.findOne(Long.parseLong(key)) : syncGroupService.findByLocalDir(key));
                if (optional.isPresent()) {
                    SyncGroup group = optional.get();
                    response.append("• Info about group **").append(key).append("**\n")
                        .append(String.format("Local Directory: %s\nRemote Directory: %s\nType: %s\n",
                            group.getLocalDir(), group.getRemoteDir(), group.getKind().toString()));
                } else {
                    response.append("• No files matching **").append(key).append("** were found.");
                }
            }
            return response.toString();
        }
        return null;
    }

    private void initSyncEditCommand() {
        // .sync edit --id <group_id> [--local <local_dir>] [--remote <remote_dir>] [--type <general|maps|cfg>]

    }

    private void initSyncDeleteCommand() {
        // .sync delete --id <group_id> [--force]

    }

    private void initSyncRefreshCommand() {
        // .sync refresh [--local [name1, name2, ...]] [--remote [server1, server2, ...]]
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        refreshLocalSpec = parser.acceptsAll(asList("l", "local"), "comma-separated list of groups to sync locally")
            .withOptionalArg().withValuesSeparatedBy(",");
        refreshRemoteSpec = parser.acceptsAll(asList("r", "remote"), "comma-separated list of servers to sync remotely")
            .withOptionalArg().withValuesSeparatedBy(",");
        commandService.register(CommandBuilder.startsWith(".sync refresh")
            .description("Refresh the given file groups locally and/or remotely [A]").permission("support")
            .parser(parser).command(this::syncGroupRefresh).queued().build());
    }

    private String syncGroupRefresh(IMessage m, OptionSet o) {
        if (!o.has("?") && (o.has(refreshLocalSpec) || o.has(refreshRemoteSpec))) {
            StringBuilder response = new StringBuilder();
            List<String> localDirs = o.valuesOf(refreshLocalSpec); // can be empty
            List<SyncGroup> groups = new ArrayList<>();
            if (localDirs.isEmpty()) {
                groups.addAll(syncGroupService.findAll());
            } else {
                for (String key : localDirs) {
                    (key.matches("[0-9]+") ?
                        syncGroupService.findOne(Long.parseLong(key)) : syncGroupService.findByLocalDir(key))
                        .ifPresent(groups::add);
                }
            }
            if (o.has(refreshLocalSpec)) {
                // first locally
                List<ServerFile> files = serverFileService.findAllEagerly();
                if (groups.isEmpty()) {
                    response.append("No groups found, skipping local refresh");
                } else {
                    response.append("Locally updated groups: ").append(groups.toString()).append("\n");
                    List<ServerFile> toRefresh = files.stream()
                        .filter(f -> groups.contains(f.getSyncGroup()))
                        .collect(Collectors.toList());
                    if (!toRefresh.isEmpty()) {
                        try {
                            discordService.privateMessage(m.getAuthor().getID())
                                .appendContent("Refreshing **" + toRefresh.size() + "** files").send();
                        } catch (Exception e) {
                            log.warn("Could not send PM to user: {}", e.toString());
                        }
                        for (ServerFile file : toRefresh) {
                            long oldTime = Optional.ofNullable(file.getLastModified()).orElse(0L);
                            String oldTag = Optional.ofNullable(file.geteTag()).orElse("");
                            ServerFile refreshed = serverFileService.refresh(file);
                            long newTime = Optional.ofNullable(refreshed.getLastModified()).orElse(0L);
                            String newTag = Optional.ofNullable(refreshed.geteTag()).orElse("");
                            if (oldTime != newTime || !oldTag.equals(newTag)) {
                                String content = String.format("Refreshed from %s\nLast modified: %d -> %d\nETag: %s -> %s",
                                    file.getRemoteUrl(), oldTime, newTime, oldTag, newTag);
                                try {
                                    discordService.privateMessage(m.getAuthor().getID()).appendContent(content).send();
                                } catch (Exception e) {
                                    log.warn("Could not send PM to user: {}", e.toString());
                                }
                            }
                        }
                    } else {
                        response.append("No files matching the given groups found");
                    }
                }
            }
            if (o.has(refreshRemoteSpec)) {
                // then remotely
                List<String> remoteServers = o.valuesOf(refreshRemoteSpec); // can be empty
                List<GameServer> servers = new ArrayList<>();
                if (remoteServers.isEmpty()) {
                    servers.addAll(gameServerService.findAll());
                } else {
                    servers.addAll(gameServerService.findServersMultiple(remoteServers));
                }
                if (remoteServers.isEmpty()) {
                    response.append("No servers found, skipping remote refresh");
                } else {
                    for (GameServer server : servers) {
                        log.info("**** Starting refresh of {} ({}) ****", server.getShortName(), server.getAddress());
                        List<SyncGroup> ok = syncGroupService.updateGroups(server, groups);
                        log.info("Successfully refreshed groups: {} on server {}", ok, server);
                    }
                }
            }
            return response.toString();
        }
        return null;
    }
}
