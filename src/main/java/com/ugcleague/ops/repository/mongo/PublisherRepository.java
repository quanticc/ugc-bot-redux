package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.Publisher;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PublisherRepository extends MongoRepository<Publisher, String> {

    Optional<Publisher> findById(String id);

    List<Publisher> findByChannelId(String channelId);
}
