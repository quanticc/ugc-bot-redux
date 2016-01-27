package com.ugcleague.ops.service;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.domain.SyncGroup;
import com.ugcleague.ops.domain.enumeration.FileGroupType;
import com.ugcleague.ops.repository.SyncGroupRepository;
import com.ugcleague.ops.service.util.FtpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SyncGroupService {
    private static final Logger log = LoggerFactory.getLogger(SyncGroupService.class);

    private final GameServerService gameServerService;
    private final SyncGroupRepository syncGroupRepository;
    private final LeagueProperties leagueProperties;
    private String repositoryDir;

    @Autowired
    public SyncGroupService(GameServerService gameServerService, SyncGroupRepository syncGroupRepository, LeagueProperties leagueProperties) {
        this.gameServerService = gameServerService;
        this.syncGroupRepository = syncGroupRepository;
        this.leagueProperties = leagueProperties;
    }

    @PostConstruct
    private void configure() {
        repositoryDir = leagueProperties.getSyncRepositoryDir();
    }

    @Async
    public void updateAllGroups(GameServer server) {
        updateGroups(server, syncGroupRepository.findAll());
    }

    public List<SyncGroup> updateGroups(GameServer server, List<SyncGroup> groups) {
        // TODO: optionally add a filter against FileGroupType to not update "MAPS", for instance.
        log.debug("Preparing to update {} with files from {}", server, groups);
        Optional<FtpClient> o = establishFtpConnection(server);
        if (o.isPresent()) {
            final FtpClient client = o.get();
            List<SyncGroup> successful = groups.stream().map(client::updateGroup).collect(Collectors.toList());
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
}
