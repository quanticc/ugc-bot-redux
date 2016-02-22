package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.Chart;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for the Chart entity.
 */
public interface ChartRepository extends MongoRepository<Chart, String> {

    Optional<Chart> findByName(String name);
}
