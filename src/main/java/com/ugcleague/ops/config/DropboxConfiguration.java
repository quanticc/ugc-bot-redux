package com.ugcleague.ops.config;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DropboxConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DropboxConfiguration.class);

    @Autowired
    private LeagueProperties properties;

    @Bean
    public DbxClientV2 dropboxClient() throws DbxException {
        DbxRequestConfig config = new DbxRequestConfig("ugc-ops-uploader", "en_US");
        return new DbxClientV2(config, properties.getDropbox().getToken());
    }
}
