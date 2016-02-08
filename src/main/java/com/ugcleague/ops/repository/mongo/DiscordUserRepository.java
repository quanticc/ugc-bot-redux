package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.DiscordUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DiscordUserRepository extends MongoRepository<DiscordUser, String> {

    Optional<DiscordUser> findById(String id);

    @Query("{ $where : \"this.last_connect > this.last_disconnect\" }")
    List<DiscordUser> findCurrentlyConnected();
}
