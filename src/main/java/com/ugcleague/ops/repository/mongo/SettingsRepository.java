package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.Settings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SettingsRepository extends MongoRepository<Settings, String> {
}
