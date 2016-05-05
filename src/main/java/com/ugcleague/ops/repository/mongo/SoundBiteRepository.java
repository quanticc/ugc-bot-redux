package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.SoundBite;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SoundBiteRepository extends MongoRepository<SoundBite, String> {

    Optional<SoundBite> findById(String id);

}
