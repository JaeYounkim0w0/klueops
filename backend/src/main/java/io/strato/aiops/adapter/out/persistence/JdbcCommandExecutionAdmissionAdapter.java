package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.CommandExecutionAdmissionPort;
import io.strato.aiops.application.service.CommandCapacityExceededException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Repository
public class JdbcCommandExecutionAdmissionAdapter implements CommandExecutionAdmissionPort {
    private static final String ADMISSION_LOCK = "aiops-command-execution-admission";
    private final JdbcTemplate jdbc;

    public JdbcCommandExecutionAdmissionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void acquire(AdmissionRequest request) {
        jdbc.execute("select pg_advisory_xact_lock(hashtext('" + ADMISSION_LOCK + "'))");
        jdbc.update("delete from command_execution_leases where expires_at <= ?", timestamp(request.acquiredAt()));
        jdbc.update("delete from command_execution_rate_events where created_at < ?",
                timestamp(request.acquiredAt().minus(1, ChronoUnit.HOURS)));

        int userActive = count("select count(*) from command_execution_leases where actor=? and mode=? and expires_at>?",
                request.actor(), request.mode().name(), timestamp(request.acquiredAt()));
        int clusterActive = count("select count(*) from command_execution_leases where cluster_id=? and mode=? and expires_at>?",
                request.clusterId(), request.mode().name(), timestamp(request.acquiredAt()));
        Instant rateWindow = request.acquiredAt().minus(1, ChronoUnit.MINUTES);
        int userRate = count("select count(*) from command_execution_rate_events where actor=? and created_at>=?",
                request.actor(), timestamp(rateWindow));
        int clusterRate = count("select count(*) from command_execution_rate_events where cluster_id=? and created_at>=?",
                request.clusterId(), timestamp(rateWindow));

        if (userActive >= request.maximumUserConcurrency()) {
            throw exceeded("User concurrent command limit reached", 10);
        }
        if (clusterActive >= request.maximumClusterConcurrency()) {
            throw exceeded("Cluster concurrent command limit reached", 10);
        }
        if (userRate >= request.maximumUserStartsPerMinute()) {
            throw exceeded("User command start rate limit reached", 60);
        }
        if (clusterRate >= request.maximumClusterStartsPerMinute()) {
            throw exceeded("Cluster command start rate limit reached", 60);
        }

        jdbc.update("""
                insert into command_execution_leases(execution_id, cluster_id, actor, mode, acquired_at, expires_at)
                values (?, ?, ?, ?, ?, ?)
                """, request.executionId(), request.clusterId(), request.actor(), request.mode().name(),
                timestamp(request.acquiredAt()), timestamp(request.expiresAt()));
        jdbc.update("""
                insert into command_execution_rate_events(execution_id, cluster_id, actor, mode, created_at)
                values (?, ?, ?, ?, ?)
                """, request.executionId(), request.clusterId(), request.actor(), request.mode().name(),
                timestamp(request.acquiredAt()));
    }

    @Override
    public void renew(UUID executionId, Instant expiresAt) {
        jdbc.update("update command_execution_leases set expires_at=? where execution_id=?",
                timestamp(expiresAt), executionId);
    }

    @Override
    public void release(UUID executionId) {
        jdbc.update("delete from command_execution_leases where execution_id=?", executionId);
    }

    @Override
    public boolean isActive(UUID executionId, Instant now) {
        return count("select count(*) from command_execution_leases where execution_id=? and expires_at>?",
                executionId, timestamp(now)) > 0;
    }

    private int count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : Math.toIntExact(value);
    }

    private CommandCapacityExceededException exceeded(String message, int retryAfterSeconds) {
        return new CommandCapacityExceededException(message, retryAfterSeconds);
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
