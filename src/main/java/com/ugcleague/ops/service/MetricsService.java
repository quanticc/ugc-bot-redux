package com.ugcleague.ops.service;

import com.codahale.metrics.*;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.ugcleague.ops.domain.document.GameServer;
import com.ugcleague.ops.domain.document.Incident;
import com.ugcleague.ops.repository.mongo.GameServerRepository;
import com.ugcleague.ops.service.util.MetricNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;
    private final GameServerService gameServerService;
    private final GameServerRepository gameServerRepository;
    private final DiscordService discordService;
    private final IncidentService incidentService;

    @Autowired
    public MetricsService(MetricRegistry metricRegistry, HealthCheckRegistry healthCheckRegistry,
                          GameServerService gameServerService, GameServerRepository gameServerRepository, DiscordService discordService, IncidentService incidentService) {
        this.metricRegistry = metricRegistry;
        this.healthCheckRegistry = healthCheckRegistry;
        this.gameServerService = gameServerService;
        this.gameServerRepository = gameServerRepository;
        this.discordService = discordService;
        this.incidentService = incidentService;
    }

    @PostConstruct
    private void configure() {
        log.debug("Initializing metric collection");
        initGameServerHealthChecks();
        initGameServerMetrics();
        initDiscordMetrics();
        initDiscordHealthChecks();
    }

    private void initDiscordMetrics() {
        metricRegistry.register(MetricNames.DISCORD_WS_RESPONSE_HISTOGRAM,
            new Histogram(new UniformReservoir()));
        metricRegistry.register(MetricNames.DISCORD_WS_RESTARTS, new Counter());
        metricRegistry.register(MetricNames.DISCORD_WS_RESPONSE, (Gauge<Long>) this::getMeanResponseTime);
        metricRegistry.register(MetricNames.DISCORD_USERS_JOINED, (Gauge<Long>) discordService::getUserCount);
        metricRegistry.register(MetricNames.DISCORD_USERS_CONNECTED, (Gauge<Long>) discordService::getConnectedUserCount);
        metricRegistry.register(MetricNames.DISCORD_USERS_ONLINE, (Gauge<Long>) discordService::getOnlineUserCount);
    }

    private void initDiscordHealthChecks() {
        healthCheckRegistry.register(MetricNames.HEALTH_DISCORD_WS, new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                Histogram responseTime = metricRegistry.histogram(MetricNames.DISCORD_WS_RESPONSE_HISTOGRAM);
                Counter restartCounter = metricRegistry.counter(MetricNames.DISCORD_WS_RESTARTS);
                Optional<Incident> incident = incidentService.getLastIncidentFromGroup(IncidentService.DISCORD_RESTART);
                ZonedDateTime time = incident.isPresent() ? incident.get().getCreatedDate() : null;
                String reason = incident.isPresent() ? incident.get().getName() : null;
                long mean = (long) responseTime.getSnapshot().getMean();
                long restarts = restartCounter.getCount();
                if (restarts > 0) {
                    return Result.unhealthy(String.format("%d restart%s, last one on %s (%s). Mean response time: %dms",
                        restarts, restarts == 1 ? "" : "s", time, reason, mean));
                } else {
                    return Result.healthy("Mean response time: " + mean + "ms");
                }
            }
        });
    }

    private void initGameServerMetrics() {
        Map<String, List<Gauge<Integer>>> pingGaugesPerRegion = new LinkedHashMap<>();
        Map<String, List<Gauge<Integer>>> playersGaugesPerRegion = new LinkedHashMap<>();
        for (GameServer server : gameServerRepository.findAll()) {
            Gauge<Integer> ping = metricRegistry.register(MetricNames.gameServerPing(server), new CachedGauge<Integer>(5, TimeUnit.MINUTES) {
                @Override
                protected Integer loadValue() {
                    // truncate negative values that signal abnormal conditions
                    Integer value = gameServerService.getServerPing(server);
                    return value != null && value > 0 ? value : null;
                }
            });
            Gauge<Integer> players = metricRegistry.register(MetricNames.gameServerPlayers(server), new CachedGauge<Integer>(5, TimeUnit.MINUTES) {
                @Override
                protected Integer loadValue() {
                    // truncate negative values that signal abnormal conditions
                    Integer value = gameServerService.getServerPlayerCount(server);
                    return value != null && value >= 0 ? value : null;
                }
            });
            String region = server.getShortName().substring(0, 3);
            pingGaugesPerRegion.computeIfAbsent(region, k -> new ArrayList<>()).add(ping);
            playersGaugesPerRegion.computeIfAbsent(region, k -> new ArrayList<>()).add(players);
        }

        // compute gs.ping.* for average region-wide ping
        for (Map.Entry<String, List<Gauge<Integer>>> entry : pingGaugesPerRegion.entrySet()) {
            String region = entry.getKey();
            metricRegistry.register("gs.ping." + region, new CachedGauge<Double>(1, TimeUnit.MINUTES) {
                @Override
                protected Double loadValue() {
                    return entry.getValue().stream()
                        .map(Gauge::getValue)
                        .filter(v -> v != null)
                        .collect(Collectors.averagingInt(v -> v));
                }
            });
        }

        // compute gs.players.count.* and avg.* for player total and average per region
        List<Gauge<Integer>> playerCounts = new ArrayList<>();
        List<Gauge<Double>> playerAverages = new ArrayList<>();
        for (Map.Entry<String, List<Gauge<Integer>>> entry : playersGaugesPerRegion.entrySet()) {
            String region = entry.getKey();
            Gauge<Integer> sum = metricRegistry.register("gs.players.count." + region, new CachedGauge<Integer>(1, TimeUnit.MINUTES) {
                @Override
                protected Integer loadValue() {
                    return entry.getValue().stream()
                        .map(Gauge::getValue)
                        .filter(v -> v != null)
                        .collect(Collectors.summingInt(v -> v));
                }
            });
            Gauge<Double> average = metricRegistry.register("gs.players.avg." + region, new CachedGauge<Double>(1, TimeUnit.MINUTES) {
                @Override
                protected Double loadValue() {
                    return entry.getValue().stream()
                        .map(Gauge::getValue)
                        .filter(v -> v != null)
                        .collect(Collectors.averagingInt(v -> v));
                }
            });
            playerCounts.add(sum);
            playerAverages.add(average);
        }

        // compute gs.players.count and avg for worldwide player count and average player count
        metricRegistry.register("gs.players.count", new CachedGauge<Integer>(1, TimeUnit.MINUTES) {
            @Override
            protected Integer loadValue() {
                return playerCounts.stream().map(Gauge::getValue)
                    .filter(v -> v != null)
                    .collect(Collectors.summingInt(v -> v));
            }
        });
        metricRegistry.register("gs.players.avg", new CachedGauge<Double>(1, TimeUnit.MINUTES) {
            @Override
            protected Double loadValue() {
                return playerAverages.stream().map(Gauge::getValue)
                    .filter(v -> v != null)
                    .collect(Collectors.averagingDouble(v -> v));
            }
        });
    }

    private void initGameServerHealthChecks() {
        healthCheckRegistry.register("GameServers.PingCheck", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                long count = gameServerRepository.count();
                List<GameServer> nonResponsiveServers = gameServerRepository.findByPingLessThanEqual(0);
                if (nonResponsiveServers.isEmpty()) {
                    return Result.healthy("All " + count + " game servers are OK");
                } else {
                    String result = nonResponsiveServers.stream()
                        .map(GameServer::getShortName).collect(Collectors.joining(", "));
                    return Result.unhealthy("Unresponsive: " + result);
                }
            }
        });
        healthCheckRegistry.register("GameServers.ValidRconCheck", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                List<GameServer> rconlessServers = gameServerRepository.findByRconPasswordIsNull();
                if (rconlessServers.isEmpty()) {
                    return Result.healthy("All servers have a valid RCON password");
                } else {
                    String result = rconlessServers.stream()
                        .map(GameServer::getShortName).collect(Collectors.joining(", "));
                    return Result.unhealthy("Missing RCON passwords: " + result);
                }
            }
        });
        healthCheckRegistry.register("GameServers.GameVersionCheck", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                List<GameServer> outdatedServers = gameServerService.findOutdatedServers();
                if (outdatedServers.isEmpty()) {
                    return Result.healthy("All servers have the latest TF2 version");
                } else {
                    String result = outdatedServers.stream()
                        .map(GameServer::getShortName).collect(Collectors.joining(", "));
                    return Result.unhealthy("Outdated: " + result);
                }
            }
        });
    }

    private long getMeanResponseTime() {
        Histogram responseTime = metricRegistry.histogram(MetricNames.DISCORD_WS_RESPONSE_HISTOGRAM);
        return (long) responseTime.getSnapshot().getMean();
    }

    @Scheduled(cron = "*/10 * * * * *")
    void checkResponseTime() { // scheduled each 10 seconds
        Histogram responseTime = metricRegistry.histogram(MetricNames.DISCORD_WS_RESPONSE_HISTOGRAM);
        long millis = discordService.getResponseTime();
        if (millis > 0) {
            responseTime.update(millis);
        }
    }
}
