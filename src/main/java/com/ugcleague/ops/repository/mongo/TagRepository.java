package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.Tag;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for the Tag entity.
 */
public interface TagRepository extends MongoRepository<Tag, String> {

    Optional<Tag> findById(String id);

    List<Tag> findByParent(String parent);

    List<Tag> findByDirect(boolean direct);
}
