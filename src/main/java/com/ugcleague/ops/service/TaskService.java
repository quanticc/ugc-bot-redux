package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.document.ScheduledTask;
import com.ugcleague.ops.repository.mongo.ScheduledTaskRepository;
import com.ugcleague.ops.service.util.RunnableTask;
import it.sauronsoftware.cron4j.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TaskService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final ScheduledTaskRepository repository;
    private final UpdatesFeedService updatesFeedService;
    private final GameServerService gameServerService;
    private final ExpireStatusService expireStatusService;
    private final Scheduler scheduler;

    @Autowired
    public TaskService(ScheduledTaskRepository repository, UpdatesFeedService updatesFeedService,
                       GameServerService gameServerService, ExpireStatusService expireStatusService) {
        this.repository = repository;
        this.updatesFeedService = updatesFeedService;
        this.gameServerService = gameServerService;
        this.expireStatusService = expireStatusService;
        this.scheduler = new Scheduler();
    }

    @PostConstruct
    private void configure() {
        Runnable gameUpdates = updatesFeedService::refreshUpdatesFeed;
        Runnable status = gameServerService::updateGameServers;
        Runnable passwords = expireStatusService::refreshExpireDates;

        String statusTriggers = "1-56/5 2-6,12-16,18-23 * * mon,wed,fri|1-31/30 0-1,7-11,17-19 * * mon,wed,fri|1-31/30 * * * tue,thu,sat,sun";
        String passwordTriggers = "3-58/5 2-6,12-16,18-23 * * mon,wed,fri|3-33/30 0-1,7-11,17-19 * * mon,wed,fri|3-33/30 * * * tue,thu,sat,sun";
        String updateTriggers = "*/5 * * * mon-fri|0 * * * sat-sun";

        schedule(getTask("hlds_announce", updateTriggers), gameUpdates);
        schedule(getTask("status", statusTriggers), status);
        schedule(getTask("passwords", passwordTriggers), passwords);

        log.info("Starting task scheduler");
        scheduler.start();
    }

    private ScheduledTask getTask(String name, String pattern) {
        return repository.findByName(name).orElseGet(() -> new ScheduledTask(name, pattern));
    }

    private ScheduledTask schedule(ScheduledTask task, Runnable runnable) {
        RunnableTask consumingTask = new RunnableTask(task, runnable);
        log.debug("Scheduling task {}", task.humanString());
        String taskId = scheduler.schedule(task.getPattern(), consumingTask);
        task.setTaskId(taskId);
        task = repository.save(task);
        return task;
    }

    public ScheduledTask reschedule(ScheduledTask task) {
        log.debug("Rescheduling task {}", task.humanString());
        scheduler.reschedule(task.getTaskId(), task.getPattern());
        return repository.save(task);
    }

    public boolean start() {
        if (!scheduler.isStarted()) {
            log.info("Starting task scheduler");
            scheduler.start();
            return true;
        } else {
            return false;
        }
    }

    public boolean stop() {
        if (scheduler.isStarted()) {
            log.info("Stopping task scheduler");
            scheduler.stop();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void destroy() throws Exception {
        stop();
    }

    public List<ScheduledTask> findAll() {
        return repository.findAll();
    }

    public Optional<ScheduledTask> findByName(String name) {
        return repository.findByName(name);
    }

    public ScheduledTask save(ScheduledTask task) {
        return repository.save(task);
    }
}
