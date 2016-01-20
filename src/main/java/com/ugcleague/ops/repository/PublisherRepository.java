package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.Publisher;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    @EntityGraph(value = "Publisher.full", type = EntityGraphType.LOAD)
    Optional<Publisher> findByName(String name);
}
