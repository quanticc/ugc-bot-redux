package com.ugcleague.ops.service;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.ugcleague.ops.domain.Flag;
import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.domain.Task;
import com.ugcleague.ops.repository.FlagRepository;
import com.ugcleague.ops.repository.GameServerRepository;
import com.ugcleague.ops.repository.TaskRepository;
import com.ugcleague.ops.service.util.SourceServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.remoting.RemoteAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Service
@Transactional
public class GameServerService {

    private static final Logger log = LoggerFactory.getLogger(GameServerService.class);

    private final AdminPanelService adminPanelService;
    private final SteamCondenserService steamCondenserService;
    private final GameServerRepository gameServerRepository;
    private final TaskRepository taskRepository;
    private final FlagRepository flagRepository;

    @Autowired
    public GameServerService(GameServerRepository gameServerRepository, SteamCondenserService steamCondenserService,
                             AdminPanelService adminPanelService, TaskRepository taskRepository, FlagRepository flagRepository) {
        this.gameServerRepository = gameServerRepository;
        this.steamCondenserService = steamCondenserService;
        this.adminPanelService = adminPanelService;
        this.taskRepository = taskRepository;
        this.flagRepository = flagRepository;
    }

    @PostConstruct
    public void initServerDetails() {
        if (gameServerRepository.count() == 0) {
            refreshServerDetails();
        }
    }

    public void refreshServerDetails() {
        try {
            adminPanelService.getServerDetails().forEach(this::refreshServerDetails);
        } catch (RemoteAccessException | IOException e) {
            log.warn("Could not retrieve server details from remote panel", e);
        }
    }

    private void refreshServerDetails(String address, Map<String, String> data) {
        GameServer server = gameServerRepository.findByAddress(address).orElseGet(() -> newGameServer());
        server.setAddress(address);
        server.setName(data.get("name"));
        server.setSubId(data.get("SUBID"));
        try {
            gameServerRepository.save(server);
        } catch (DataIntegrityViolationException e) {
            log.warn("Unable to update server data", e);
        }
    }

    private GameServer newGameServer() {
        ZonedDateTime epoch = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneId.systemDefault());
        GameServer server = new GameServer();
        server.setVersion(0);
        server.setExpireDate(epoch);
        server.setExpireCheckDate(epoch);
        server.setLastGameUpdate(epoch);
        server.setLastRconDate(epoch);
        server.setStatusCheckDate(epoch);
        return server;
    }

    @Scheduled(initialDelay = 60000, fixedRate = 600000)
    public void refreshRconPasswords() {
        // TODO: switch to a better logic since this task depends on expire status refresh
        ZonedDateTime now = ZonedDateTime.now();
        log.debug("==== Refreshing RCON server passwords ====");
        if (!taskRepository.findByName("refreshRconPasswords").map(Task::getEnabled).orElse(false)) {
            log.debug("Skipping task. Next attempt at {}", now.plusMinutes(10));
            return;
        }
        // refreshing passwords of expired servers since they auto restart and change password
        long count = gameServerRepository.findByRconRefreshNeeded(now).parallelStream().map(this::refreshRconPassword)
            .map(gameServerRepository::save).count();
        log.info("{} servers updated their RCON passwords. Next check at {}", count, now.plusMinutes(10));
    }

    /**
     * Crawl through of the remote server panel looking for the rcon_password of the given <code>server</code>.
     *
     * @param server a GameServer
     * @return the updated GameServer, or <code>null</code> if the updated rcon_password could not be retrieved
     */
    public GameServer refreshRconPassword(GameServer server) {
        if (server == null) {
            return null;
        }
        log.debug("Refreshing RCON password data: {}", server.getName());
        try {
            Map<String, String> result = adminPanelService.getServerConfig(server.getSubId());
            server.setRconPassword(result.get("rcon_password"));
            server.setSvPassword(result.get("sv_password"));
            server.setLastRconDate(ZonedDateTime.now());
            return gameServerRepository.save(server);
        } catch (RemoteAccessException | IOException e) {
            log.warn("Could not refresh the RCON password", e.toString());
        }
        return server;
    }

    @Scheduled(initialDelay = 30000, fixedRate = 600000)
    public void updateGameServers() {
        ZonedDateTime now = ZonedDateTime.now();
        log.debug("==== Refreshing server status ====");
        long refreshed = -1;
        long updating = -1;
        if (taskRepository.findByName("refreshServerStatus").map(Task::getEnabled).orElse(false)) {
            refreshed = gameServerRepository.findAll().parallelStream().map(this::refreshServerStatus)
                .map(gameServerRepository::save).count();
        }
        if (taskRepository.findByName("updateGameServers").map(Task::getEnabled).orElse(false)) {
            updating = gameServerRepository
                .findByLastGameUpdateBeforeAndVersionLessThan(now.minusMinutes(5), steamCondenserService.getLatestVersion())
                .map(this::performGameUpdate).map(gameServerRepository::save).count();
        }
        ZonedDateTime next = now.plusMinutes(10);
        if (updating < 0) {
            log.debug("Update checks skipped. Next attempt at {}", next);
        } else if (updating == 0) {
            log.debug("All servers up-to-date. Next check at {}", next);
        } else {
            log.info("Performing game update on {} servers. Next check at {}", updating, next);
        }
        if (refreshed < 0) {
            log.debug("Status refresh skipped. Next attempt at {}", next);
        } else {
            log.debug("{} servers had their status refreshed. Next check at {}", refreshed, next);
        }
    }

    public GameServer refreshServerStatus(GameServer server) {
        if (server == null) {
            return null;
        }
        log.debug("Refreshing server status: {}", server.getName());
        SourceServer source = getSourceServer(server);
        if (source != null) {
            server.setStatusCheckDate(ZonedDateTime.now());
            Integer ping = steamCondenserService.ping(source);
            server.setPing(ping);
            if (ping >= 0) {
                server.setPlayers(steamCondenserService.players(source));
                Map<String, Object> info = steamCondenserService.info(source);
                Optional.ofNullable(info.get("maxPlayers")).map(this::safeParse).ifPresent(server::setMaxPlayers);
                Optional.ofNullable(info.get("gameVersion")).map(this::safeParse).ifPresent(server::setVersion);
                Optional.ofNullable(info.get("mapName")).map(Object::toString).ifPresent(server::setMapName);
                server.setTvPort(Optional.ofNullable(info.get("tvPort")).map(this::safeParse).orElse(0));
            }
        }
        return server;
    }

    private Integer safeParse(Object value) {
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.debug("Invalid format of value: {}", value);
            return null;
        }
    }

    private GameServer performGameUpdate(GameServer server) {
        // check player count, never update if players > 0
        SourceServer source = getSourceServer(server);
        if (source != null) {
            server.setPlayers(steamCondenserService.players(source));
        }
        if (server.getPlayers() > 0) {
            log.info("Server update for {} {} is on hold. Players connected: {}", server.getName(), server.getAddress(),
                server.getPlayers());
        } else {
            try {
                adminPanelService.upgrade(server.getSubId());
                server.setLastGameUpdate(ZonedDateTime.now());
            } catch (IOException e) {
                log.warn("Could not perform game update: {}", e.toString());
            }
        }
        return server;
    }

    public String rcon(String address, String command) {
        return gameServerRepository.findByAddress(address).map(server -> nonThrowingRcon(server, command))
            .orElseThrow(() -> new IllegalArgumentException("Bad address: " + address));
    }

    private String nonThrowingRcon(GameServer server, String command) {
        try {
            return rcon(server, command);
        } catch (RemoteAccessException | SteamCondenserException | TimeoutException e) {
            log.warn("Could not execute RCON command", e);
        }
        return null;
    }

    public String rcon(GameServer server, String command)
        throws RemoteAccessException, SteamCondenserException, TimeoutException {
        // strip "rcon " if exists
        int idx = command.indexOf("rcon ");
        if (idx >= 0) {
            command = command.substring(idx);
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Not a valid command");
        }
        SourceServer source = getSourceServer(server);
        try {
            return steamCondenserService.rcon(source, server.getRconPassword(), command);
        } catch (RemoteAccessException e) {
            // retrieve the latest rcon_password and retry
            refreshRconPassword(server);
            return steamCondenserService.rcon(source, server.getRconPassword(), command);
        }
    }

    public SourceServer getSourceServer(GameServer server) {
        return steamCondenserService.getSourceServer(server.getAddress());
    }

    public Map<String, String> getFTPConnectInfo(GameServer server) {
        Map<String, String> connectInfo = adminPanelService.getFTPConnectInfo(server.getSubId());
        SourceServer source = steamCondenserService.getSourceServer(server.getAddress());
        connectInfo.put("ftp-hostname", source.getIpAddresses().get(0).getHostAddress());
        return connectInfo;
    }

    public boolean isSecure(GameServer server) {
        return flagRepository.findByName("insecure").map(f -> server.getFlags().contains(f)).orElse(false);
    }

    public void addInsecureFlag(GameServer server) {
        server.getFlags().add(flagRepository.findByName("insecure").orElseGet(this::createInsecureFlag));
        gameServerRepository.save(server);
    }

    private Flag createInsecureFlag() {
        Flag insecure = new Flag();
        insecure.setName("insecure");
        insecure.setDescription("Do not use TLS on FTP connections");
        flagRepository.save(insecure);
        return insecure;
    }

}
