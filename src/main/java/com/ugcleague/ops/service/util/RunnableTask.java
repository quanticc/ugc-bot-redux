package com.ugcleague.ops.service.util;

import com.ugcleague.ops.domain.document.ScheduledTask;
import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunnableTask extends Task {

    private static final Logger log = LoggerFactory.getLogger(RunnableTask.class);

    private final ScheduledTask task;
    private final Runnable runnable;

    public RunnableTask(ScheduledTask task, Runnable runnable) {
        this.task = task;
        this.runnable = runnable;
    }

    public ScheduledTask getTask() {
        return task;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        if (task.getEnabled()) {
            log.debug("**** Running {} task", task.getName());
            runnable.run();
        } else {
            log.debug("**** Task {} is not enabled", task.getName());
        }
    }

    @Override
    public String toString() {
        return "ConsumingTask{" +
            "task=" + task +
            ", runnable=" + runnable +
            '}';
    }
}
