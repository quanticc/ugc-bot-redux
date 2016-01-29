package com.ugcleague.ops.service;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.ServerFile;
import com.ugcleague.ops.repository.ServerFileRepository;
import com.ugcleague.ops.service.util.BZip2Decompressor;
import com.ugcleague.ops.service.util.RarDecompressor;
import jodd.io.ZipUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ServerFileService {
    private static final Logger log = LoggerFactory.getLogger(ServerFileService.class);

    public static final String APPLICATION_ZIP = "application/zip";
    public static final String APPLICATION_X_RAR_COMPRESSED = "application/x-rar-compressed";
    public static final String APPLICATION_X_BZIP2 = "application/x-bzip2";
    public static final String APPLICATION_X_VALVE_BSP = "application/x-valve-bsp";

    private final Tika tika = new Tika();
    private final ServerFileRepository serverFileRepository;
    private final LeagueProperties leagueProperties;
    private String repositoryDir;

    @Autowired
    public ServerFileService(ServerFileRepository serverFileRepository, LeagueProperties leagueProperties) {
        this.serverFileRepository = serverFileRepository;
        this.leagueProperties = leagueProperties;
    }

    @PostConstruct
    private void configure() {
        repositoryDir = leagueProperties.getSyncRepositoryDir();
    }

    public HttpStatus isUpToDate(ServerFile serverFile) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setBufferRequestBody(false);

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(requestFactory);
        restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());

        HttpHeaders requestHeaders = new HttpHeaders();
        //requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
        requestHeaders.setAccept(Collections.singletonList(MediaType.ALL));

        if (serverFile.geteTag() != null) {
            requestHeaders.setIfNoneMatch(serverFile.geteTag());
        }

        if (serverFile.getLastModified() != null) {
            requestHeaders.setLastModified(serverFile.getLastModified());
        }

        HttpEntity<String> entity = new HttpEntity<String>(requestHeaders);

        ResponseEntity<byte[]> response = restTemplate.exchange(serverFile.getRemoteUrl(), HttpMethod.GET, entity, byte[].class);

        return response.getStatusCode();
    }

    private ResponseEntity<byte[]> exchange(ServerFile serverFile) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setBufferRequestBody(false);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(requestFactory);
        restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());
        HttpHeaders requestHeaders = new HttpHeaders();
        //requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
        requestHeaders.setAccept(Collections.singletonList(MediaType.ALL));
        if (serverFile.geteTag() != null) {
            requestHeaders.setIfNoneMatch(serverFile.geteTag());
        }
        if (serverFile.getLastModified() != null) {
            requestHeaders.setLastModified(serverFile.getLastModified());
        }
        HttpEntity<String> entity = new HttpEntity<String>(requestHeaders);
        return restTemplate.exchange(serverFile.getRemoteUrl(), HttpMethod.GET, entity, byte[].class);
    }

    public ServerFile refresh(ServerFile serverFile) {
        // check if the resource is up-to-date
        log.debug("Checking if we have the latest version of: {}", serverFile.getName());
        ResponseEntity<byte[]> response = exchange(serverFile);
        try {
            updateIfNeeded(serverFile, response);
        } catch (IOException e) {
            log.warn("Could not update remote file", e);
        }
        // successful transfer: move to sync-repository
        // successful but File does not match expected file (serverFile.syncGroup.kind)
        return serverFile;
    }

    static String fallbackFilename(ServerFile serverFile) {
        URL extUrl;
        try {
            extUrl = new URL(serverFile.getRemoteUrl());
        } catch (MalformedURLException e) {
            return serverFile.getName();
        }
        String filename = serverFile.getName();
        String path = extUrl.getPath();
        String[] pathContents = path.split("[\\\\/]");
        String lastPart = pathContents[pathContents.length - 1];
        String[] lastPartContents = lastPart.split("\\.");
        if (lastPartContents.length > 1) {
            int lastPartContentLength = lastPartContents.length;
            String name = "";
            for (int i = 0; i < lastPartContentLength; i++) {
                if (i < (lastPartContents.length - 1)) {
                    name += lastPartContents[i];
                    if (i < (lastPartContentLength - 2)) {
                        name += ".";
                    }
                }
            }
            String extension = lastPartContents[lastPartContentLength - 1];
            filename = name + "." + extension;
        }
        log.debug("Got fallback name for file: {} -> {}", serverFile.getRemoteUrl(), filename);
        return filename;
    }

    private void updateIfNeeded(ServerFile serverFile, ResponseEntity<byte[]> response) throws IOException {
        if (response.getStatusCode() == HttpStatus.OK) {
            HttpHeaders responseHeaders = response.getHeaders();
            String key = "filename=\"";
            List<String> contentDisposition = responseHeaders.get("Content-Disposition");
            String fileName = (contentDisposition == null ? fallbackFilename(serverFile) : contentDisposition.stream().filter(s -> s.contains(key))
                .map(s -> s.substring(s.indexOf(key) + key.length(), s.length() - 1)).findFirst()
                .orElse(fallbackFilename(serverFile)));
            Path tempDir = Paths.get("tmp", serverFile.getName());
            Files.createDirectories(tempDir);
            log.info("Downloading resource from {}", serverFile.getRemoteUrl());
            Path output = Files.write(tempDir.resolve(fileName), response.getBody());
            serverFile.setLastModified(responseHeaders.getLastModified());
            String eTag = responseHeaders.getETag();
            if (eTag != null) {
                serverFile.seteTag(eTag);
            }
            log.debug("Updating resource data: {}", serverFile.toString());
            serverFileRepository.save(serverFile);
            Path destPath = storeFile(serverFile.getSyncGroup().getLocalDir(), output);
            log.info("Resource saved to: {}", destPath);
        }
    }

    private Path storeFile(String storeKey, Path path) throws IOException {
        String type = tika.detect(path);
        log.debug("File {} is of type: {}", path, type);
        Path srcPath = path;
        Path destDir = Paths.get(repositoryDir, storeKey);
        Files.createDirectories(destDir);
        Path outputDir = path.resolveSibling("content");
        if (APPLICATION_ZIP.equals(type)) {
            ZipUtil.unzip(path.toFile(), outputDir.toFile());
            srcPath = walkAndFindCommonPath(outputDir);
        } else if (APPLICATION_X_RAR_COMPRESSED.equals(type)) {
            RarDecompressor.decompress(path.toFile(), outputDir.toFile());
            srcPath = walkAndFindCommonPath(outputDir);
        } else if (APPLICATION_X_BZIP2.equals(type)) {
            srcPath = BZip2Decompressor.decompress(path.toFile()).toPath();
            if (!isValveMap(srcPath)) {
                log.warn("The file {} does not seem to be a Valve BSP map", srcPath);
            }
        }
        log.debug("Copying to repository: {} -> {}", srcPath, destDir);
        if (Files.isDirectory(srcPath)) {
            FileUtils.copyDirectory(srcPath.toFile(), destDir.toFile());
        } else {
            FileUtils.copyFileToDirectory(srcPath.toFile(), destDir.toFile());
        }
        return destDir;
    }

    static Path walkAndFindCommonPath(Path root) throws IOException {
        List<Path> contents = Files.walk(root).filter(p -> !p.equals(root)).collect(Collectors.toList());
        Path common = longestCommonPath(contents);
        if (common == null) {
            common = root;
        }
        return common;
    }

    static Path longestCommonPath(List<Path> paths) {
        Path commonPath = null;
        for (int prefixIndex = 0; prefixIndex < paths.get(0).getNameCount(); prefixIndex++) {
            Path subpath = paths.get(0).subpath(0, prefixIndex + 1);
            for (int i = 1; i < paths.size(); i++) {
                Path current = paths.get(i).subpath(0, prefixIndex + 1);
                if (!subpath.equals(current)) {
                    return commonPath;
                }
                commonPath = current;
            }
        }
        log.debug("Longest common path between {} is {}", paths, commonPath);
        return commonPath;
    }

    static boolean isValveMap(Path path) {
        byte[] buffer = new byte[4];
        try (InputStream stream = new FileInputStream(path.toFile())) {
            IOUtils.read(stream, buffer);
            return hasBSPMagicPacket(buffer);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hasBSPMagicPacket(byte[] bytes) {
        // VBSP little-endian, PSBV big-endian
        return (bytes[0] == (byte) 0x56 && bytes[1] == (byte) 0x42 && bytes[2] == (byte) 0x53 && bytes[3] == (byte) 0x50);
    }

    public List<ServerFile> findAll() {
        return serverFileRepository.findAll();
    }

    public List<ServerFile> findByName(String name) {
        return serverFileRepository.findByNameLike(name);
    }

    public Optional<ServerFile> findOne(Long id) {
        return Optional.ofNullable(serverFileRepository.findOne(id));
    }

    public ServerFile save(ServerFile file) {
        return serverFileRepository.save(file);
    }

    public List<ServerFile> findAllEagerly() {
        return serverFileRepository.findAllWithEagerRelationships();
    }
}
