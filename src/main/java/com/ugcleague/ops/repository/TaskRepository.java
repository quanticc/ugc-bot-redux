package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the Task entity.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByName(String name);

}
