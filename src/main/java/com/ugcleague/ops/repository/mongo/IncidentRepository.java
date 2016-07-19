package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.Incident;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends MongoRepository<Incident, String> {

    Optional<Incident> findById(String id);

    List<Incident> findByGroup(String group);
}
