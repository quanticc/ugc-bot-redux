package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.DiscordMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the DiscordMessage entity.
 */
public interface DiscordMessageRepository extends JpaRepository<DiscordMessage,Long> {

    Optional<DiscordMessage> findByDiscordMessageId(String id);
}
