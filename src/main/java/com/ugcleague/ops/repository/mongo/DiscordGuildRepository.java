package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.DiscordGuild;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DiscordGuildRepository extends MongoRepository<DiscordGuild, String> {

    Optional<DiscordGuild> findById(String id);
}
