package io.strato.aiops.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class JdbcValuesAssistanceJobAdapterTest {
    @Autowired JdbcTemplate jdbc;

    /** 소유권 격리, 단일 실행 및 요청 원문 제거를 검증한다. */
    @Test void isolatesOwnersAndCompletesAtomically() {
        var adapter = new JdbcValuesAssistanceJobAdapter(jdbc);
        UUID tenant = UUID.randomUUID();
        UUID id = adapter.create(tenant, "owner", UUID.randomUUID(), "encrypted-input");
        assertThat(adapter.find(id, UUID.randomUUID(), "owner")).isEmpty();
        assertThat(adapter.find(id, tenant, "other")).isEmpty();
        assertThat(adapter.start(id)).isTrue();
        assertThat(adapter.start(id)).isFalse();
        adapter.complete(id, "encrypted-output", false);
        var result = adapter.find(id, tenant, "owner").orElseThrow();
        assertThat(result.request()).isNull();
        assertThat(result.result()).isEqualTo("encrypted-output");
    }

    /** 취소된 작업에 늦게 도착한 성공 결과가 상태를 덮어쓰지 않는다. */
    @Test void neverCompletesCanceledJob() {
        var adapter = new JdbcValuesAssistanceJobAdapter(jdbc);
        UUID tenant = UUID.randomUUID();
        UUID id = adapter.create(tenant, "owner", UUID.randomUUID(), "encrypted-input");
        adapter.start(id);
        jdbc.update("update async_jobs set status='CANCELED' where id=?", id);
        adapter.complete(id, "late-result", false);
        assertThat(adapter.find(id, tenant, "owner").orElseThrow().result()).isNull();
        assertThat(jdbc.queryForObject("select status from async_jobs where id=?", String.class, id)).isEqualTo("CANCELED");
    }

    /** 검증 실패 결과는 보관하되 공통 작업 상태를 성공으로 표시하지 않는다. */
    @Test void keepsFailedProposalWithoutReportingSuccess() {
        var adapter = new JdbcValuesAssistanceJobAdapter(jdbc);
        UUID tenant = UUID.randomUUID();
        UUID id = adapter.create(tenant, "owner", UUID.randomUUID(), "encrypted-input");
        adapter.start(id);
        adapter.complete(id, "encrypted-failure", true);
        assertThat(adapter.find(id, tenant, "owner").orElseThrow().result()).isEqualTo("encrypted-failure");
        assertThat(jdbc.queryForObject("select status from async_jobs where id=?", String.class, id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select error_code from async_jobs where id=?", String.class, id)).isEqualTo("VALUES_GENERATION_FAILED");
    }
}
