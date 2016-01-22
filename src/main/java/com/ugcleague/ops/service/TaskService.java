package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.Task;
import com.ugcleague.ops.repository.TaskRepository;
import com.ugcleague.ops.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Transactional
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final ScheduledExecutorService executorService;
    private final Map<String, Long> fallbackRates = new ConcurrentHashMap<>();
    private final Map<String, Runnable> tasks = new ConcurrentHashMap<>();

    @Autowired
    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
        ThreadFactory threadFactory = new TaskThreadFactory();
        executorService = Executors.newScheduledThreadPool(8, threadFactory);
    }

    public boolean isEnabled(String taskName) {
        return taskRepository.findByName(taskName).map(Task::getEnabled).orElse(false);
    }

    public long getMillisForNextSchedule(String taskName) {
        return taskRepository.findByName(taskName).map(Task::getFixedRate).orElseGet(() -> getDefaultRate(taskName));
    }

    private long getDefaultRate(String taskName) {
        return fallbackRates.getOrDefault(taskName, 60000L);
    }

    public void registerTask(String taskName, long initialDelay, long fixedRate, Runnable runnable) {
        Task task = taskRepository.findByName(taskName).orElseGet(() -> newTask(taskName, fixedRate));
        task.setEnabled(true);
        task.setFixedRate(fixedRate);
        tasks.put(task.getName(), runnable);
        taskRepository.save(task);
        executorService.schedule(runnable, initialDelay, TimeUnit.MILLISECONDS);
    }

    private Task newTask(String taskName, long fixedRate) {
        Task task = new Task();
        task.setName(taskName);
        task.setFixedRate(fixedRate);
        return task;
    }

    public void scheduleNext(String taskName) {
        taskRepository.findByName(taskName).ifPresent(task -> {
            ZonedDateTime nextRun = ZonedDateTime.now().plus(task.getFixedRate(), ChronoUnit.MILLIS);
            log.debug("Next run for {} {} ({})", taskName, DateUtil.formatRelative(Duration.between(Instant.now(), nextRun)), nextRun);
            executorService.schedule(tasks.get(taskName), task.getFixedRate(), TimeUnit.MILLISECONDS);
        });
    }

    static class TaskThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        TaskThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                Thread.currentThread().getThreadGroup();
            namePrefix = "tasks-worker-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                namePrefix + threadNumber.getAndIncrement(),
                0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
