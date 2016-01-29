package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.DiscordUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the DiscordUser entity.
 */
public interface DiscordUserRepository extends JpaRepository<DiscordUser,Long> {

    Optional<DiscordUser> findByDiscordUserId(String id);

    @Query("select discordUser from DiscordUser discordUser where discordUser.connected > discordUser.disconnected ")
    List<DiscordUser> findCurrentlyConnected();
}
