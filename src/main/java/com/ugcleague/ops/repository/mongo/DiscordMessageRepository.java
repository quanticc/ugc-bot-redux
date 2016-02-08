package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.DiscordMessage;
import com.ugcleague.ops.domain.document.DiscordUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DiscordMessageRepository extends MongoRepository<DiscordMessage, String> {

    Optional<DiscordMessage> findById(String id);
}
