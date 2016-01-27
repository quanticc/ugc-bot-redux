package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.SyncGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the SyncGroup entity.
 */
public interface SyncGroupRepository extends JpaRepository<SyncGroup, Long> {

    Optional<SyncGroup> findByLocalDir(String localDir);

}
