package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.document.GameServer;
import com.ugcleague.ops.repository.mongo.GameServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;

@Service
@Transactional
public class GameServerMigrationService {

    private static final Logger log = LoggerFactory.getLogger(GameServerMigrationService.class);

    private final GameServerRepository gameServerRepository;
    private final LegacyGameServerService legacyGameServerService;

    @Autowired
    public GameServerMigrationService(GameServerRepository gameServerRepository, LegacyGameServerService legacyGameServerService) {
        this.gameServerRepository = gameServerRepository;
        this.legacyGameServerService = legacyGameServerService;
    }

    @PostConstruct
    private void configure() {
        if (gameServerRepository.count() == 0 && legacyGameServerService.count() > 0) {
            for (com.ugcleague.ops.domain.GameServer gameServer : legacyGameServerService.findAll()) {
                log.debug("Migrating GameServer {}", gameServer.getShortName());
                GameServer newGameServer = new GameServer();
                newGameServer.setId(gameServer.getSubId());
                newGameServer.setName(gameServer.getName());
                newGameServer.setAddress(gameServer.getAddress());
                newGameServer.setClaimable(gameServer.getFlags().stream().noneMatch(f -> f.getName().equals("no-claims")));
                newGameServer.setSecure(gameServer.getFlags().stream().noneMatch(f -> f.getName().equals("insecure")));
                newGameServer.setExpireCheckDate(gameServer.getExpireCheckDate());
                newGameServer.setExpireDate(gameServer.getExpireDate());
                newGameServer.setLastGameUpdate(gameServer.getLastGameUpdate());
                newGameServer.setLastRconDate(gameServer.getLastRconDate());
                newGameServer.setLastValidPing(gameServer.getStatusCheckDate());
                newGameServer.setRconPassword(gameServer.getRconPassword());
                newGameServer.setTvPort(gameServer.getTvPort());
                newGameServer.setSvPassword(gameServer.getSvPassword());
                newGameServer.setMapName(gameServer.getMapName());
                newGameServer.setMaxPlayers(gameServer.getMaxPlayers());
                newGameServer.setPing(gameServer.getPing());
                newGameServer.setVersion(gameServer.getVersion());
                newGameServer.setStatusCheckDate(gameServer.getStatusCheckDate());
                gameServerRepository.save(newGameServer);
            }
        }
    }
}
