package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
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

    @org.springframework.beans.factory.annotation.Autowired
    public StaleJobRecoveryService(
            AsyncJobRepositoryPort asyncJobRepositoryPort,
            @Value("${aiops.jobs.maximum-runtime-seconds:900}") long maximumRuntimeSeconds,
            RuntimeLeasePort runtimeLeasePort
    ) {
        this.asyncJobRepositoryPort = asyncJobRepositoryPort;
        this.maximumRuntimeSeconds = maximumRuntimeSeconds;
        this.runtimeLeasePort = runtimeLeasePort;
    }

    StaleJobRecoveryService(AsyncJobRepositoryPort asyncJobRepositoryPort, long maximumRuntimeSeconds) {
        this(asyncJobRepositoryPort, maximumRuntimeSeconds, RuntimeLeasePort.localOnly());
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
            job.markTimedOut(now, "Job exceeded the maximum runtime of " + maximumRuntimeSeconds + " seconds");
            asyncJobRepositoryPort.save(job);
        });
        return staleJobs.size();
    }
}
