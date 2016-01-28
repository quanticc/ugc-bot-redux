package com.ugcleague.ops.service.util;

import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.domain.SyncGroup;
import it.sauronsoftware.ftp4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class FtpClient {
    private static final Logger log = LoggerFactory.getLogger(FtpClient.class);

    private final FTPClient client = new FTPClient();
    private final GameServer server;
    private final Set<String> visited = new HashSet<>();
    private final Map<String, LocalDateTime> times = new HashMap<>();
    private final Map<String, Long> sizes = new HashMap<>();
    private final String repositoryDir;

    public FtpClient(GameServer server, String repositoryDir) {
        this(server, repositoryDir, false);
    }

    public FtpClient(GameServer server, String repositoryDir, boolean secure) {
        client.setType(FTPClient.TYPE_BINARY);
        this.repositoryDir = repositoryDir;
        this.server = server;
        if (secure) {
            configureSecurity();
        }
    }

    private void configureSecurity() {
        TrustManager[] trustManager = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }};
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManager, new SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            client.setSSLSocketFactory(sslSocketFactory);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.warn("Could not setup SSL context", e);
        }
        client.setSecurity(FTPClient.SECURITY_FTPES);
    }

    public boolean connect(Map<String, String> credentials) {
        try {
            throwingConnect(credentials);
            log.info("Connected to {} FTP server", server.getName());
            return true;
        } catch (IOException | FTPIllegalReplyException | FTPException e) {
            log.warn("Login to {} FTP server failed: {}", server.getName(), e.toString());
        }
        return false;
    }

    @Retryable(backoff = @Backoff(2000), include = {IOException.class, FTPIllegalReplyException.class, FTPException.class})
    private void throwingConnect(Map<String, String> credentials)
        throws IllegalStateException, IOException, FTPIllegalReplyException, FTPException {
        try {
            client.connect(credentials.get("ftp-hostname"));
            client.login(credentials.get("ftp-username"), credentials.get("ftp-password"));
            log.info("Connected to {} FTP server", server.getName());
        } catch (IllegalStateException | IOException | FTPIllegalReplyException | FTPException e) {
            client.disconnect(true);
            // rethrow to trigger retrying
            throw e;
        }
    }

    public void disconnect() {
        try {
            log.info("Disconnecting from {} FTP server", server.getName());
            client.disconnect(true);
        } catch (IOException | FTPIllegalReplyException | FTPException e) {
            log.warn("Could not properly disconnect from FTP server: {}", e.toString());
        }
    }

    public SyncGroup updateGroup(SyncGroup group) {
        try {
            return throwingUpdateGroup(group);
        } catch (IllegalStateException | FTPException | IOException | FTPIllegalReplyException e) {
            log.warn("Could not update {} due to {}", group.getRemoteDir(), e.toString());
            return null;
        }
    }

    public SyncGroup throwingUpdateGroup(SyncGroup group) throws FTPException, IOException, FTPIllegalReplyException {
        Path localPath = Paths.get(repositoryDir, group.getLocalDir());
        if (!Files.isDirectory(localPath) || !Files.exists(localPath)) {
            log.warn("Skipping missing local folder: {}", localPath);
            return null;
        }
        List<Path> toUpload = Files.walk(localPath).filter(p -> !Files.isDirectory(p))
            .filter(p -> isOutdated(p, group.getLocalDir(), group.getRemoteDir()))
            .peek(p -> log.info("Preparing to upload {}", p)).collect(Collectors.toList());
        int i = 0;
        for (Path path : toUpload) {
            log.info("[{}/{}] Uploading {} to {}", ++i, toUpload.size(), path, server.getName());
            tryUpload(path);
        }
        return group;
    }

    private void tryUpload(Path path) throws FTPIllegalReplyException, IOException, FTPException {
        try {
            upload(path);
        } catch (FTPDataTransferException | FTPAbortedException e) {
            log.warn("Could not upload file after retrying: {}", e.toString());
        }
    }

    @Retryable(backoff = @Backoff(5000), include = {FTPDataTransferException.class, FTPAbortedException.class})
    private void upload(Path path)
        throws FTPIllegalReplyException, IOException, FTPException, FTPDataTransferException, FTPAbortedException {
        client.upload(path.toFile(), new SimpleTransferListener());
    }

    private boolean isOutdated(Path path, String localDir, String remoteDir) {
        // map a local path to the corresponding remote path
        // use group localDir for the starting point, obtain a relative path
        Path localPath = Paths.get(repositoryDir, localDir).toAbsolutePath().relativize(path.toAbsolutePath());
        // this relative path can be used to obtain the equivalent remote path
        Path remotePath = Paths.get(remoteDir).resolve(localPath);
        Path currentDir = remotePath.getParent();
        try {
            String dir = currentDir.toString();
            if (!visited.contains(dir)) {
                // create missing directories, starting from the root to the current directory
                client.changeDirectory("/");
                createDirectories(dir);
                client.changeDirectory(dir);
                // save the current directory contents size and modified-date data
                FTPFile[] list = client.list();
                Arrays.asList(list).stream().forEach(f -> saveFileAttributes(currentDir, f));
                visited.add(dir);
            }
            client.changeDirectory(dir);
            String key = currentDir.resolve(localPath).toString();
            return (path.toFile().length() != sizes.getOrDefault(key, -1L) || (!path.toString().endsWith(".bsp")
                && LocalDateTime.ofInstant(Instant.ofEpochMilli(path.toFile().lastModified()), ZoneId.systemDefault())
                .isAfter(times.getOrDefault(key, LocalDateTime.MIN))));
        } catch (IOException | FTPIllegalReplyException | FTPException e) {
            log.warn("A FTP operation could not be completed", e);
        } catch (IllegalStateException e) {
            log.warn("The FTP connection was closed", e);
        } catch (FTPDataTransferException | FTPAbortedException | FTPListParseException e) {
            log.warn("Could not retrieve file attributes", e);
        }
        return false;
    }

    private void saveFileAttributes(Path parentDir, FTPFile file) {
        String key = parentDir.resolve(file.getName()).toString();
        times.put(key, LocalDateTime.ofInstant(file.getModifiedDate().toInstant(), ZoneId.systemDefault()));
        sizes.put(key, file.getSize());
    }

    private void createDirectories(String dir) throws IOException, FTPIllegalReplyException, FTPException {
        List<String> paths = Arrays.asList(dir.split("/")).stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
        for (String path : paths) {
            try {
                client.createDirectory(path);
                log.debug("{}: mkdir {}", client.currentDirectory(), path);
            } catch (IOException | FTPIllegalReplyException | FTPException e) {
                // ignore file exists messages
                if (!e.getMessage().contains("File exists.")) {
                    log.warn("Could not create directory {}: {}", path, e.toString());
                }
            }
            client.changeDirectory(path);
        }
    }

}
