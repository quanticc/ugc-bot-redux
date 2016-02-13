package com.ugcleague.ops.service;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.ugcleague.ops.domain.Flag;
import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.event.GameServerDeathEvent;
import com.ugcleague.ops.event.GameUpdateCompletedEvent;
import com.ugcleague.ops.event.GameUpdateDelayedEvent;
import com.ugcleague.ops.event.GameUpdateStartedEvent;
import com.ugcleague.ops.repository.FlagRepository;
import com.ugcleague.ops.repository.GameServerRepository;
import com.ugcleague.ops.service.util.*;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Transactional
public class GameServerService {

    private static final Logger log = LoggerFactory.getLogger(GameServerService.class);
    private static final String INSECURE_FLAG = "insecure";

    private final AdminPanelService adminPanelService;
    private final SteamCondenserService steamCondenserService;
    private final GameServerRepository gameServerRepository;
    private final FlagRepository flagRepository;
    private final ApplicationEventPublisher publisher;
    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    private final UpdateResultMap updateResultMap = new UpdateResultMap();
    private final DeadServerMap deadServerMap = new DeadServerMap();

    @Autowired
    public GameServerService(GameServerRepository gameServerRepository, SteamCondenserService steamCondenserService,
                             AdminPanelService adminPanelService, FlagRepository flagRepository,
                             ApplicationEventPublisher publisher, MetricRegistry metricRegistry,
                             HealthCheckRegistry healthCheckRegistry) {
        this.gameServerRepository = gameServerRepository;
        this.steamCondenserService = steamCondenserService;
        this.adminPanelService = adminPanelService;
        this.flagRepository = flagRepository;
        this.publisher = publisher;
        this.metricRegistry = metricRegistry;
        this.healthCheckRegistry = healthCheckRegistry;
    }

    @PostConstruct
    private void configure() {
        if (gameServerRepository.count() == 0) {
            refreshServerDetails();
        }
        healthCheckRegistry.register("gameServers.pingCheck", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                long count = gameServerRepository.count();
                List<GameServer> nonResponsiveServers = gameServerRepository.findByPingLessThanEqual(0);
                if (nonResponsiveServers.isEmpty()) {
                    return Result.healthy("All " + count + " game servers are OK");
                } else {
                    String result = nonResponsiveServers.stream()
                        .map(GameServer::getName).collect(Collectors.joining(", "));
                    return Result.unhealthy("Unresponsive: " + result);
                }
            }
        });
        healthCheckRegistry.register("gameServers.validRconCheck", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                List<GameServer> rconlessServers = gameServerRepository.findByRconPasswordIsNull();
                if (rconlessServers.isEmpty()) {
                    return Result.healthy("All servers have a valid RCON password");
                } else {
                    String result = rconlessServers.stream()
                        .map(GameServer::getName).collect(Collectors.joining(", "));
                    return Result.unhealthy("Missing passwords: " + result);
                }
            }
        });
        healthCheckRegistry.register("gameServers.version", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                List<GameServer> outdatedServers = findOutdatedServers();
                if (outdatedServers.isEmpty()) {
                    return Result.healthy("All servers have the latest TF2 version");
                } else {
                    String result = outdatedServers.stream()
                        .map(GameServer::getName).collect(Collectors.joining(", "));
                    return Result.unhealthy("Outdated: " + result);
                }
            }
        });
        for (GameServer server : gameServerRepository.findAll()) {
            metricRegistry.register("gameServers." + server.getShortName() + ".ping",
                new Histogram(new SlidingTimeWindowReservoir(1, TimeUnit.DAYS)));
            metricRegistry.register("gameServers." + server.getShortName() + ".players",
                new Histogram(new SlidingTimeWindowReservoir(1, TimeUnit.DAYS)));
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
        GameServer server = gameServerRepository.findByAddress(address).orElseGet(this::newGameServer);
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

    public void refreshRconPasswords() {
        log.debug("==== Refreshing RCON server passwords ====");
        ZonedDateTime now = ZonedDateTime.now();
        // refreshing passwords of expired servers since they auto restart and change password
        long count = gameServerRepository.findByRconRefreshNeeded(now).stream()
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
        log.debug("Refreshing RCON password data: {}", server.getName());
        try {
            Map<String, String> result = adminPanelService.getServerConfig(server.getSubId());
            server.setRconPassword(result.get("rcon_password")); // can be null if the server is bugged
            server.setSvPassword(result.get("sv_password")); // can be null if the server is bugged
            server.setLastRconDate(ZonedDateTime.now());
            return gameServerRepository.save(server);
        } catch (RemoteAccessException | IOException e) {
            log.warn("Could not refresh the RCON password", e.toString());
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
        long refreshed = gameServerRepository.findAll().parallelStream().map(this::refreshServerStatus)
            .map(gameServerRepository::save).count();
        long updating = findOutdatedServers().stream().map(this::performGameUpdate)
            .map(gameServerRepository::save).count();
        if (updating == 0) {
            log.debug("All servers up-to-date");
            if (!updateResultMap.isEmpty()) {
                // all servers are up to date, report and then clear the progress map
                publisher.publishEvent(new GameUpdateCompletedEvent(updateResultMap.duplicate()).toVersion(latestVersion));
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
        long failingCount = deadServerMap.values().stream()
            .map(info -> info.getAttempts().get()).filter(i -> i >= 5).count();
        int maxFailedAttempts = deadServerMap.values().stream()
            .map(info -> info.getAttempts().get()).reduce(0, Integer::max);
        if (failingCount > 5) {
            log.warn("{} are unresponsive after the last 5 checks (max failures {})", failingCount, maxFailedAttempts);
            publisher.publishEvent(new GameServerDeathEvent(deadServerMap.duplicate()));
        }
    }

    public GameServer refreshServerStatus(GameServer server) {
        if (server == null) {
            return null;
        }
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
                deadServerMap.put(server, new DeadServerInfo(server));
            } else {
                deadServerMap.computeIfAbsent(server, DeadServerInfo::new).getAttempts().incrementAndGet();
            }
            metricRegistry.histogram("gameServers." + server.getShortName() + ".ping").update(server.getPing());
            metricRegistry.histogram("gameServers." + server.getShortName() + ".players").update(server.getPlayers());
        }
        return server;
    }

    public DeadServerMap getDeadServerMap() {
        return deadServerMap;
    }

    public Map<String, Object> refreshServerStatus(SourceServer source) {
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
            server.setPing(steamCondenserService.ping(source));
            server.setPlayers(steamCondenserService.players(source));
        }
        if (updateResultMap.isEmpty()) {
            publisher.publishEvent(new GameUpdateStartedEvent(updateResultMap));
        }
        UpdateResult result = updateResultMap.computeIfAbsent(server, k -> new UpdateResult());
        if (server.getPlayers() > 0) {
            log.info("Server update for {} {} is on hold. Players connected: {}", server.getName(), server.getAddress(),
                server.getPlayers());
            if (result.getLastRconAnnounce().get().isBefore(Instant.now().minusSeconds(60 * 20))) {
                try {
                    rcon(server, Optional.empty(), "Game update on hold until all players leave the server");
                    result.getLastRconAnnounce().set(Instant.now());
                } catch (SteamCondenserException | TimeoutException ignored) {
                }
            }
            result.getAttempts().incrementAndGet();
        } else if (server.getPing() < 0) {
            // timed out servers might be down due to a large update
            log.info("Server update for {} {} is on hold. It appears to be offline", server.getName(), server.getAddress());
            result.getAttempts().incrementAndGet();
        } else {
            try {
                if (adminPanelService.upgrade(server.getSubId())) {
                    // save status so it's signalled as "dead server" during restart
                    deadServerMap.computeIfAbsent(server, DeadServerInfo::new).getAttempts().addAndGet(10);
                    server.setLastGameUpdate(ZonedDateTime.now());
                }
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
            log.warn("Could not execute RCON command", e);
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

    private String refreshPasswordAndGet(GameServer server) {
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
        Map<String, String> connectInfo = adminPanelService.getFTPConnectInfo(server.getSubId());
        SourceServer source = steamCondenserService.getSourceServer(server.getAddress());
        connectInfo.put("ftp-hostname", source.getIpAddresses().get(0).getHostAddress());
        return connectInfo;
    }

    public boolean isSecure(GameServer server) {
        GameServer s = gameServerRepository.findOneWithEagerRelationships(server.getId()).get();
        return flagRepository.findByName(INSECURE_FLAG).map(f -> s.getFlags().contains(f)).orElse(false);
    }

    public GameServer addInsecureFlag(GameServer server) {
        GameServer s = gameServerRepository.findOneWithEagerRelationships(server.getId()).get();
        s.getFlags().add(flagRepository.findByName(INSECURE_FLAG).orElseGet(this::createInsecureFlag));
        return gameServerRepository.save(server);
    }

    public GameServer removeInsecureFlag(GameServer server) {
        GameServer s = gameServerRepository.findOneWithEagerRelationships(server.getId()).get();
        Flag insecure = flagRepository.findByName(INSECURE_FLAG).orElseGet(this::createInsecureFlag);
        s.getFlags().remove(insecure);
        return gameServerRepository.save(server);
    }

    private Flag createInsecureFlag() {
        Flag insecure = new Flag();
        insecure.setName("insecure");
        insecure.setDescription("Do not use TLS on FTP connections");
        flagRepository.save(insecure);
        return insecure;
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

    public List<GameServer> findAllEagerly() {
        return gameServerRepository.findAllWithEagerRelationships();
    }

    public int attemptRestart(GameServer server) {
        server = refreshServerStatus(server);
        int players = server.getPlayers();
        if (players > 0) {
            log.info("Not restarting server due to players present: {}", players);
            return server.getPlayers();
        } else {
            try {
                boolean success = adminPanelService.restart(server.getSubId());
                if (success) {
                    // save status so it's signalled as "dead server" during restart
                    deadServerMap.computeIfAbsent(server, DeadServerInfo::new).getAttempts().addAndGet(10);
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

    public GameServer save(GameServer gameServer) {
        return gameServerRepository.save(gameServer);
    }
}
