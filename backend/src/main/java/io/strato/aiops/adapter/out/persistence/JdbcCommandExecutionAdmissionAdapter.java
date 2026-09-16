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

    /** JdbcCommandExecutionAdmissionAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JdbcCommandExecutionAdmissionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** JdbcCommandExecutionAdmissionAdapter의 acquire 처리에 필요한 업무 로직을 수행한다. */
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

    /** JdbcCommandExecutionAdmissionAdapter의 renew 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public void renew(UUID executionId, Instant expiresAt) {
        jdbc.update("update command_execution_leases set expires_at=? where execution_id=?",
                timestamp(expiresAt), executionId);
    }

    /** JdbcCommandExecutionAdmissionAdapter의 release 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public void release(UUID executionId) {
        jdbc.update("delete from command_execution_leases where execution_id=?", executionId);
    }

    /** JdbcCommandExecutionAdmissionAdapter의 isActive 처리 조건의 충족 여부를 판단한다. */
    @Override
    public boolean isActive(UUID executionId, Instant now) {
        return count("select count(*) from command_execution_leases where execution_id=? and expires_at>?",
                executionId, timestamp(now)) > 0;
    }

    /** JdbcCommandExecutionAdmissionAdapter의 count 처리에 필요한 업무 로직을 수행한다. */
    private int count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : Math.toIntExact(value);
    }

    /** JdbcCommandExecutionAdmissionAdapter의 exceeded 처리에 필요한 업무 로직을 수행한다. */
    private CommandCapacityExceededException exceeded(String message, int retryAfterSeconds) {
        return new CommandCapacityExceededException(message, retryAfterSeconds);
    }

    /** JdbcCommandExecutionAdmissionAdapter의 timestamp 처리에 필요한 업무 로직을 수행한다. */
    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
