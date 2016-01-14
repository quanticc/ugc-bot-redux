package com.ugcleague.ops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "league", ignoreUnknownFields = false)
public class LeagueProperties {

    private final GameServers gameServers = new GameServers();
    private final Feed feed = new Feed();
    private final Async async = new Async();
    private final Datasource datasource = new Datasource();
    private final Cache cache = new Cache();
    private final Discord discord = new Discord();

    private int consoleListenPort = 7131;
    private String syncRepositoryDir = "sync-repository";
    private String steamApiKey;
    private String ugcApiKey;

    @Data
    public static class GameServers {
        private String username;
        private String password;
    }

    @Data
    public static class Feed {
        private String cacheDir = "feed";
        private String url;
    }

    @Data
    public static class Async {
        private int corePoolSize = 2;
        private int maxPoolSize = 50;
        private int queueCapacity = 10000;
    }

    @Data
    public static class Datasource {
        private boolean cachePrepStmts = true;
        private int prepStmtCacheSize = 250;
        private int prepStmtCacheSqlLimit = 2048;
        private boolean useServerPrepStmts = true;
    }

    @Data
    public static class Cache {
        private int timeToLiveSeconds = 3600;
        private final Ehcache ehcache = new Ehcache();

        @Data
        public static class Ehcache {
            private String maxBytesLocalHeap = "16M";
        }
    }

    @Data
    public static class Discord {
        private boolean autologin;
        private String email;
        private String password;
        private List<String> invites = new ArrayList<>();
        private List<String> masters = new ArrayList<>();
    }
}
