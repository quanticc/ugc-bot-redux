package com.ugcleague.ops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "league", ignoreUnknownFields = false)
public class LeagueProperties {

    private String configPath = "config.json";
    private final GameServers gameServers = new GameServers();
    private final Feed feed = new Feed();
    private final Async async = new Async();
    private final Datasource datasource = new Datasource();
    private final Cache cache = new Cache();
    private final Discord discord = new Discord();
    private final Stats stats = new Stats();
    private final Ugc ugc = new Ugc();
    private final Dropbox dropbox = new Dropbox();
    private final Metrics metrics = new Metrics();
    private final Remote remote = new Remote();

    @Data
    public static class GameServers {
        private String username;
        private String password;
        private int consoleListenPort;
        private String steamApiKey;
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
        private long timeoutDelay = 10000;
        private int maxMissedPings = 50;
        private List<String> invites = new ArrayList<>();
        private String master;
        private List<String> quitting = new ArrayList<>();
        private String debugChannel = "";
        private Support support = new Support();
        private Map<String, String> channels = new LinkedHashMap<>();

        @Data
        public static class Support {
            private String guild;
            private List<String> channels = new ArrayList<>();
            private List<String> roles = new ArrayList<>();
            private List<String> excludedRoles = new ArrayList<>();
        }
    }

    @Data
    public static class Stats {
        private Map<String, String> endpoints = new LinkedHashMap<>();
    }

    @Data
    public static class Ugc {
        private String key;
        private Map<String, String> endpoints = new LinkedHashMap<>();
    }

    @Data
    public static class Dropbox {
        private String key; // v1
        private String secret; // v1
        private String token; // v2
        private String uploadsDir;
    }

    @Data
    public static class Remote {
        private String syncRepositoryDir = "sync-repository";
        private String downloadsDir = "downloads";
    }

    @Data
    public static class Metrics {
        private final Jmx jmx = new Jmx();
        private final Spark spark = new Spark();
        private final Graphite graphite = new Graphite();

        @Data
        public static class Jmx {
            private boolean enabled = true;
        }

        @Data
        public static class Spark {
            private boolean enabled = false;
            private String host = "localhost";
            private int port = 9999;
        }

        @Data
        public static class Graphite {
            private boolean enabled = false;
            private String host = "localhost";
            private int port = 2003;
            private String prefix = "ugc";
        }
    }
}
