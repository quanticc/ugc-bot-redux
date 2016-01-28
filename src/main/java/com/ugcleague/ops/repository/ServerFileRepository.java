package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.ServerFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data JPA repository for the ServerFile entity.
 */
public interface ServerFileRepository extends JpaRepository<ServerFile, Long> {

    List<ServerFile> findByNameLike(String name);

    @Query("select distinct serverFile from ServerFile serverFile left join fetch serverFile.syncGroup")
    List<ServerFile> findAllWithEagerRelationships();
}
