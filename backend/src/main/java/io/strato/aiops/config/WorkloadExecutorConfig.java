package io.strato.aiops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class WorkloadExecutorConfig {

    @Bean("analysisJobExecutor")
    public ThreadPoolTaskExecutor analysisJobExecutor(
            @Value("${aiops.executors.analysis-job.core-size:2}") int coreSize,
            @Value("${aiops.executors.analysis-job.max-size:4}") int maxSize,
            @Value("${aiops.executors.analysis-job.queue-capacity:50}") int queueCapacity
    ) {
        return executor("analysis-job-", coreSize, maxSize, queueCapacity);
    }

    @Bean("clusterSyncExecutor")
    public ThreadPoolTaskExecutor clusterSyncExecutor(
            @Value("${aiops.executors.cluster-sync.core-size:2}") int coreSize,
            @Value("${aiops.executors.cluster-sync.max-size:4}") int maxSize,
            @Value("${aiops.executors.cluster-sync.queue-capacity:100}") int queueCapacity
    ) {
        return executor("cluster-sync-", coreSize, maxSize, queueCapacity);
    }

    @Bean("analysisSectionExecutor")
    public Executor analysisSectionExecutor(
            @Value("${aiops.executors.analysis-section.core-size:3}") int coreSize,
            @Value("${aiops.executors.analysis-section.max-size:6}") int maxSize,
            @Value("${aiops.executors.analysis-section.queue-capacity:50}") int queueCapacity
    ) {
        return executor("analysis-section-", coreSize, maxSize, queueCapacity);
    }

    @Bean("readinessProbeExecutor")
    public Executor readinessProbeExecutor(
            @Value("${aiops.executors.readiness-probe.core-size:3}") int coreSize,
            @Value("${aiops.executors.readiness-probe.max-size:4}") int maxSize,
            @Value("${aiops.executors.readiness-probe.queue-capacity:20}") int queueCapacity
    ) {
        return executor("readiness-probe-", coreSize, maxSize, queueCapacity);
    }

    @Bean("commandConsoleExecutor")
    public ThreadPoolTaskExecutor commandConsoleExecutor(
            @Value("${aiops.executors.command-console.core-size:4}") int coreSize,
            @Value("${aiops.executors.command-console.max-size:12}") int maxSize,
            @Value("${aiops.executors.command-console.queue-capacity:100}") int queueCapacity
    ) {
        return executor("command-console-", coreSize, maxSize, queueCapacity);
    }

    @Bean("applicationDeliveryExecutor")
    public ThreadPoolTaskExecutor applicationDeliveryExecutor(
            @Value("${aiops.executors.application-delivery.core-size:2}") int coreSize,
            @Value("${aiops.executors.application-delivery.max-size:4}") int maxSize,
            @Value("${aiops.executors.application-delivery.queue-capacity:30}") int queueCapacity
    ) {
        // Helm 작업은 외부 I/O 중심이므로 분석 작업과 격리된 제한 큐를 사용한다.
        return executor("application-delivery-", coreSize, maxSize, queueCapacity);
    }

    @Bean("aiChatHeartbeatScheduler")
    @Primary
    public ThreadPoolTaskScheduler aiChatHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ai-chat-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean("resourceLogScheduler")
    public ThreadPoolTaskScheduler resourceLogScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("resource-log-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    private ThreadPoolTaskExecutor executor(String prefix, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
