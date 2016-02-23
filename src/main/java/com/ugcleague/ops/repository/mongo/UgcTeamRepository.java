package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.UgcTeam;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UgcTeamRepository extends MongoRepository<UgcTeam, Integer> {
}
