/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ugcleague.ops.javafx;

import com.ugcleague.ops.config.Constants;
import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.SimpleCommandLinePropertySource;

/**
 * Bridge to support Spring IoC container with JavaFX
 *
 * @author Thomas Darimont
 */
public abstract class AbstractJavaFxApplicationSupport extends Application {

    private static String[] savedArgs;

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void init() throws Exception {
        SpringApplication app = new SpringApplication(getClass());
        SimpleCommandLinePropertySource source = new SimpleCommandLinePropertySource(savedArgs);
        addDefaultProfile(app, source);
        applicationContext = app.run(savedArgs);
        applicationContext.getEnvironment();
        //applicationContext = SpringApplication.run(getClass(), savedArgs);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        SpringApplication.exit(applicationContext);
    }

    protected static void launchApp(Class<? extends AbstractJavaFxApplicationSupport> appClass, String[] args) {
        AbstractJavaFxApplicationSupport.savedArgs = args;
        Application.launch(appClass, args);
    }

    /**
     * If no profile has been configured, set by default the "dev" profile.
     */
    private static void addDefaultProfile(SpringApplication app, SimpleCommandLinePropertySource source) {
        if (!source.containsProperty("spring.profiles.active") &&
            !System.getenv().containsKey("SPRING_PROFILES_ACTIVE")) {

            app.setAdditionalProfiles(Constants.SPRING_PROFILE_DEVELOPMENT);
        }
    }
}
