package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.ScheduledTask;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for the ScheduledTask entity.
 */
public interface ScheduledTaskRepository extends MongoRepository<ScheduledTask, String> {

    Optional<ScheduledTask> findByTaskId(String taskId);

    Optional<ScheduledTask> findByName(String name);
}
