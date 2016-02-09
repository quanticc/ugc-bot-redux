package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.SizzStats;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the SizzStats entity.
 */
public interface SizzStatsRepository extends MongoRepository<SizzStats, Long> {
}
