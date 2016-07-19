package com.ugcleague.ops.service;

import com.codahale.metrics.MetricRegistry;
import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.ugcleague.ops.domain.document.GameServer;
import com.ugcleague.ops.event.GameUpdateCompletedEvent;
import com.ugcleague.ops.event.GameUpdateDelayedEvent;
import com.ugcleague.ops.event.GameUpdateStartedEvent;
import com.ugcleague.ops.repository.mongo.GameServerRepository;
import com.ugcleague.ops.service.util.MetricNames;
import com.ugcleague.ops.service.util.SourceServer;
import com.ugcleague.ops.service.util.UpdateResult;
import com.ugcleague.ops.service.util.UpdateResultMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.remoting.RemoteAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Transactional
public class GameServerService {

    private static final Logger log = LoggerFactory.getLogger(GameServerService.class);

    private final AdminPanelService adminPanelService;
    private final SteamCondenserService steamCondenserService;
    private final GameServerRepository gameServerRepository;
    private final ApplicationEventPublisher publisher;
    private final MetricRegistry metricRegistry;

    private final UpdateResultMap updateResultMap = new UpdateResultMap();
    //private final DeadServerMap deadServerMap = new DeadServerMap();
    private final Map<String, String> availableMods = new LinkedHashMap<>();

    @Autowired
    public GameServerService(GameServerRepository gameServerRepository, SteamCondenserService steamCondenserService,
                             AdminPanelService adminPanelService, ApplicationEventPublisher publisher,
                             MetricRegistry metricRegistry) {
        this.gameServerRepository = gameServerRepository;
        this.steamCondenserService = steamCondenserService;
        this.adminPanelService = adminPanelService;
        this.publisher = publisher;
        this.metricRegistry = metricRegistry;
    }

    @PostConstruct
    private void configure() {
//        if (gameServerRepository.count() == 0) {
//            refreshServerDetails();
//        }

        availableMods.put("metamod", "5171820e847b6");
        availableMods.put("sourcemod", "517176e485ca6");
        availableMods.put("sourcemod-update", "51796947838dd");
    }

    public void refreshServerDetails() {
        try {
            adminPanelService.getServerDetails().forEach(this::refreshServerDetails);
        } catch (RemoteAccessException | IOException e) {
            log.warn("Could not retrieve server details from remote panel", e);
        }
    }

    private void refreshServerDetails(String address, Map<String, String> data) {
        GameServer server = gameServerRepository.findByAddress(address).orElseGet(this::newGameServer);
        server.setId(data.get("SUBID"));
        server.setAddress(address);
        server.setName(data.get("name"));
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
        server.setLastValidPing(epoch);
        return server;
    }

    public void refreshRconPasswords() {
        log.debug("==== Refreshing RCON server passwords ====");
        // refreshing passwords of expired servers since they auto restart and change password
        long count = gameServerRepository.findByRconRefreshNeeded().stream()
            .map(this::refreshRconPassword).filter(u -> u != null).count();
        log.info("{} servers updated their RCON passwords", count);
    }

    /**
     * Crawl through the remote server panel looking for the rcon_password of the given <code>server</code>.
     *
     * @param server a GameServer
     * @return the updated GameServer, or <code>null</code> if the updated rcon_password could not be retrieved
     */
    public GameServer refreshRconPassword(GameServer server) {
        if (server == null) {
            return null;
        }
        log.debug("Refreshing RCON data: {}", server.getShortNameAndAddress());
        try {
            // TODO: signal abnormal conditions through incidents instead of just logging
            Map<String, String> result = adminPanelService.getServerConfig(server.getId());
            if (!result.getOrDefault("result", "").equals("")) {
                log.warn("RCON refresh failed for {}: {}", server.getShortNameAndAddress(), result.get("result"));
            }
            server.setRconPassword(result.get("rcon_password")); // can be null if the server is bugged
            server.setSvPassword(result.get("sv_password")); // can be null if the server is bugged
            if (server.getRconPassword() == null || server.getSvPassword() == null) {
                log.warn("RCON refresh with invalid data for {}", server.getShortNameAndAddress());
            }
            server.setLastRconDate(ZonedDateTime.now());
            return gameServerRepository.save(server);
        } catch (RemoteAccessException | IOException e) {
            log.warn("Could not refresh RCON data for {}: {}", server.getShortNameAndAddress(), e.toString());
        }
        return null;
    }

    public List<GameServer> findOutdatedServers() {
        int latestVersion = steamCondenserService.getLatestVersion();
        return gameServerRepository.findByVersionLessThan(latestVersion);
    }

    @Async
    public void updateGameServers() {
        log.debug("==== Refreshing server status ====");
        int latestVersion = steamCondenserService.getLatestVersion();
        long refreshed = gameServerRepository.findAll().stream().map(this::refreshServerStatus)
            .map(gameServerRepository::save).count();
        long updating = findOutdatedServers().stream().map(this::performGameUpdate)
            .map(gameServerRepository::save).count();
        if (updating == 0) {
            log.debug("All servers up-to-date");
            if (!updateResultMap.isEmpty()) {
                // all servers are up to date, report and then clear the progress map
                publisher.publishEvent(new GameUpdateCompletedEvent(updateResultMap.duplicate(), latestVersion));
                updateResultMap.clear();
            }
        } else {
            log.info("Update is pending on {} servers", updating);
            List<GameServer> failed = updateResultMap.getSlowUpdates(10);
            if (!failed.isEmpty()) {
                // there are servers holding out the update
                publisher.publishEvent(new GameUpdateDelayedEvent(updateResultMap).causedBy(failed));
            }
        }
        log.debug("{} servers had their status refreshed", refreshed);
//        long failingCount = deadServerMap.values().stream()
//            .map(info -> info.getAttempts().get()).filter(i -> i >= 5).count();
//        int maxFailedAttempts = deadServerMap.values().stream()
//            .map(info -> info.getAttempts().get()).reduce(0, Integer::max);
//        if (failingCount > 5) {
//            log.warn("{} are unresponsive after the last 5 checks (max failures {})", failingCount, maxFailedAttempts);
//            publisher.publishEvent(new GameServerDeathEvent(deadServerMap.duplicate()));
//        }
    }

    public GameServer refreshServerStatus(GameServer server) {
        return refreshServerStatus(server, false);
    }

    public GameServer refreshServerStatus(GameServer server, boolean invalidateCachedValues) {
        if (server == null) {
            return null;
        }
        SourceServer source = getSourceServer(server);
        if (source != null) {
            server.setStatusCheckDate(ZonedDateTime.now());
            Integer ping = invalidateCachedValues ? pingAndLogIncident(server) : pingGaugeValue(server);
            server.setPing(ping);
            if (ping >= 0) {
                server.setPlayers(invalidateCachedValues ? playerCountAndLogIncident(server) : playersGaugeValue(server));
                Map<String, Object> info = steamCondenserService.info(source);
                Optional.ofNullable(info.get("maxPlayers")).map(this::safeParse).ifPresent(server::setMaxPlayers);
                Optional.ofNullable(info.get("gameVersion")).map(this::safeParse).ifPresent(server::setVersion);
                Optional.ofNullable(info.get("mapName")).map(Object::toString).ifPresent(server::setMapName);
                server.setTvPort(Optional.ofNullable(info.get("tvPort")).map(this::safeParse).orElse(0));
                //deadServerMap.put(server, new DeadServerInfo(server));
            }

            /*else {
                deadServerMap.computeIfAbsent(server, DeadServerInfo::new).getAttempts().incrementAndGet();
            }*/
        }
        return server;
    }

    private Integer pingGaugeValue(GameServer server) {
        Object value = metricRegistry.getGauges().get(MetricNames.gameServerPing(server)).getValue();
        return value == null ? -1 : (Integer) value;
    }

    private Integer playersGaugeValue(GameServer server) {
        Object value = metricRegistry.getGauges().get(MetricNames.gameServerPlayers(server)).getValue();
        return value == null ? -1 : (Integer) value;
    }

    public Integer getServerPing(GameServer server) {
        // refresh ping and player count but don't save to DB
        if (steamCondenserService.containsSourceServer(server.getAddress())) {
            return pingAndLogIncident(server);
        }
        return null;
    }

    public Integer getServerPlayerCount(GameServer server) {
        // refresh ping and player count but don't save to DB
        if (steamCondenserService.containsSourceServer(server.getAddress())) {
            return playerCountAndLogIncident(server);
        }
        return null;
    }

    private Integer pingAndLogIncident(GameServer server) {
        Integer ping = steamCondenserService.ping(getSourceServer(server));
        if (ping < 0) {
            log.warn("Last ping to {} failed", server.getShortNameAndAddress());
        }
        return ping;
    }

    private Integer playerCountAndLogIncident(GameServer server) {
        Integer count = steamCondenserService.players(getSourceServer(server));
        if (count < 0) {
            log.warn("Last player retrieval of {} failed", server.getShortNameAndAddress());
        }
        return count;
    }

//    public DeadServerMap getDeadServerMap() {
//        return deadServerMap;
//    }

    public Map<String, Object> refreshSourceServerStatus(SourceServer source) {
        Map<String, Object> map = new LinkedHashMap<>();
        int ping = steamCondenserService.ping(source);
        map.put("ping", ping);
        if (ping >= 0) {
            map.put("players", steamCondenserService.players(source));
            Map<String, Object> info = steamCondenserService.info(source);
            map.putAll(info); // "maxPlayers", "gameVersion", "mapName", "tvPort"
        }
        return map;
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
            server.setPing(pingAndLogIncident(server));
            server.setPlayers(playerCountAndLogIncident(server));
        }
        if (updateResultMap.isEmpty()) {
            publisher.publishEvent(new GameUpdateStartedEvent(updateResultMap));
        }
        UpdateResult result = updateResultMap.computeIfAbsent(server, k -> new UpdateResult());
        if (server.getPlayers() > 0) {
            log.info("Server update for {} is on hold. Players connected: {}", server.getShortNameAndAddress(),
                server.getPlayers());
            if (result.getLastRconAnnounce().get().isBefore(Instant.now().minusSeconds(60 * 20))) {
                try {
                    rcon(server, Optional.empty(), "Game update on hold until all players leave the server");
                    result.getLastRconAnnounce().set(Instant.now());
                } catch (SteamCondenserException | TimeoutException ignored) {
                }
            }
            result.getAttempts().incrementAndGet();
        } else if (server.getPing() < 0 && result.getAttempts().get() < 3) {
            // timed out servers might be down due to a large update
            log.info("Server update for {} is on hold. It appears to be offline", server.getShortNameAndAddress());
            result.getAttempts().incrementAndGet();
        } else {
            if (result.getAttempts().get() > 0) {
                log.warn("Proceeding with game update of {} after {} failed attempts", server.getShortNameAndAddress(), result.getAttempts().get());
            }
            try {
                AdminPanelService.Result response = adminPanelService.upgrade(server.getId());
                // save status so it's signalled as "dead server" during restart
                // TODO: use incidents instead
//                deadServerMap.computeIfAbsent(server, DeadServerInfo::new).getAttempts()
//                    .addAndGet(response == AdminPanelService.Result.SUCCESSFUL ? 1 : 5);
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
            return rcon(server, Optional.empty(), command);
        } catch (RemoteAccessException | SteamCondenserException | TimeoutException e) {
            log.warn("Could not execute RCON command '{}' on {}: {}", server.getShortNameAndAddress(),
                command, e.toString());
        }
        return null;
    }

    public String rcon(GameServer server, Optional<String> password, String command) throws TimeoutException, SteamCondenserException {
        // strip "rcon " if exists
        final String cmd = cleanCommand(command);
        // get the socket
        SourceServer source = getSourceServer(server);
        // 1. try with given password
        // 2. if failed, try with cached pasword
        // 3. if failed, get from web panel
        return password.map(pw -> wrappedRcon(source, pw, cmd))
            .orElseGet(() -> Optional.ofNullable(wrappedRcon(source, server.getRconPassword(), cmd))
                .orElseGet(() -> Optional.ofNullable(wrappedRcon(source, refreshPasswordAndGet(server), cmd))
                    .orElse("Could not execute command")));
    }

    public String rcon(SourceServer source, String password, String command) throws TimeoutException, SteamCondenserException {
        // strip "rcon " if exists
        final String cmd = cleanCommand(command);
        // get the socket
        return Optional.ofNullable(wrappedRcon(source, password, cmd))
            .orElse("Could not execute command");
    }

    public String refreshPasswordAndGet(GameServer server) {
        refreshRconPassword(server);
        return server.getRconPassword();
    }

    private String wrappedRcon(SourceServer source, String password, String command) {
        try {
            return steamCondenserService.rcon(source, password, command);
        } catch (RemoteAccessException | SteamCondenserException | TimeoutException e) {
            return null;
        }
    }

    private String cleanCommand(String command) {
        String prefix = "rcon ";
        if (command.startsWith(prefix)) {
            command = command.substring(prefix.length());
        }
        return command;
    }

    public SourceServer getSourceServer(GameServer server) {
        return steamCondenserService.getSourceServer(server.getAddress());
    }

    public SourceServer getSourceServer(String address) {
        return steamCondenserService.getSourceServer(address);
    }

    public Map<String, String> getFTPConnectInfo(GameServer server) {
        Map<String, String> connectInfo = adminPanelService.getFTPConnectInfo(server.getId());
        SourceServer source = steamCondenserService.getSourceServer(server.getAddress());
        connectInfo.put("ftp-hostname", source.getIpAddresses().get(0).getHostAddress());
        return connectInfo;
    }

    public List<GameServer> findServers(String k) {
        String key = k.trim().toLowerCase();
        return gameServerRepository.findAll().stream()
            .filter(s -> isClaimedCase(s, key) || isUnclaimedCase(s, key)
                || containsName(s, key) || isShortName(s, key) || hasAddressLike(s, key))
            .collect(Collectors.toList());
    }

    public List<GameServer> findServersMultiple(List<String> input) {
        List<String> keys = input.stream().map(k -> k.trim().toLowerCase()).collect(Collectors.toList());
        return gameServerRepository.findAll().stream()
            .filter(s -> isClaimedCase(s, keys) || isUnclaimedCase(s, keys)
                || containsName(s, keys) || isShortName(s, keys) || hasAddressLike(s, keys))
            .collect(Collectors.toList());
    }

    private boolean isUnclaimedCase(GameServer s, List<String> keys) {
        ZonedDateTime now = ZonedDateTime.now();
        return keys.stream().anyMatch(k -> k.equals("unclaimed"))
            && now.isAfter(s.getExpireDate());
    }

    private boolean isClaimedCase(GameServer s, List<String> keys) {
        ZonedDateTime now = ZonedDateTime.now();
        return keys.stream().anyMatch(k -> k.equals("claimed"))
            && now.isBefore(s.getExpireDate());
    }

    private boolean isUnclaimedCase(GameServer s, String key) {
        ZonedDateTime now = ZonedDateTime.now();
        return key.equals("unclaimed") && now.isAfter(s.getExpireDate());
    }

    private boolean isClaimedCase(GameServer s, String key) {
        ZonedDateTime now = ZonedDateTime.now();
        return key.equals("claimed") && now.isBefore(s.getExpireDate());
    }

    private boolean containsName(GameServer server, List<String> keys) {
        return keys.stream().anyMatch(k -> containsName(server, k));
    }

    private boolean isShortName(GameServer server, List<String> keys) {
        return keys.stream().anyMatch(k -> isShortName(server, k));
    }

    private boolean hasAddressLike(GameServer server, List<String> keys) {
        return keys.stream().anyMatch(k -> hasAddressLike(server, k));
    }

    private boolean containsName(GameServer server, String key) {
        return server.getName().trim().toLowerCase().contains(key);
    }

    private boolean isShortName(GameServer server, String key) {
        return key.equals(toShortName(server));
    }

    private boolean hasAddressLike(GameServer server, String key) {
        return server.getAddress().startsWith(key);
    }

    public String toShortName(GameServer server) {
        return server.getShortName();
    }

    public List<GameServer> findAll() {
        return gameServerRepository.findAll();
    }

    private boolean isEmpty(GameServer server) {
        server = refreshServerStatus(server);
        return server.getPlayers() == 0;
    }

    public String getServerConsole(GameServer server) throws IOException {
        return adminPanelService.getServerConsole(server.getId());
    }

    public int attemptRestart(GameServer server) {
        if (!isEmpty(server)) {
            int count = server.getPlayers();
            log.info("Not restarting server {} due to players present: {}", server.getShortNameAndAddress(), count);
            return count;
        } else {
            try {
                // TODO: improve return
                AdminPanelService.Result response = adminPanelService.restart(server.getId());
                boolean success = (response == AdminPanelService.Result.SUCCESSFUL);
                if (success) {
                    // save status so it's signalled as "dead server" during restart
//                    deadServerMap.computeIfAbsent(server, DeadServerInfo::new).getAttempts().addAndGet(10);
                    return 0;
                } else {
                    return -1;
                }
            } catch (IOException e) {
                log.warn("Could not restart server: {}", e.toString());
                return -5;
            }
        }
    }

    public int attemptUpdate(GameServer server) {
        if (!isEmpty(server)) {
            int count = server.getPlayers();
            log.info("Not upgrading server due to players present: {}", count);
            return count;
        } else {
            try {
                // TODO: improve return
                AdminPanelService.Result response = adminPanelService.upgrade(server.getId());
                boolean success = (response == AdminPanelService.Result.SUCCESSFUL);
                if (success) {
                    // save status so it's signalled as "dead server" during restart
//                    deadServerMap.computeIfAbsent(server, DeadServerInfo::new).getAttempts().addAndGet(10);
                    return 0;
                } else {
                    return -1;
                }
            } catch (IOException e) {
                log.warn("Could not upgrade server: {}", e.toString());
                return -5;
            }
        }
    }

    public int attemptModInstall(GameServer server, String modName) {
        if (!availableMods.containsKey(modName)) {
            log.warn("Invalid mod name: {}", modName);
            return -6;
        }
        if (!isEmpty(server)) {
            int count = server.getPlayers();
            log.info("Not installing mod due to players present: {}", count);
            return count;
        } else {
            try {
                // TODO: improve return
                AdminPanelService.Result response = adminPanelService.installMod(server.getId(), availableMods.get(modName));
                boolean success = (response == AdminPanelService.Result.SUCCESSFUL);
                if (success) {
                    // save status so it's signalled as "dead server" during restart
//                    deadServerMap.computeIfAbsent(server, DeadServerInfo::new).getAttempts().addAndGet(10);
                    return 0;
                } else {
                    return -1;
                }
            } catch (IOException e) {
                log.warn("Could not install mod to server: {}", e.toString());
                return -5;
            }
        }
    }

    public Map<String, String> getAvailableMods() {
        return availableMods;
    }

    public GameServer save(GameServer gameServer) {
        return gameServerRepository.save(gameServer);
    }

    public GameServer addInsecureFlag(GameServer server) {
        server.setSecure(false);
        return gameServerRepository.save(server);
    }

    public GameServer removeInsecureFlag(GameServer server) {
        server.setSecure(true);
        return gameServerRepository.save(server);
    }
}
