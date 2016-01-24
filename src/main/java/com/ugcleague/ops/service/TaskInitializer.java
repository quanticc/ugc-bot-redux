package com.ugcleague.ops.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class TaskInitializer {

    private static final Logger log = LoggerFactory.getLogger(TaskInitializer.class);

    private final TaskService taskService;
    private final UpdatesFeedService updatesFeedService;
    private final GameServerService gameServerService;
    private final ExpireStatusService expireStatusService;

    @Autowired
    public TaskInitializer(TaskService taskService, UpdatesFeedService updatesFeedService,
                           GameServerService gameServerService, ExpireStatusService expireStatusService) {
        this.taskService = taskService;
        this.updatesFeedService = updatesFeedService;
        this.gameServerService = gameServerService;
        this.expireStatusService = expireStatusService;
    }

    @PostConstruct
    private void configure() {
        log.debug("Preparing to initialize scheduled tasks");
        // register main tasks into application
        // task 1. refresh updates feed
        // task 2. refresh server status -> update game servers
        // task 3. refresh expire status -> task 4. refresh rcon passwords
        Runnable refreshUpdatesFeed = updatesFeedService::refreshUpdatesFeed;
        Runnable updateGameServers = gameServerService::updateGameServers;
        Runnable refreshExpireDates = expireStatusService::refreshExpireDates;
        Runnable refreshRconPasswords = gameServerService::refreshRconPasswords;
        taskService.register("refreshUpdatesFeed", 20000, 600000, refreshUpdatesFeed);
        taskService.register("updateGameServers", 30000, 150000, updateGameServers);
        taskService.register("refreshExpireDates", 50000, 600000, refreshExpireDates);
        taskService.register("refreshRconPasswords", 60000, 600000, refreshRconPasswords);
    }
}
