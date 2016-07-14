package com.ugcleague.ops.service;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.sharing.SharedLinkMetadata;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.document.RemoteFile;
import com.ugcleague.ops.service.util.FileShareTask;
import jodd.io.ZipUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.ugcleague.ops.util.DateUtil.now;
import static com.ugcleague.ops.util.Util.humanizeBytes;

@Service
public class DropboxService {

    private static final Logger log = LoggerFactory.getLogger(DropboxService.class);
    private static final long CHUNK_MAX_SIZE = 150_000_000;

    private final LeagueProperties leagueProperties;
    private final DbxClientV2 client;

    @Autowired
    public DropboxService(LeagueProperties leagueProperties, DbxClientV2 client) {
        this.leagueProperties = leagueProperties;
        this.client = client;
    }

    /**
     * Upload a group of files in batch if possible, defaulting to individual upload if needed.
     *
     * @param task a FileShareTask containing the file definitions to upload
     * @return the same FileShareTask instance, now containing the successfully uploaded files.
     */
    public FileShareTask batchUpload(FileShareTask task) {
        Path downloadsPath = Paths.get(leagueProperties.getRemote().getDownloadsDir());
        List<RemoteFile> files = task.getRequested();
        if (task.isZip()) {
            // try to make a batch upload with all the tasked files
            String id = files.get(0).getServer();
            String zipFilename = String.format("batch-%s-%s.zip", id, now("yyyyMMddHHmmss"));
            Path zipPath = Paths.get("tmp", "zip", zipFilename);
            try {
                Files.createDirectories(zipPath.getParent());
                ZipUtil.AddToZip command = ZipUtil.addToZip(ZipUtil.createZip(zipPath.toFile()));
                for (RemoteFile file : files) {
                    // TODO check for clean input
                    Path srcPath = downloadsPath.resolve(file.getServer() + file.getFolder())
                        .resolve(file.getFilename())
                        .toAbsolutePath();
                    log.debug("Adding to batch zip: {}", srcPath);
                    command.file(srcPath.toFile());
                }
                log.debug("Packing into {}", zipPath);
                command.add();
                String destPath = String.format("%s/%s/%s",
                    leagueProperties.getDropbox().getUploadsDir(), id, zipFilename);
                Metadata metadata = uploadFile(zipPath, destPath);
                if (metadata != null) {
                    Optional<String> batchShareLink = getSharedLink(destPath);
                    task.setBatchSharedUrl(batchShareLink);
                    for (RemoteFile successful : task.getRequested()) {
                        //batchShareLink.ifPresent(successful::setSharedUrl); // to copy batch url to each file
                        task.getSuccessful().add(successful);
                    }
                }
                return task;
            } catch (DbxException | IOException e) {
                log.warn("Could not upload as batch, attempting as single file uploads: {}", e.toString());
            }
        }
        for (RemoteFile file : files) {
            // TODO check for clean input
            Path srcPath = downloadsPath.resolve(file.getServer() + file.getFolder())
                .resolve(file.getFilename())
                .toAbsolutePath();
            File srcFile = srcPath.toFile();
            try {
                if (srcFile.length() > CHUNK_MAX_SIZE) {
                    log.info("Packing into gzip: {}", srcPath);
                    srcPath = ZipUtil.gzip(srcFile).toPath();
                }
                String destPath = String.format("%s/%s/%s", leagueProperties.getDropbox().getUploadsDir(),
                    file.getServer() + file.getFolder(), srcPath.getFileName().toString());
                destPath = destPath.replace("\\", "/").replace("//", "/");
                Metadata metadata = uploadFile(srcPath, destPath);
                if (metadata != null) {
                    task.getSuccessful().add(file);
                    getSharedLink(destPath).ifPresent(file::setSharedUrl);
                }
            } catch (IOException | DbxException e) {
                log.warn("Failed to upload: {}", e.toString());
            }
        }
        return task;
    }

    /**
     * Uploads a single file to Dropbox (maximum size 150 MB)
     *
     * @param srcPath  the file to upload
     * @param destPath the remote location to store the file, MUST start with "/"
     * @return Dropbox metadata about the upload, or <code>null</code> if the upload failed due to size restrictions.
     * @throws IOException  if the local stream had a I/O error
     * @throws DbxException if a Dropbox error occurred
     */
    public FileMetadata uploadFile(Path srcPath, String destPath) throws IOException, DbxException {
        long size = srcPath.toFile().length();
        if (size > CHUNK_MAX_SIZE) {
            log.warn("Skipping file {} because it is too large (> 150 MB): {}", srcPath, humanizeBytes(size));
            return null;
        } else {
            try (InputStream inputStream = new FileInputStream(srcPath.toFile())) {
                FileMetadata metadata = client.files().uploadBuilder(destPath)
                    .withMode(WriteMode.ADD).uploadAndFinish(inputStream);
                log.info("Uploaded {} to {} ({}) on {}", srcPath, metadata.getPathLower(), humanizeBytes(metadata.getSize()), metadata.getServerModified());
                return metadata;
            }
        }
    }

    /**
     * Get an non-expirable, publicly shareable Dropbox link to the given path.
     *
     * @param dropboxPath the dropbox path where the file is located
     * @return a URL with a shared link or an empty optional if an error happened.
     */
    public Optional<String> getSharedLink(String dropboxPath) {
        try {
            SharedLinkMetadata result = client.sharing().createSharedLinkWithSettings(dropboxPath);
            log.info("Got shared link for {}: {}", result.getPathLower(), result.getUrl());
            return Optional.ofNullable(result.getUrl());
        } catch (DbxException e) {
            log.warn("Could not get a shared link for {}: {}", dropboxPath, e.toString());
        }
        return Optional.empty();
    }

    public Metadata exists(String path) throws DbxException {
        // TODO validate path
        return client.files().getMetadata(path);
    }

    public ListFolderResult listFolder(String path) throws DbxException {
        return client.files().listFolder(path);
    }

    public ListFolderResult listFolderContinue(String cursor) throws DbxException {
        return client.files().listFolderContinue(cursor);
    }

    public FileMetadata downloadFile(String srcPath, Path destPath) throws IOException, DbxException {
        try (OutputStream outputStream = new FileOutputStream(destPath.toFile())) {
            FileMetadata metadata = client.files().downloadBuilder(srcPath).download(outputStream);
            log.info("Downloaded {} to {} ({})", metadata.getPathLower(), destPath, humanizeBytes(metadata.getSize()));
            return metadata;
        }
    }

    public Metadata deleteFile(String path) throws DbxException {
        return client.files().delete(path);
    }
}
