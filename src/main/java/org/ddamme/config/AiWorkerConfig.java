package org.ddamme.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring configuration for AI worker thread pool.
 * Only loads if ai.worker.enabled=true
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ai.worker.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AiWorkerConfig {

    private final AiWorkerProperties properties;

    /**
     * Thread pool for AI job execution.
     *
     * Key features:
     * - CallerRunsPolicy: If queue full, caller thread executes (backpressure)
     * - Graceful shutdown: Waits up to 5 minutes for jobs to complete
     * - Named threads: Easy to identify in logs/profiling
     */
    @Bean(name = "aiJobExecutor")
    public Executor aiJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCoreThreads());
        executor.setMaxPoolSize(properties.getMaxThreads());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("ai-job-");

        // Backpressure: caller executes if queue full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Graceful shutdown: wait for jobs to complete
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300); // 5 minutes

        executor.initialize();

        log.info("AI worker thread pool initialized: core={}, max={}, queue={}",
                properties.getCoreThreads(), properties.getMaxThreads(), properties.getQueueCapacity());

        return executor;
    }
}
