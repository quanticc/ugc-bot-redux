package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.UgcPlayer;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UgcPlayerRepository extends MongoRepository<UgcPlayer, Long> {
}
