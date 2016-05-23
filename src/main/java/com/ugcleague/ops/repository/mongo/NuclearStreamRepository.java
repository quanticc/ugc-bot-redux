package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.NuclearStream;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NuclearStreamRepository extends MongoRepository<NuclearStream, String> {

    Optional<NuclearStream> findById(String id);
}
