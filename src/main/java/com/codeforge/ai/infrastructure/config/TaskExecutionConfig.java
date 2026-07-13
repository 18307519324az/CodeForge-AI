package com.codeforge.ai.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskExecutionConfig {

    @Bean("generationTaskExecutor")
    public Executor generationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("generation-task-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Bounded executor for streaming AI generation tasks.
     * <p>
     * Core size 2, max 4, queue capacity 10. Uses {@link ThreadPoolExecutor.AbortPolicy}
     * (default) to reject tasks when overloaded, so callers get an immediate error
     * rather than unbounded queue growth.
     */
    @Bean("streamingTaskExecutor")
    public Executor streamingTaskExecutor() {
        return new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                new java.util.concurrent.ThreadFactory() {
                    private final java.util.concurrent.atomic.AtomicInteger counter =
                            new java.util.concurrent.atomic.AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "streaming-task-" + counter.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                });
    }
}
