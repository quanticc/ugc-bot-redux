package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.GameServer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface GameServerRepository extends MongoRepository<GameServer, String> {

    Optional<GameServer> findById(String id);

    Optional<GameServer> findByAddress(String address);

    Optional<GameServer> findByAddressStartingWith(String address);

    @Query("{ $where : 'this.rcon_password == null || (this.last_rcon_date <= this.expire_date && this.expire_date <= new Date())' }")
    List<GameServer> findByRconRefreshNeeded();

    Stream<GameServer> findByStatusCheckDateBefore(ZonedDateTime dateTime);

    List<GameServer> findByVersionLessThan(Integer version);

    Stream<GameServer> findByLastGameUpdateBeforeAndVersionLessThan(ZonedDateTime date, Integer version);

    List<GameServer> findByPingLessThanEqual(Integer ping);

    List<GameServer> findByRconPasswordIsNull();
}
