package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.Permission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PermissionRepository extends MongoRepository<Permission, String> {

    Optional<Permission> findByName(String name);
}
