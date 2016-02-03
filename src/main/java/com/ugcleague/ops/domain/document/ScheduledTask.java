package com.ugcleague.ops.domain.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Objects;

import static com.ugcleague.ops.util.DateUtil.humanizeCronPatterns;
import static com.ugcleague.ops.util.DateUtil.relativeNextTriggerFromCron;

@Document(collection = "scheduled_task")
public class ScheduledTask extends AbstractAuditingEntity {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTask.class);

    @Id
    private String id;

    @Field("task_id")
    @Indexed(sparse = true)
    private String taskId;

    @Field("name")
    @Indexed(unique = true)
    private String name;

    @Field("enabled")
    private Boolean enabled = true;

    @Field("pattern")
    private String pattern;

    public ScheduledTask() {

    }

    public ScheduledTask(String name, String pattern) {
        this.name = name;
        this.pattern = pattern;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledTask that = (ScheduledTask) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ScheduledTask{" +
            "id='" + id + '\'' +
            ", taskId='" + taskId + '\'' +
            ", name='" + name + '\'' +
            ", enabled=" + enabled +
            ", pattern='" + pattern + '\'' +
            ", created=" + getCreatedDate() +
            ", modified=" + getLastModifiedDate() +
            '}';
    }

    public String humanString() {
        return String.format("**%s** is %s and scheduled to run: %s (next run %s)", name,
            enabled != null && enabled ? "enabled" : "disabled", humanizeCronPatterns(pattern),
            relativeNextTriggerFromCron(pattern));
    }
}
