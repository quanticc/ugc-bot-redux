package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.repository.LegacyGameServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class LegacyGameServerService {

    private static final Logger log = LoggerFactory.getLogger(LegacyGameServerService.class);

    private final LegacyGameServerRepository legacyGameServerRepository;

    @Autowired
    public LegacyGameServerService(LegacyGameServerRepository legacyGameServerRepository) {
        this.legacyGameServerRepository = legacyGameServerRepository;
    }

    public long count() {
        return legacyGameServerRepository.count();
    }

    public List<GameServer> findAll() {
        return legacyGameServerRepository.findAllWithEagerRelationships();
    }
}
