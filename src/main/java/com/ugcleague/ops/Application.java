package com.ugcleague.ops;

import com.ugcleague.ops.config.Constants;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.javafx.AbstractJavaFxApplicationSupport;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.MetricFilterAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.MetricRepositoryAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

@EnableAutoConfiguration(exclude = {MetricFilterAutoConfiguration.class, MetricRepositoryAutoConfiguration.class})
@EnableConfigurationProperties({LeagueProperties.class, LiquibaseProperties.class})
@SpringBootApplication
public class Application extends AbstractJavaFxApplicationSupport {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private Environment env;

    /**
     * Initializes the application.
     * <p>
     * Spring profiles can be configured with a program arguments --spring.profiles.active=your-active-profile
     * <p>
     */
    @PostConstruct
    public void initApplication() throws IOException {
        if (env.getActiveProfiles().length == 0) {
            log.warn("No Spring profile configured, running with default configuration");
        } else {
            log.info("Running with Spring profile(s) : {}", Arrays.toString(env.getActiveProfiles()));
            Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
            if (activeProfiles.contains(Constants.SPRING_PROFILE_DEVELOPMENT) && activeProfiles.contains(Constants.SPRING_PROFILE_PRODUCTION)) {
                log.error("You have misconfigured your application! " +
                    "It should not run with both the 'dev' and 'prod' profiles at the same time.");
            }
            if (activeProfiles.contains(Constants.SPRING_PROFILE_PRODUCTION) && activeProfiles.contains(Constants.SPRING_PROFILE_FAST)) {
                log.error("You have misconfigured your application! " +
                    "It should not run with both the 'prod' and 'fast' profiles at the same time.");
            }
            if (activeProfiles.contains(Constants.SPRING_PROFILE_DEVELOPMENT) && activeProfiles.contains(Constants.SPRING_PROFILE_CLOUD)) {
                log.error("You have misconfigured your application! " +
                    "It should not run with both the 'dev' and 'cloud' profiles at the same time.");
            }
        }
        context.registerShutdownHook();
    }

    /**
     * Main method, used to run the application.
     */
    public static void main(String[] args) {
        launchApp(Application.class, args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Thread.currentThread().setName("JavaFXAppThread");
        log.debug("Started JavaFX runtime");
    }
}
