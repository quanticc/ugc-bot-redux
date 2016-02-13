package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.GameServer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Spring Data JPA repository for the GameServer entity.
 */
public interface GameServerRepository extends JpaRepository<GameServer, Long> {

    @EntityGraph(value = "GameServer.detail", type = EntityGraphType.LOAD)
    Optional<GameServer> findByAddress(String address);

    @EntityGraph(value = "GameServer.detail", type = EntityGraphType.LOAD)
    Optional<GameServer> findByAddressStartingWith(String address);

    @Query("select distinct gameServer from GameServer gameServer left join fetch gameServer.flags")
    List<GameServer> findAllWithEagerRelationships();

    @Query("select gameServer from GameServer gameServer left join fetch gameServer.flags where gameServer.id =:id")
    Optional<GameServer> findOneWithEagerRelationships(@Param("id") Long id);

    @Query("select gameServer from GameServer gameServer where gameServer.lastRconDate <= gameServer.expireDate and gameServer.expireDate <=:date")
    List<GameServer> findByRconRefreshNeeded(@Param("date") ZonedDateTime date);

    Stream<GameServer> findByStatusCheckDateBefore(ZonedDateTime dateTime);

    List<GameServer> findByVersionLessThan(Integer version);

    Stream<GameServer> findByLastGameUpdateBeforeAndVersionLessThan(ZonedDateTime date, Integer version);

    List<GameServer> findByPingLessThanEqual(Integer ping);

    List<GameServer> findByRconPasswordIsNull();
}
