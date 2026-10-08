package com.ohgiraffers.springdatajpa.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiConfig {
    // Java 17에서는 제한된 스레드 풀로 느린 외부 API 작업을 처리한다.
    @Bean("aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("menu-ai-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
