package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.ServerFile;
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

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;

@Service
@Transactional
public class FileQueryService {

    private static final Logger log = LoggerFactory.getLogger(FileQueryService.class);

    private final CommandService commandService;
    private final ServerFileService serverFileService;
    private final SyncGroupService syncGroupService;

    private OptionSpec<String> addNonOptionSpec;
    private OptionSpec<String> addGroupSpec;
    private OptionSpec<String> addNameSpec;
    private OptionSpec<String> infoNonOptionSpec;
    private OptionSpec<String> refreshNonOptionSpec;
    private OptionSpec<Boolean> addRequiredSpec;
    private OptionSpec<String> editUrlSpec;
    private OptionSpec<String> editGroupSpec;
    private OptionSpec<String> editIdSpec;
    private OptionSpec<String> editNameSpec;
    private OptionSpec<Boolean> editRequiredSpec;
    private OptionSpec<Boolean> editClearSpec;

    @Autowired
    public FileQueryService(CommandService commandService, ServerFileService serverFileService, SyncGroupService syncGroupService) {
        this.commandService = commandService;
        this.serverFileService = serverFileService;
        this.syncGroupService = syncGroupService;
    }

    @PostConstruct
    private void configure() {
        initFileListCommand();
        initFileAddCommand();
        initFileInfoCommand();
        initFileEditCommand();
        initFileDeleteCommand(); // TODO
        initFileRefreshCommand();
    }

    private void initFileListCommand() {
        // .file list
        commandService.register(CommandBuilder.equalsTo(".file list")
            .description("List the files cached for server synchronization [A]").permission("support")
            .command((message, o) -> {
                StringBuilder builder = new StringBuilder();
                for (ServerFile file : serverFileService.findAll()) {
                    builder.append(String.format("[%d] **%s** %s (%s) last modified %s\n",
                        file.getId(), file.getName(), file.getRemoteUrl(), file.getSyncGroup().getLocalDir(),
                        Optional.ofNullable(file.getLastModified()).map(Object::toString).orElse("never")
                    ));
                }
                return builder.toString();
            }).build());
    }

    private void initFileAddCommand() {
        // .file add [--group <group_name>] [--name <name>] [--required [true|false]] <non-option: url>
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        addNonOptionSpec = parser.nonOptions("URL with the location of the files").ofType(String.class);
        addGroupSpec = parser.acceptsAll(asList("g", "group"), "sync group name (see .sync list)").withRequiredArg();
        addNameSpec = parser.acceptsAll(asList("n", "name"), "identifying name").withRequiredArg();
        addRequiredSpec = parser.acceptsAll(asList("r", "required"), "file required to be synced (when performing health checks)")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        commandService.register(CommandBuilder.startsWith(".file add")
            .description("Add remote files to the GS sync list [A]").permission("support")
            .parser(parser).command(this::fileAdd).build());
    }

    private String fileAdd(IMessage m, OptionSet o) {
        List<String> urls = o.valuesOf(addNonOptionSpec);
        if (!o.has("?")) {
            if (urls.isEmpty()) {
                return "You must give the remote URL of the file";
            }
            StringBuilder response = new StringBuilder();
            Optional<String> group = o.has(addGroupSpec) ? Optional.of(o.valueOf(addGroupSpec)) : Optional.empty();
            Optional<String> name = o.has(addNameSpec) ? Optional.of(o.valueOf(addNameSpec)) : Optional.empty();
            boolean multiple = urls.size() > 1;
            AtomicInteger i = new AtomicInteger(1);
            for (String url : urls) {
                try {
                    new URL(url);
                    ServerFile file = new ServerFile();
                    file.setName(multiple ? name.map(n -> n + "-" + i.getAndIncrement()).orElse("") : name.orElse(""));
                    file.setRemoteUrl(url);
                    file.setRequired(o.valueOf(addRequiredSpec));
                    file.setSyncGroup(group.map(g -> syncGroupService.findByLocalDir(g).orElse(null)).orElse(syncGroupService.getDefaultGroup()));
                    serverFileService.save(file);
                    response.append("Added: ").append(url).append("\n");
                } catch (MalformedURLException e) {
                    response.append("Invalid URL: ").append(url).append("\n");
                }
            }
            return response.toString();
        }
        return null;
    }

    private void initFileInfoCommand() {
        // .file info --id <file_id>
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        infoNonOptionSpec = parser.nonOptions("Numeric ID or name of the cached file").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".file info")
            .description("Get info about a cached file [A]").permission("support")
            .parser(parser).command(this::fileInfo).build());
    }

    private String fileInfo(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(infoNonOptionSpec);
        if (!o.has("?") && !nonOptions.isEmpty()) {
            StringBuilder response = new StringBuilder();
            for (String key : nonOptions) {
                // might be a number
                if (key.matches("[0-9]+")) {
                    Optional<ServerFile> optional = serverFileService.findOne(Long.parseLong(key));
                    if (optional.isPresent()) {
                        ServerFile file = optional.get();
                        response.append("• Info about file **").append(key).append("**\n")
                            .append(String.format("Name: %s\nURL: %s\nGroup: %s\nLast modified: %s\nETag: %s\n",
                                file.getName(), file.getRemoteUrl(), file.getSyncGroup().getLocalDir(),
                                Optional.ofNullable(file.getLastModified()).map(Object::toString).orElse("never"),
                                Optional.ofNullable(file.geteTag()).orElse("<none>")));
                    } else {
                        response.append("• No files matching **").append(key).append("** were found.");
                    }
                } else {
                    List<ServerFile> files = serverFileService.findByName(key);
                    if (files.isEmpty()) {
                        response.append("• No files matching **").append(key).append("** were found.");
                    } else {
                        response.append("Files matching **").append(key).append("**\n");
                        files.forEach(f -> appendFileInfo(response, f));
                    }
                }
            }
            return response.toString();
        }
        return null;
    }

    private void appendFileInfo(StringBuilder response, ServerFile file) {
        response.append(String.format("• *ID:* %d, *Name:* %s, *URL:* %s, *Group:* %s, *Last modified:* %s, *ETag:* %s\n",
            file.getId(), file.getName(), file.getRemoteUrl(), file.getSyncGroup().getLocalDir(),
            Optional.ofNullable(file.getLastModified()).map(Object::toString).orElse("never"),
            Optional.ofNullable(file.geteTag()).orElse("<none>")));
    }

    private void initFileEditCommand() {
        // .file edit --id <file_id> [--group <group_name>] [--name <name>] [--url <url>]
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        editIdSpec = parser.acceptsAll(asList("i", "id"), "the name or the unique ID of the file").withRequiredArg().required();
        editUrlSpec = parser.acceptsAll(asList("u", "url"), "remote URL where to grab the file").withRequiredArg();
        editGroupSpec = parser.acceptsAll(asList("g", "group"), "sync group name (see .sync list)").withRequiredArg();
        editNameSpec = parser.acceptsAll(asList("n", "name"), "identifying name").withRequiredArg();
        editRequiredSpec = parser.acceptsAll(asList("r", "required"), "file required to be synced (when performing health checks)")
            .withRequiredArg().ofType(Boolean.class);
        editClearSpec = parser.acceptsAll(asList("c", "clear"), "clears cached meta-data")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        commandService.register(CommandBuilder.startsWith(".file edit")
            .description("Update a given file with new values [A]").permission("support")
            .parser(parser).command(this::fileEdit).build());
    }

    private String fileEdit(IMessage m, OptionSet o) {
        if (!o.has("?")) {
            String key = o.valueOf(editIdSpec);
            ServerFile file = key.matches("[0-9]+") ?
                serverFileService.findOne(Long.parseLong(key))
                    .orElseGet(() -> serverFileService.findByName(key).stream().findFirst().orElse(null)) :
                serverFileService.findByName(key).stream().findFirst().orElse(null);
            if (file == null) {
                return "No file was found with the given id";
            } else {
                StringBuilder response = new StringBuilder();
                Optional<String> url = Optional.ofNullable(o.valueOf(editUrlSpec));
                Optional<String> name = Optional.ofNullable(o.valueOf(editNameSpec));
                Optional<String> group = Optional.ofNullable(o.valueOf(editGroupSpec));
                Optional<Boolean> required = Optional.ofNullable(o.valueOf(editRequiredSpec));
                Optional<Boolean> clear = o.has(editClearSpec) ? Optional.ofNullable(o.valueOf(editClearSpec)) : Optional.empty();
                boolean clearCache = clear.isPresent() && clear.get() != null && clear.get();

                if (url.isPresent()) {
                    try {
                        new URL(url.get());
                        String prev = file.getRemoteUrl();
                        file.setRemoteUrl(url.get());
                        // if the url is changed, invalidate meta-data
                        if (!file.getRemoteUrl().equals(prev)) {
                            clearCache = true;
                        }
                    } catch (MalformedURLException e) {
                        response.append("Invalid URL: ").append(url.get()).append(". Value won't be updated\n");
                    }
                }
                name.ifPresent(file::setName);
                group.map(d -> syncGroupService.findByLocalDir(d).orElse(null)).ifPresent(file::setSyncGroup);
                required.ifPresent(file::setRequired);
                if (clearCache) {
                    file.seteTag(null);
                    file.setLastModified(null);
                }
                ServerFile updated = serverFileService.save(file);
                response.append(String.format("Updated file id %d: Name='%s' Group='%s' Required=%s URL=%s%s\n",
                    updated.getId(), updated.getName(), updated.getSyncGroup().getLocalDir(),
                    updated.getRequired(), updated.getRemoteUrl(), clearCache ? " and cleared meta-data" : ""));
                return response.toString();
            }
        }
        return null;
    }

    private void initFileDeleteCommand() {
        // .file delete --id <group_id>
        // TODO implement .file delete command
    }

    private void initFileRefreshCommand() {
        // .file refresh [--all] <non-options: file keys>
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        refreshNonOptionSpec = parser.nonOptions("Numeric ID or name of the cached file").ofType(String.class);
        parser.acceptsAll(asList("a", "all"), "perform operation on all cached files");
        commandService.register(CommandBuilder.startsWith(".file refresh")
            .description("Check if a cached file is outdated [A]").permission("support")
            .parser(parser).command(this::fileRefresh).queued().build());
    }

    private String fileRefresh(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(refreshNonOptionSpec);
        if (!o.has("?") && (!nonOptions.isEmpty() || o.has("a"))) {
            StringBuilder response = new StringBuilder();
            if (o.has("a")) {
                // ignore all non-options if --all is specified
                for (ServerFile file : serverFileService.findAll()) {
                    response.append(String.format("• Preparing to refresh **%s**: %s Last modified: %s ETag: %s\n",
                        file.getName(), file.getRemoteUrl(),
                        Optional.ofNullable(file.getLastModified()).map(Object::toString).orElse("never"),
                        Optional.ofNullable(file.geteTag()).orElse("<none>")));
                    serverFileService.refresh(file);
                }
            } else {
                for (String key : nonOptions) {
                    // might be a number
                    if (key.matches("[0-9]+")) {
                        Optional<ServerFile> optional = serverFileService.findOne(Long.parseLong(key));
                        if (optional.isPresent()) {
                            ServerFile file = optional.get();
                            response.append(String.format("• Refresh **%s**: %s Last modified: %s ETag: %s\n",
                                file.getName(), file.getRemoteUrl(),
                                Optional.ofNullable(file.getLastModified()).map(Object::toString).orElse("never"),
                                Optional.ofNullable(file.geteTag()).orElse("<none>")));
                            serverFileService.refresh(file);
                        } else {
                            response.append("• No files matching **").append(key).append("** were found.");
                        }
                    } else {
                        List<ServerFile> files = serverFileService.findByName(key);
                        if (files.isEmpty()) {
                            response.append("• No files matching **").append(key).append("** were found.");
                        } else {
                            files.forEach(file -> {
                                response.append(String.format("• Refresh **%s**: %s Last modified: %s ETag: %s\n",
                                    file.getName(), file.getRemoteUrl(),
                                    Optional.ofNullable(file.getLastModified()).map(Object::toString).orElse("never"),
                                    Optional.ofNullable(file.geteTag()).orElse("<none>")));
                                serverFileService.refresh(file);
                            });
                        }
                    }
                }
            }
            return response.toString();
        }
        return null;
    }
}
