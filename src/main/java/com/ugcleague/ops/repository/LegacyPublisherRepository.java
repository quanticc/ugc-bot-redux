package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.Publisher;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Deprecated
public interface LegacyPublisherRepository extends JpaRepository<Publisher, Long> {

    @EntityGraph(value = "Publisher.full", type = EntityGraphType.LOAD)
    Optional<Publisher> findByName(String name);

    @Query("select distinct publisher from Publisher publisher left join fetch publisher.subscribers")
    List<Publisher> findAllEagerly();
}
