package com.ugcleague.ops.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.metamodel.EntityType;
import java.util.Set;

@Configuration
@EnableCaching
@AutoConfigureAfter(value = {DatabaseConfiguration.class})
@Profile("!" + Constants.SPRING_PROFILE_FAST)
public class CacheConfiguration {

    private final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    @PersistenceContext
    private EntityManager entityManager;

    private net.sf.ehcache.CacheManager cacheManager;

    @PreDestroy
    public void destroy() {
        log.info("Closing Cache Manager");
        cacheManager.shutdown();
    }

    @Bean
    public CacheManager cacheManager(LeagueProperties leagueProperties) {
        log.debug("Starting Ehcache");
        cacheManager = net.sf.ehcache.CacheManager.create();
        cacheManager.getConfiguration().setMaxBytesLocalHeap(leagueProperties.getCache().getEhcache().getMaxBytesLocalHeap());
        Set<EntityType<?>> entities = entityManager.getMetamodel().getEntities();
        for (EntityType<?> entity : entities) {

            String name = entity.getName();
            if (name == null || entity.getJavaType() != null) {
                name = entity.getJavaType().getName();
            }
            Assert.notNull(name, "entity cannot exist without a identifier");

            net.sf.ehcache.Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.getCacheConfiguration().setTimeToLiveSeconds(leagueProperties.getCache().getTimeToLiveSeconds());
            }
        }
        EhCacheCacheManager ehCacheManager = new EhCacheCacheManager();
        ehCacheManager.setCacheManager(cacheManager);
        return ehCacheManager;
    }
}
