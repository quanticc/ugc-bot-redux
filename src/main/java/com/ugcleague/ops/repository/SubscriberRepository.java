package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Deprecated
public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    Optional<Subscriber> findByUserId(String id);

    Optional<Subscriber> findByUserIdAndName(String id, String name);
}
