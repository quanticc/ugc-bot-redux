package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.Series;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for the Series entity.
 */
public interface SeriesRepository extends MongoRepository<Series, String> {

    Optional<Series> findByName(String name);
}
