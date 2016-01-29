package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.DiscordChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the DiscordChannel entity.
 */
public interface DiscordChannelRepository extends JpaRepository<DiscordChannel,Long> {

    Optional<DiscordChannel> findByDiscordChannelId(String id);
}
