package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.DiscordAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the DiscordAttachment entity.
 */
public interface DiscordAttachmentRepository extends JpaRepository<DiscordAttachment,Long> {

}
