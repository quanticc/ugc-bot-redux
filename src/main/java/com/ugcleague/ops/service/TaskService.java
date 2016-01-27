package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.Task;
import com.ugcleague.ops.repository.TaskRepository;
import com.ugcleague.ops.service.discord.CommandService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.ugcleague.ops.util.DateUtil.formatAbsolute;
import static com.ugcleague.ops.util.DateUtil.formatRelative;
import static java.util.Arrays.asList;

@Service
@Transactional
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final CommandService commandService;
    private final ScheduledExecutorService executorService;
    private final Map<String, Runnable> runnableMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> futureMap = new ConcurrentHashMap<>();

    private OptionSpec<String> nameSpec;
    private OptionSpec<Long> rateSpec;
    private OptionSpec<Boolean> enableSpec;
    private OptionSpec<Void> helpSpec;
    private OptionSpec<Void> listSpec;

    @Autowired
    public TaskService(TaskRepository taskRepository, CommandService commandService) {
        this.taskRepository = taskRepository;
        this.commandService = commandService;
        this.executorService = Executors.newScheduledThreadPool((int) Math.max(5, taskRepository.count() / 2 + 1), new TaskThreadFactory());
    }

    @PostConstruct
    private void configure() {
        // .task -n <name> -r <rate> -e <true|false>
        OptionParser parser = new OptionParser();
        parser.posixlyCorrect(true);
        listSpec = parser.acceptsAll(asList("l", "list"), "list registered tasks");
        helpSpec = parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        nameSpec = parser.acceptsAll(asList("n", "name"), "name of the task").requiredUnless(listSpec)
            .withRequiredArg().describedAs("taskName");
        enableSpec = parser.acceptsAll(asList("e", "enable"), "enable or disable the task").withOptionalArg()
            .ofType(Boolean.class).defaultsTo(true);
        rateSpec = parser.acceptsAll(asList("r", "rate"), "fixed rate to set").requiredUnless(listSpec, enableSpec)
            .withRequiredArg().ofType(Long.class).describedAs("ms");
        commandService.register(CommandBuilder.startsWith(".task")
            .description("Configures bot scheduled tasks").permission("master")
            .parser(parser).command(this::executeTaskCommand).build());
    }

    private String executeTaskCommand(IMessage message, OptionSet o) {
        if (!o.has(helpSpec)) {
            if (o.has(listSpec)) {
                return buildTaskList();
            } else {
                boolean enable = o.valueOf(enableSpec);
                String name = o.valueOf(nameSpec);
                long rate = enable ? o.valueOf(rateSpec) : 0;
                return updateTask(name, rate, enable);
            }
        }
        return null;
    }

    private String buildTaskList() {
        return "*Scheduled Tasks*\n" + taskRepository.findAll().stream().map(task -> {
            ScheduledFuture<?> future = futureMap.get(task.getName());
            String details = task.getEnabled() ? String.format("each %s%s",
                formatAbsolute(Duration.ofMillis(task.getFixedRate())),
                (future == null ? "" : String.format(" (scheduled to run %s)", formatRelative(Duration.between(Instant.now(),
                    Instant.now().plusMillis(future.getDelay(TimeUnit.MILLISECONDS))))))) : "disabled";
            return String.format("**%s** %s", task.getName(), details);
        }).collect(Collectors.joining("\n"));
    }

    private String updateTask(String name, long rate, boolean enable) {
        if (enable && rate <= 0) {
            return "Rate must be positive";
        }
        Optional<Task> o = taskRepository.findByName(name);
        if (!o.isPresent()) {
            return "No task exists by that name";
        }
        Task task = o.get();
        task.setEnabled(enable);
        taskRepository.save(task);
        if (enable) {
            ScheduledFuture<?> future = changeTaskLaunchParameters(task, rate, 0);
            return "Task **" + name + "** was scheduled to run " +
                formatRelative(Duration.between(Instant.now(),
                    Instant.now().plusMillis(future.getDelay(TimeUnit.MILLISECONDS))));
        } else {
            return "Task was disabled";
        }
    }

    public ScheduledFuture<?> register(String taskName, long initialDelay, long fixedRate, Runnable runnable) {
        Task task = taskRepository.findByName(taskName).orElseGet(() -> newTask(taskName, fixedRate));
        if (task.getFixedRate() <= 0) {
            // invalid fixed rate - normalize to 10s+
            task.setFixedRate(Math.max(10000, fixedRate));
        }
        initialDelay = Math.max(0, initialDelay);
        runnableMap.put(taskName, runnable);
        taskRepository.save(task);
        log.debug("Scheduling {} in {} ms with a fixed rate of {} ms", taskName, initialDelay, fixedRate);
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(wrappedRunnable(taskName, runnableMap.get(taskName)),
            initialDelay, fixedRate, TimeUnit.MILLISECONDS);
        futureMap.put(taskName, future);
        return future;
    }

    private Task newTask(String taskName, long fixedRate) {
        Task task = new Task();
        task.setEnabled(true);
        task.setName(taskName);
        task.setFixedRate(fixedRate);
        return task;
    }

    public Optional<ScheduledFuture<?>> changeTaskLaunchParameters(String taskName, long fixedRate) {
        return taskRepository.findByName(taskName).map(t -> changeTaskLaunchParameters(t, fixedRate, 0));
    }

    public ScheduledFuture<?> changeTaskLaunchParameters(Task task, long fixedRate, long initialDelay) {
        if (fixedRate <= 0) {
            throw new IllegalArgumentException("Bad fixed rate");
        }
        String taskName = task.getName();
        long previousRate = task.getFixedRate();
        ScheduledFuture<?> future = futureMap.get(taskName);
        if (previousRate != fixedRate) {
            task.setFixedRate(fixedRate);
            taskRepository.save(task);
            long remaining = initialDelay;
            if (future != null) {
                remaining = Math.max(0, future.getDelay(TimeUnit.MILLISECONDS));
                if (future.cancel(true)) {
                    log.warn("Could not cancel launcher for task: {}", taskName);
                }
            }
            log.debug("Scheduling {} in {} ms with a fixed rate of {} ms", taskName, remaining, fixedRate);
            future = executorService.scheduleAtFixedRate(wrappedRunnable(taskName, runnableMap.get(taskName)),
                remaining, fixedRate, TimeUnit.MILLISECONDS);
            futureMap.put(taskName, future);
        } else {
            log.info("Set rate of {} is the same as the current one, no changes done", taskName);
            taskRepository.save(task);
        }
        return future;
    }

    private Runnable wrappedRunnable(String taskName, Runnable original) {
        Task task = taskRepository.findByName(taskName).get();
        return () -> {
            ZonedDateTime nextRun = ZonedDateTime.now().plus(task.getFixedRate(), ChronoUnit.MILLIS);
            if (task.getEnabled()) {
                log.debug("**** Running task: {}", taskName);
                original.run(); // if the target is @Async then we shouldn't block here and the next log line will be accurate
                log.debug("**** Next run for {} {} ({})", taskName,
                    formatRelative(Duration.between(Instant.now(), nextRun)), nextRun);
            } else {
                log.debug("**** Task {} is not enabled. Next attempt {} ({})", taskName,
                    formatRelative(Duration.between(Instant.now(), nextRun)), nextRun);
            }
        };
    }

    static class TaskThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        TaskThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                Thread.currentThread().getThreadGroup();
            namePrefix = "task-launcher-";
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
