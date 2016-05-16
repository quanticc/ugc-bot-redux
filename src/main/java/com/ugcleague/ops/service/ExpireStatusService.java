package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.document.GameServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;

@Service
@Transactional
public class ExpireStatusService {

    private static final Logger log = LoggerFactory.getLogger(ExpireStatusService.class);
    private static final String SERVICE_URL = "https://www.gameservers.com/ugcleague/free/index.php?action=get_status";

    private final RestTemplate restTemplate = new RestTemplate();
    private final HttpEntity<String> entity;
    private final ParameterizedTypeReference<Map<String, Integer>> type = new ParameterizedTypeReference<Map<String, Integer>>() {
    };
    private final GameServerService gameServerService;

    @Autowired
    public ExpireStatusService(GameServerService gameServerService) {
        this.gameServerService = gameServerService;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("User-Agent", "Mozilla");
        this.entity = new HttpEntity<>(null, headers);
    }

    @Async
    public void refreshExpireDates() {
        log.debug("==== Refreshing expire dates of ALL servers ====");
        ZonedDateTime now = ZonedDateTime.now();
        Map<String, Integer> map = getExpireSeconds();
        long count = gameServerService.findAll().stream().filter(s -> map.containsKey(s.getId()))
            .map(s -> refreshExpireDate(s, now, (Integer) map.get(s.getId()))).map(gameServerService::save).count();
        log.info("{} expire dates refreshed", count);
        gameServerService.refreshRconPasswords();
    }

    private GameServer refreshExpireDate(GameServer server, ZonedDateTime now, Integer seconds) {
        if (seconds != 0) {
            server.setExpireDate(now.plusSeconds(seconds));
        }
        server.setExpireCheckDate(now);
        return server;
    }

    public Map<String, Integer> getExpireSeconds() {
        try {
            ResponseEntity<Map<String, Integer>> response = restTemplate.exchange(SERVICE_URL, HttpMethod.GET, entity, type);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Game servers claim status refreshed");
                return response.getBody();
            }
        } catch (RestClientException e) {
            log.warn("Could not refresh claim status", e.toString());
        }
        return Collections.emptyMap();
    }
}
