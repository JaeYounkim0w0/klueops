package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.CommandExecutionAdmissionPort;
import io.strato.aiops.application.service.CommandCapacityExceededException;
import io.strato.aiops.domain.command.CommandExecutionMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
class JdbcCommandExecutionAdmissionAdapterTest {
    @Autowired JdbcTemplate jdbc;

    /** JdbcCommandExecutionAdmissionAdapterTest의 enforcesUserConcurrencyAndReleaseAllowsAnotherExecution 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void enforcesUserConcurrencyAndReleaseAllowsAnotherExecution() {
        JdbcCommandExecutionAdmissionAdapter adapter = new JdbcCommandExecutionAdmissionAdapter(jdbc);
        Instant now = Instant.parse("2026-09-09T05:00:00Z");
        UUID clusterId = UUID.randomUUID();
        UUID first = UUID.randomUUID();

        adapter.acquire(request(first, clusterId, "operator", now, 1, 10, 30, 120));

        assertThatThrownBy(() -> adapter.acquire(request(UUID.randomUUID(), clusterId, "operator",
                now.plusSeconds(1), 1, 10, 30, 120)))
                .isInstanceOf(CommandCapacityExceededException.class)
                .hasMessageContaining("User concurrent command limit");

        adapter.release(first);
        assertThat(adapter.isActive(first, now.plusSeconds(2))).isFalse();
    }

    /** JdbcCommandExecutionAdmissionAdapterTest의 expiredLeaseNoLongerConsumesConcurrency 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void expiredLeaseNoLongerConsumesConcurrency() {
        JdbcCommandExecutionAdmissionAdapter adapter = new JdbcCommandExecutionAdmissionAdapter(jdbc);
        Instant now = Instant.parse("2026-09-09T05:00:00Z");
        UUID clusterId = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        adapter.acquire(request(expired, clusterId, "operator", now, 1, 10, 30, 120));
        jdbc.update("update command_execution_leases set expires_at=? where execution_id=?",
                java.sql.Timestamp.from(now.minusSeconds(1)), expired);

        UUID replacement = UUID.randomUUID();
        adapter.acquire(request(replacement, clusterId, "operator", now.plusSeconds(1), 1, 10, 30, 120));

        assertThat(adapter.isActive(replacement, now.plusSeconds(1))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from command_execution_leases where execution_id=?",
                Long.class, expired)).isZero();
    }

    /** JdbcCommandExecutionAdmissionAdapterTest의 request 처리에 필요한 업무 로직을 수행한다. */
    private CommandExecutionAdmissionPort.AdmissionRequest request(UUID executionId, UUID clusterId, String actor,
            Instant now, int userConcurrency, int clusterConcurrency, int userRate, int clusterRate) {
        return new CommandExecutionAdmissionPort.AdmissionRequest(executionId, clusterId, actor,
                CommandExecutionMode.COMMAND, now, now.plusSeconds(60), userConcurrency, clusterConcurrency,
                userRate, clusterRate);
    }
}
