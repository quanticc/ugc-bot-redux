package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.LogsTfStats;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for the LogsTfStats entity.
 */
public interface LogsTfStatsRepository extends MongoRepository<LogsTfStats, Long> {

    Optional<LogsTfStats> findById(Long id);
}
