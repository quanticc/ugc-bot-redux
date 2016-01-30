package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.UgcSeason;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the UgcMatch entity.
 */
public interface UgcSeasonRepository extends MongoRepository<UgcSeason, Integer> {

}
