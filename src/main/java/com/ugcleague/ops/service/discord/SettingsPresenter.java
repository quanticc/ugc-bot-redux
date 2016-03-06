package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.config.Constants;
import com.ugcleague.ops.domain.document.Settings;
import com.ugcleague.ops.repository.mongo.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;

@Service
@Transactional
@Order(1)
public class SettingsPresenter {

    private static final Logger log = LoggerFactory.getLogger(SettingsPresenter.class);

    private final SettingsRepository settingsRepository;

    @Autowired
    public SettingsPresenter(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @PostConstruct
    private void configure() {
        Settings cfg = settingsRepository.findOne(Constants.BOT_SETTINGS);
        if (cfg == null) {
            log.info("Initializing persistent settings");
            cfg = new Settings();
            cfg.setId(Constants.BOT_SETTINGS);
            settingsRepository.save(cfg);
        }
    }
}
