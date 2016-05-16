package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.Flag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the Flag entity.
 */
@Deprecated
public interface FlagRepository extends JpaRepository<Flag, Long> {

    Optional<Flag> findByName(String name);

}
