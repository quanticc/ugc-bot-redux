package com.ugcleague.ops.config;

import com.ugcleague.ops.async.ExceptionHandlingAsyncTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
@EnableRetry
public class AsyncConfiguration implements AsyncConfigurer {

    private final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);

    @Autowired
    private LeagueProperties leagueProperties;

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        log.debug("Creating Async Task Executor");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(leagueProperties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(leagueProperties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(leagueProperties.getAsync().getQueueCapacity());
        executor.setThreadNamePrefix("task-executor-");
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
