package com.ugcleague.ops.service.discord;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxFiles;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.service.DropboxService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static com.ugcleague.ops.util.DateUtil.formatRelativeBetweenNowAnd;
import static com.ugcleague.ops.util.Util.humanizeBytes;

@Service
public class DropboxQueryService {

    private static final Logger log = LoggerFactory.getLogger(DropboxQueryService.class);

    private final CommandService commandService;
    private final DropboxService dropboxService;
    private final LeagueProperties leagueProperties;

    private OptionSpec<String> listNonOptionSpec;
    private OptionSpec<String> existsNonOptionSpec;
    private OptionSpec<String> shareNonOptionSpec;
    private String uploadsDir;

    @Autowired
    public DropboxQueryService(CommandService commandService, DropboxService dropboxService,
                               LeagueProperties leagueProperties) {
        this.commandService = commandService;
        this.dropboxService = dropboxService;
        this.leagueProperties = leagueProperties;
    }

    @PostConstruct
    private void configure() {
        initShareCommand();
        initExistsCommand();
        initListCommand();
        initDeleteCommand();
        uploadsDir = leagueProperties.getDropbox().getUploadsDir();
    }

    private void initListCommand() {
        // .db list <path>
        OptionParser parser = newParser();
        listNonOptionSpec = parser.nonOptions("Remote folders to list its contents").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".db list")
            .description("List contents of a Dropbox folder")
            .permission("support").permissionReplies().mention()
            .queued().parser(parser).command(this::listCommand).build());
    }

    private String listCommand(IMessage message, OptionSet optionSet) {
        Set<String> nonOptions = optionSet.valuesOf(listNonOptionSpec).stream().collect(Collectors.toSet());
        if (optionSet.has("?")) {
            return null;
        }
        StringBuilder response = new StringBuilder();
        if (nonOptions.isEmpty()) {
            try {
                response.append(listFolder(uploadsDir));
            } catch (DbxException e) {
                response.append(e.getMessage());
            }
        }
        for (String arg : nonOptions) {
            if (arg.contains("..")) {
                response.append("Folder can't contain \"..\"\n");
            } else {
                try {
                    response.append(listFolder(uploadsDir + arg));
                } catch (DbxException e) {
                    response.append(e.getMessage());
                }
            }
        }
        return response.toString();
    }

    private String listFolder(String path) throws DbxException {
        DbxFiles.ListFolderResult result = dropboxService.listFolder(path);
        String response = "Contents of **" + path + "**\n";
        while (result != null) {
            for (DbxFiles.Metadata metadata : result.entries) {
                response += formatMetadata(metadata);
            }
            if (result.hasMore) {
                result = dropboxService.listFolderContinue(result.cursor);
            } else {
                result = null;
            }
        }
        return response;
    }

    private String formatMetadata(DbxFiles.Metadata metadata) {
        String size = "";
        String lastModified = "";
        if (metadata instanceof DbxFiles.FileMetadata) {
            DbxFiles.FileMetadata fileMetadata = (DbxFiles.FileMetadata) metadata;
            size = humanizeBytes(fileMetadata.size);
            lastModified = formatRelativeBetweenNowAnd(fileMetadata.serverModified.toInstant());
        } else if (metadata instanceof DbxFiles.FolderMetadata) {
            size = "(dir)";
        } else if (metadata instanceof DbxFiles.DeletedMetadata) {
            size = "(deleted)";
        }
        return String.format("**%s**\t\t%s\t\t%s\n", metadata.name, size, lastModified);
    }

    private void initExistsCommand() {
        // .db exists <path>
        OptionParser parser = newParser();
        existsNonOptionSpec = parser.nonOptions("Remote file to check for existence").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".db exists")
            .description("Check a Dropbox file for existence")
            .permission("support").permissionReplies().mention()
            .queued().parser(parser).command(this::existsCommand).build());
    }

    private String existsCommand(IMessage message, OptionSet optionSet) {
        Set<String> nonOptions = optionSet.valuesOf(existsNonOptionSpec).stream().collect(Collectors.toSet());
        if (optionSet.has("?")) {
            return null;
        }
        StringBuilder response = new StringBuilder();
        if (nonOptions.isEmpty()) {
            try {
                response.append(exists(uploadsDir));
            } catch (DbxException e) {
                response.append(e.getMessage());
            }
        }
        for (String arg : nonOptions) {
            if (arg.contains("..")) {
                response.append("Request can't contain \"..\"\n");
            } else {
                try {
                    response.append(exists(uploadsDir + arg));
                } catch (DbxException e) {
                    response.append(e.getMessage());
                }
            }
        }
        return response.toString();
    }

    private String exists(String path) throws DbxException {
        return formatMetadata(dropboxService.exists(path));
    }

    private void initShareCommand() {
        // .db share <path>
        OptionParser parser = newParser();
        shareNonOptionSpec = parser.nonOptions("Remote file to get its shareable link").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".db share")
            .description("Get shared download link of a remote file")
            .permission("support").permissionReplies().mention()
            .queued().parser(parser).command(this::shareCommand).build());
    }

    private String shareCommand(IMessage message, OptionSet optionSet) {
        Set<String> nonOptions = optionSet.valuesOf(shareNonOptionSpec).stream().collect(Collectors.toSet());
        if (optionSet.has("?")) {
            return null;
        }
        StringBuilder response = new StringBuilder();
        if (nonOptions.isEmpty()) {
            Optional<String> link = dropboxService.getSharedLink(uploadsDir);
            if (link.isPresent()) {
                response.append(link.get()).append("\n");
            }
        }
        for (String arg : nonOptions) {
            if (arg.contains("..")) {
                response.append("File can't contain \"..\"\n");
            } else {
                Optional<String> link = dropboxService.getSharedLink(uploadsDir + arg);
                if (link.isPresent()) {
                    response.append(link.get()).append("\n");
                }
            }
        }
        return response.toString();
    }

    private void initDeleteCommand() {
        // .db delete <path>
        OptionParser parser = newParser();
        shareNonOptionSpec = parser.nonOptions("Remote file or folder to delete").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".db delete")
            .description("Delete remote file or folder")
            .permission("support").permissionReplies().mention()
            .queued().parser(parser).command(this::deleteCommand).build());
    }

    private String deleteCommand(IMessage message, OptionSet optionSet) {
        Set<String> nonOptions = optionSet.valuesOf(shareNonOptionSpec).stream().collect(Collectors.toSet());
        if (optionSet.has("?") || nonOptions.isEmpty()) {
            return null;
        }
        StringBuilder response = new StringBuilder();
        for (String arg : nonOptions) {
            if (arg.contains("..")) {
                response.append("File can't contain \"..\"\n");
            } else {
                try {
                    DbxFiles.Metadata metadata = dropboxService.deleteFile(uploadsDir + arg);
                    log.debug("Deleted: {}", metadata);
                    response.append(String.format("*Deleted %s*\n", uploadsDir + arg));
                } catch (DbxException e) {
                    log.warn("Failed to delete: {}", e.toString());
                    response.append(String.format("*Failed to delete %s*\n", uploadsDir + arg));
                }
            }
        }
        return response.toString();
    }
}
