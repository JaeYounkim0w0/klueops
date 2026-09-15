package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.ApplicationLifecycleRepositoryPort;
import io.strato.aiops.application.port.out.RuntimeLeasePort;
import io.strato.aiops.domain.job.AsyncJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

@Service
public class StaleJobRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StaleJobRecoveryService.class);

    private final AsyncJobRepositoryPort asyncJobRepositoryPort;
    private final long maximumRuntimeSeconds;
    private final RuntimeLeasePort runtimeLeasePort;
    private final ApplicationLifecycleRepositoryPort applicationLifecycleRepositoryPort;

    @org.springframework.beans.factory.annotation.Autowired
    public StaleJobRecoveryService(
            AsyncJobRepositoryPort asyncJobRepositoryPort,
            @Value("${aiops.jobs.maximum-runtime-seconds:900}") long maximumRuntimeSeconds,
            RuntimeLeasePort runtimeLeasePort,
            ApplicationLifecycleRepositoryPort applicationLifecycleRepositoryPort
    ) {
        this.asyncJobRepositoryPort = asyncJobRepositoryPort;
        this.maximumRuntimeSeconds = maximumRuntimeSeconds;
        this.runtimeLeasePort = runtimeLeasePort;
        this.applicationLifecycleRepositoryPort = applicationLifecycleRepositoryPort;
    }

    StaleJobRecoveryService(AsyncJobRepositoryPort asyncJobRepositoryPort, long maximumRuntimeSeconds) {
        this.asyncJobRepositoryPort = asyncJobRepositoryPort;
        this.maximumRuntimeSeconds = maximumRuntimeSeconds;
        this.runtimeLeasePort = RuntimeLeasePort.localOnly();
        this.applicationLifecycleRepositoryPort = null;
    }

    @Scheduled(fixedDelayString = "${aiops.jobs.recovery-interval-ms:60000}")
    @Transactional
    public void recoverStaleJobs() {
        if (!runtimeLeasePort.acquireOrRenew("scheduler:stale-job-recovery", Duration.ofSeconds(90))) return;
        int recovered = recoverStaleJobsAt(Instant.now());
        if (recovered > 0) {
            log.warn("Recovered {} stale asynchronous job(s) as timed out", recovered);
        }
    }

    int recoverStaleJobsAt(Instant now) {
        Instant cutoff = now.minusSeconds(maximumRuntimeSeconds);
        List<AsyncJob> staleJobs = asyncJobRepositoryPort.findActiveCreatedBefore(cutoff);
        staleJobs.forEach(job -> {
            String errorMessage = "Job exceeded the maximum runtime of " + maximumRuntimeSeconds + " seconds";
            job.markTimedOut(now, errorMessage);
            asyncJobRepositoryPort.save(job);
            if (applicationLifecycleRepositoryPort != null)
                applicationLifecycleRepositoryPort.recoverTimedOutOperation(job.id(), now, errorMessage);
        });
        if (applicationLifecycleRepositoryPort != null) {
            // 이미 terminal 상태인 Job에 남은 PENDING/RUNNING Helm 이력도 매 주기 복구한다.
            applicationLifecycleRepositoryPort.recoverOrphanedOperations(now);
        }
        return staleJobs.size();
    }
}
