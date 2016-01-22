package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.repository.GameServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
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
    private final GameServerRepository gameServerRepository;
    private final TaskService taskService;

    @Autowired
    public ExpireStatusService(GameServerRepository gameServerRepository, TaskService taskService) {
        this.gameServerRepository = gameServerRepository;
        this.taskService = taskService;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("User-Agent", "Mozilla");
        this.entity = new HttpEntity<>(null, headers);
    }

    @PostConstruct
    private void configure() {
        taskService.registerTask("refreshExpireDates", 50000, 600000, this::refreshExpireDates);
    }

    /**
     * Retrieves the latest result of the GameServers claim page.
     */
    //@Scheduled(initialDelay = 50000, fixedRate = 600000)
    public void refreshExpireDates() {
        String task = "refreshExpireDates";
        taskService.scheduleNext(task);
        log.debug("==== Refreshing expire dates of ALL servers ====");
        if (taskService.isEnabled(task)) {
            ZonedDateTime now = ZonedDateTime.now();
            Map<String, Integer> map = getExpireSeconds();
            long count = gameServerRepository.findAll().parallelStream().filter(s -> map.containsKey(s.getSubId()))
                .map(s -> refreshExpireDate(s, now, (Integer) map.get(s.getSubId()))).map(gameServerRepository::save).count();
            log.info("{} expire dates refreshed", count);
        }
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
