package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.DiscordChannel;
import com.ugcleague.ops.domain.document.DiscordUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DiscordChannelRepository extends MongoRepository<DiscordChannel, String> {

    Optional<DiscordChannel> findById(String id);
}
