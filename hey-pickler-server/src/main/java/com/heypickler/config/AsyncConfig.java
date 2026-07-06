package com.heypickler.config;

import com.heypickler.common.aspect.AuditExecutorMonitor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("rankingExecutor")
    public Executor rankingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ranking-");
        executor.initialize();
        return executor;
    }

    /**
     * Audit log writer. Larger queue (500) because audit must never block an admin
     * request. Under burst load we previously dropped the oldest queued entry via
     * DiscardOldestPolicy — that was a compliance hole (Loop-v2 D8).
     *
     * <p>Now: CallerRunsPolicy + {@link AuditExecutorMonitor}. When the queue is
     * saturated and the executor rejects a submission, the calling admin thread
     * runs the task inline (slower but durable — no audit gap). The monitor
     * counts rejections and emits a WARN every 100 rejections so backpressure
     * shows up in operator logs.
     */
    @Bean("auditLogExecutor")
    public Executor auditLogExecutor(AuditExecutorMonitor monitor) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                monitor.recordRejection();
                super.rejectedExecution(r, e);
            }
        });
        executor.initialize();
        return executor;
    }

    /**
     * Shared rejected-task counter — used by the audit executor's
     * rejection handler and exposed for tests.
     */
    @Bean
    public AuditExecutorMonitor auditExecutorMonitor() {
        return new AuditExecutorMonitor();
    }
}
