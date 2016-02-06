package com.ugcleague.ops.service;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.domain.RemoteFile;
import com.ugcleague.ops.domain.SyncGroup;
import com.ugcleague.ops.domain.enumeration.FileGroupType;
import com.ugcleague.ops.repository.RemoteFileRepository;
import com.ugcleague.ops.repository.SyncGroupRepository;
import com.ugcleague.ops.service.util.FileShareTask;
import com.ugcleague.ops.service.util.FtpClient;
import it.sauronsoftware.ftp4j.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Transactional
public class SyncGroupService {
    private static final Logger log = LoggerFactory.getLogger(SyncGroupService.class);

    private final GameServerService gameServerService;
    private final SyncGroupRepository syncGroupRepository;
    private final LeagueProperties leagueProperties;
    private final RemoteFileRepository remoteFileRepository;
    private final DropboxService dropboxService;
    private final Map<GameServer, Map<String, LocalDateTime>> lastAccessMap = new ConcurrentHashMap<>();
    private String repositoryDir;
    private String downloadsDir;

    @Autowired
    public SyncGroupService(GameServerService gameServerService, SyncGroupRepository syncGroupRepository,
                            LeagueProperties leagueProperties, RemoteFileRepository remoteFileRepository,
                            DropboxService dropboxService) {
        this.gameServerService = gameServerService;
        this.syncGroupRepository = syncGroupRepository;
        this.leagueProperties = leagueProperties;
        this.remoteFileRepository = remoteFileRepository;
        this.dropboxService = dropboxService;
    }

    @PostConstruct
    private void configure() {
        repositoryDir = leagueProperties.getRemote().getSyncRepositoryDir();
        downloadsDir = leagueProperties.getRemote().getDownloadsDir();
    }

    @Async
    public void updateAllGroups(GameServer server) {
        updateGroups(server, syncGroupRepository.findAll());
    }

    public List<SyncGroup> updateGroups(GameServer server, List<SyncGroup> groups) {
        return retryableUpdateGroups(server, groups);
    }

    private List<SyncGroup> retryableUpdateGroups(GameServer server, List<SyncGroup> groups) {
        log.debug("Preparing to update {} with files from {}", server, groups);
        Optional<FtpClient> o = establishFtpConnection(server);
        if (o.isPresent()) {
            final FtpClient client = o.get();
            List<SyncGroup> successful = groups.stream().map(client::updateGroup)
                .filter(g -> g != null).collect(Collectors.toList());
            client.disconnect();
            return successful;
        }
        return Collections.emptyList();
    }

    private Optional<FtpClient> establishFtpConnection(GameServer server) {
        FtpClient ftpClient = new FtpClient(server, repositoryDir, gameServerService.isSecure(server));
        Map<String, String> credentials = gameServerService.getFTPConnectInfo(server);
        if (!ftpClient.connect(credentials)) {
            gameServerService.addInsecureFlag(server);
            ftpClient = new FtpClient(server, repositoryDir, false);
            if (!ftpClient.connect(credentials)) {
                log.warn("Unable to login to the FTP server");
                return Optional.empty();
            }
        }
        return Optional.of(ftpClient);
    }

    public Optional<SyncGroup> findByLocalDir(String name) {
        return syncGroupRepository.findByLocalDir(name);
    }

    public SyncGroup getDefaultGroup() {
        return syncGroupRepository.findByLocalDir("tf").orElseGet(() -> {
            SyncGroup group = new SyncGroup();
            group.setLocalDir("tf");
            group.setRemoteDir("/orangebox/tf");
            group.setKind(FileGroupType.GENERAL);
            syncGroupRepository.save(group);
            return group;
        });
    }

    public SyncGroup save(SyncGroup group) {
        return syncGroupRepository.save(group);
    }

    public List<SyncGroup> findAll() {
        return syncGroupRepository.findAll();
    }

    public Optional<SyncGroup> findOne(long id) {
        return Optional.ofNullable(syncGroupRepository.findOne(id));
    }

    public List<RemoteFile> getFileList(GameServer server, String dir, Predicate<RemoteFile> filter) {
        try {
            return retryableFileList(server, dir, filter);
        } catch (IOException e) {
            log.warn("Could not get file list: {}", e.toString());
            return Collections.emptyList();
        }
    }

    @Retryable(backoff = @Backoff(3000))
    private List<RemoteFile> retryableFileList(GameServer server, String dir, Predicate<RemoteFile> filter) throws IOException {
        LocalDateTime last = lastAccessMap.computeIfAbsent(server, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(dir, k -> LocalDateTime.MIN);
        if (LocalDateTime.now().minusMinutes(leagueProperties.getRemote().getListCachedMinutes()).isBefore(last)) {
            log.debug("Retrieving a cached file list from {} ({}) on directory {}", server.getShortName(), server.getAddress(), dir);
            return remoteFileRepository.findByFolderAndOwner(dir, server).stream()
                .filter(filter).collect(Collectors.toList());
        } else {
            log.debug("Preparing to get file list from {} ({}) on directory {}", server.getShortName(), server.getAddress(), dir);
            Optional<FtpClient> o = establishFtpConnection(server);
            if (o.isPresent()) {
                FtpClient client = o.get();
                List<FTPFile> files = client.list(dir);
                if (files.isEmpty()) {
                    client.disconnect();
                    throw new IOException("No files found");
                }
                // map FTPFiles to RemoteFiles
                List<RemoteFile> remotes = files.stream()
                    .map(f -> mapToRemoteFile(f, server, dir))
                    .filter(filter)
                    .map(remoteFileRepository::save)
                    .collect(Collectors.toList());
                client.disconnect();
                lastAccessMap.get(server).put(dir, LocalDateTime.now());
                return remotes;
            } else {
                throw new IOException("Could not establish connection");
            }
        }
    }

    private RemoteFile mapToRemoteFile(FTPFile file, GameServer server, String dir) {
        RemoteFile remote = remoteFileRepository.findByFolderAndFilenameAndOwner(dir, file.getName(), server)
            .orElseGet(RemoteFile::new);
        remote.setFilename(file.getName());
        remote.setFolder(dir);
        remote.setOwner(server);
        remote.setSize(file.getSize());
        Date modifiedDate = file.getModifiedDate();
        if (modifiedDate != null) {
            remote.setModified(ZonedDateTime.ofInstant(modifiedDate.toInstant(), ZoneId.systemDefault()));
        }
        return remote;
    }

    @Async
    public CompletableFuture<FileShareTask> shareToDropbox(GameServer server, String dir, Predicate<RemoteFile> filter) {
        FileShareTask task = new FileShareTask();
        try {
            List<RemoteFile> files = retryableListAndDownload(server, dir, filter);
            if (!files.isEmpty()) {
                // these files are now downloaded - upload is pending
                task.getRequested().addAll(files);
                dropboxService.batchUpload(task);
            }
        } catch (IOException e) {
            log.warn("Could not get file list: {}", e.toString());
        }
        task.setEnd(System.currentTimeMillis());
        return CompletableFuture.completedFuture(task);
    }

    @Retryable(backoff = @Backoff(3000))
    private List<RemoteFile> retryableListAndDownload(GameServer server, String dir, Predicate<RemoteFile> filter) throws IOException {
        log.debug("Preparing to get file list from {} ({}) on directory {}",
            server.getShortName(), server.getAddress(), dir);
        Optional<FtpClient> o = establishFtpConnection(server);
        if (o.isPresent()) {
            FtpClient client = o.get();
            List<FTPFile> files = client.list(dir);
            if (files.isEmpty()) {
                client.disconnect();
                throw new IOException("No files found");
            }
            // map FTPFiles to RemoteFiles
            List<RemoteFile> remotes = files.stream()
                .map(f -> mapToRemoteFile(f, server, dir))
                .filter(filter)
                .map(remoteFileRepository::save)
                .collect(Collectors.toList());
            for (RemoteFile file : remotes) {
                // TODO improve path concatenation (check for bad input)
                Path parent = Paths.get(downloadsDir).resolve(server.getShortName() + file.getFolder());
                Files.createDirectories(parent);
                Path destPath = parent.resolve(file.getFilename()).toAbsolutePath();
                log.debug("Downloading from {} ({}): {}/{} --> {}",
                    server.getShortName(), server.getAddress(), file.getFolder(), file.getFilename(), destPath);
                // file could already be downloaded. check first
                if (Files.exists(destPath)) {
                    log.info("File appears to be already downloaded: {}", destPath);
                    long actual = destPath.toFile().length();
                    long expected = file.getSize();
                    if (actual != expected) {
                        log.warn("File sizes don't match: remote={} local={}", expected, actual);
                    }
                } else {
                    // download is a blocking operation, that might be retried
                    // TODO ensure that file.getFolder() has a leading slash
                    client.download(file.getFolder(), file.getFilename(), destPath.toFile());
                    // file could be corrupt or non existing at this point
                    if (Files.exists(destPath)) {
                        log.info("File downloaded from {} to {}", file, destPath);
                        // size could be a nice heuristic, but don't believe in it too much for now - just log
                        long actual = destPath.toFile().length();
                        long expected = file.getSize();
                        if (actual != expected) {
                            log.warn("File sizes don't match: remote={} local={}", expected, actual);
                        }
                    } else {
                        client.disconnect();
                        throw new IOException("Destination file does not exist");
                    }
                }
            }
            client.disconnect();
            return remotes;
        } else {
            throw new IOException("Could not establish connection");
        }
    }

    public RemoteFile saveRemoteFile(RemoteFile remoteFile) {
        return remoteFileRepository.save(remoteFile);
    }

    public File getLocalFile(RemoteFile remoteFile) {
        // TODO check for clean input
        return Paths.get(downloadsDir)
            .resolve(remoteFile.getOwner().getShortName() + remoteFile.getFolder())
            .resolve(remoteFile.getFilename()).toFile();
    }
}
