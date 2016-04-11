package com.ugcleague.ops.service;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.ugcleague.ops.config.Constants;
import com.ugcleague.ops.service.discord.AnnouncePresenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import javax.annotation.PostConstruct;
import java.util.SortedMap;
import java.util.stream.Collectors;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    private final HealthCheckRegistry healthCheckRegistry;
    private final AnnouncePresenter announcePresenter;
    private final RestOperations restTemplate;

    @Autowired
    public HealthCheckService(HealthCheckRegistry healthCheckRegistry, AnnouncePresenter announcePresenter, RestOperations restTemplate) {
        this.healthCheckRegistry = healthCheckRegistry;
        this.announcePresenter = announcePresenter;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    private void configure() {
        registerPingCheck("ugcleague.org", "http://ugcleague.org");
        registerPingCheck("ugcleague.net", "http://ugcleague.net");
    }

    public void registerPingCheck(final String name, final String url) {
        healthCheckRegistry.register(name, new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("User-Agent", Constants.USER_AGENT);
                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<>(headers), String.class);
                    log.debug("{} {}", response.getStatusCode(), url);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        return Result.healthy(response.getStatusCode().value() + " " + response.getStatusCode().getReasonPhrase());
                    } else {
                        return Result.unhealthy(response.getStatusCode().value() + " " + response.getStatusCode().getReasonPhrase());
                    }
                } catch (Exception e) {
                    log.warn("Failed to perform ping check", e);
                    return Result.unhealthy("Failed with exception");
                }
            }
        });
    }

    @Scheduled(cron = "0 */15 * * * ?")
    void periodicHealthCheck() {
        SortedMap<String, HealthCheck.Result> result = healthCheckRegistry.runHealthChecks();
        String failing = result.entrySet().stream()
            .filter(e -> !e.getValue().isHealthy())
            .map(e -> e.getKey() + ": *" + e.getValue().getMessage() + "*")
            .collect(Collectors.joining("\n"));
        if (!failing.isEmpty()) {
            announcePresenter.announce("issues", "Failing health checks\n" + failing);
        }
    }
}
