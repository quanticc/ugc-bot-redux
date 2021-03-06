package com.ugcleague.ops.config;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.jvm.*;
import com.mongodb.Mongo;
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics;
import com.ryantenney.metrics.spring.config.annotation.MetricsConfigurerAdapter;
import fr.ippon.spark.metrics.SparkReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import se.maypril.metrics.MongoDBReporter;

import javax.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableMetrics(proxyTargetClass = true)
public class MetricsConfiguration extends MetricsConfigurerAdapter {

    private static final String PROP_METRIC_REG_JVM_MEMORY = "jvm.memory";
    private static final String PROP_METRIC_REG_JVM_GARBAGE = "jvm.garbage";
    private static final String PROP_METRIC_REG_JVM_THREADS = "jvm.threads";
    private static final String PROP_METRIC_REG_JVM_FILES = "jvm.files";
    private static final String PROP_METRIC_REG_JVM_BUFFERS = "jvm.buffers";

    private final Logger log = LoggerFactory.getLogger(MetricsConfiguration.class);

    private MetricRegistry metricRegistry = new MetricRegistry();

    private HealthCheckRegistry healthCheckRegistry = new HealthCheckRegistry();

    @Autowired
    private LeagueProperties properties;

    @Override
    @Bean
    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    @Override
    @Bean
    public HealthCheckRegistry getHealthCheckRegistry() {
        return healthCheckRegistry;
    }

    @PostConstruct
    public void init() {
        log.debug("Registering JVM gauges");
        metricRegistry.register(PROP_METRIC_REG_JVM_MEMORY, new MemoryUsageGaugeSet());
        metricRegistry.register(PROP_METRIC_REG_JVM_GARBAGE, new GarbageCollectorMetricSet());
        metricRegistry.register(PROP_METRIC_REG_JVM_THREADS, new ThreadStatesGaugeSet());
        metricRegistry.register(PROP_METRIC_REG_JVM_FILES, new FileDescriptorRatioGauge());
        metricRegistry.register(PROP_METRIC_REG_JVM_BUFFERS, new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()));
        if (properties.getMetrics().getJmx().isEnabled()) {
            log.debug("Initializing Metrics JMX reporting");
            JmxReporter jmxReporter = JmxReporter.forRegistry(metricRegistry).build();
            jmxReporter.start();
        }
    }

    @Configuration
    @ConditionalOnClass(Graphite.class)
    @Profile("!" + Constants.SPRING_PROFILE_FAST)
    public static class GraphiteRegistry {

        private final Logger log = LoggerFactory.getLogger(GraphiteRegistry.class);

        @Autowired
        private MetricRegistry metricRegistry;

        @Autowired
        private LeagueProperties properties;

        @PostConstruct
        private void init() {
            if (properties.getMetrics().getGraphite().isEnabled()) {
                log.info("Initializing Metrics Graphite reporting");
                String graphiteHost = properties.getMetrics().getGraphite().getHost();
                Integer graphitePort = properties.getMetrics().getGraphite().getPort();
                String graphitePrefix = properties.getMetrics().getGraphite().getPrefix();
                Graphite graphite = new Graphite(new InetSocketAddress(graphiteHost, graphitePort));
                GraphiteReporter graphiteReporter = GraphiteReporter.forRegistry(metricRegistry)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .prefixedWith(graphitePrefix)
                    .build(graphite);
                graphiteReporter.start(1, TimeUnit.MINUTES);
            }
        }
    }

    @Configuration
    @ConditionalOnClass(SparkReporter.class)
    @Profile("!" + Constants.SPRING_PROFILE_FAST)
    public static class SparkRegistry {

        private final Logger log = LoggerFactory.getLogger(SparkRegistry.class);

        @Autowired
        private MetricRegistry metricRegistry;

        @Autowired
        private LeagueProperties properties;

        @PostConstruct
        private void init() {
            if (properties.getMetrics().getSpark().isEnabled()) {
                log.info("Initializing Metrics Spark reporting");
                String sparkHost = properties.getMetrics().getSpark().getHost();
                Integer sparkPort = properties.getMetrics().getSpark().getPort();
                SparkReporter sparkReporter = SparkReporter.forRegistry(metricRegistry)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build(sparkHost, sparkPort);
                sparkReporter.start(1, TimeUnit.MINUTES);
            }
        }
    }

    @Configuration
    @Profile("!" + Constants.SPRING_PROFILE_FAST)
    public static class MongoRegistry {

        private final Logger log = LoggerFactory.getLogger(MongoRegistry.class);

        @Autowired
        private MetricRegistry metricRegistry;

        @Autowired
        private LeagueProperties properties;

        @Autowired
        private Mongo mongo;

        @Autowired
        private MongoProperties mongoProperties;

        @PostConstruct
        private void init() {
            if (properties.getMetrics().getMongo().isEnabled()) {
                log.info("Initializing Metrics Mongo reporting");
                MongoDBReporter mongoDBReporter = MongoDBReporter.forRegistry(metricRegistry)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .prefixedWith(properties.getMetrics().getMongo().getPrefix())
                    .filter(((name, metric) -> properties.getMetrics().getMongo().getIncludedMetrics().stream()
                        .anyMatch(name::startsWith))) // only report if it matches any included metric
                    .build(mongo.getDB(mongoProperties.getDatabase()));
                mongoDBReporter.start(5, TimeUnit.MINUTES);
            }
        }
    }
}
