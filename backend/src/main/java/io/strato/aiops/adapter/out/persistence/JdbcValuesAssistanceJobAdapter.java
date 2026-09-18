package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ValuesAssistanceJobPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.Optional;

/** AI 호출과 분리된 짧은 DB transaction으로 암호화된 제안을 저장한다. */
@Repository
public class JdbcValuesAssistanceJobAdapter implements ValuesAssistanceJobPort {
    private final JdbcTemplate jdbc;
    /** 저장에 필요한 DB 접근자를 주입한다. */
    public JdbcValuesAssistanceJobAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 공통 Job과 소유권 행을 원자적으로 생성한다. */
    @Override @Transactional
    public UUID create(UUID tenant, String actor, UUID chartVersion, String request) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into async_jobs(id,type,status,created_at) values (?,'HELM_VALUES','PENDING',CURRENT_TIMESTAMP)", id);
        jdbc.update("insert into values_assistance_jobs(job_id,tenant_id,actor,chart_version_id,request_payload) values (?,?,?,?,?)",
                id, tenant, actor, chartVersion, request);
        return id;
    }

    /** 작업 ID를 알아도 다른 Tenant 또는 사용자의 payload는 읽을 수 없다. */
    @Override public Optional<Entry> find(UUID id, UUID tenant, String actor) {
        return jdbc.query("select * from values_assistance_jobs where job_id=? and tenant_id=? and actor=?",
                (rs, row) -> new Entry(id, tenant, actor, rs.getObject("chart_version_id", UUID.class),
                        rs.getString("request_payload"), rs.getString("result_payload")), id, tenant, actor).stream().findFirst();
    }

    /** 중복 실행과 이미 취소된 작업 실행을 방지한다. */
    @Override public boolean start(UUID id) {
        return jdbc.update("update async_jobs set status='RUNNING',started_at=CURRENT_TIMESTAMP where id=? and status='PENDING'", id) == 1;
    }

    /** terminal 상태를 덮어쓰지 않고 성공 전환과 암호화 결과를 함께 저장한다. */
    @Override @Transactional
    public void complete(UUID id, String result, boolean generationFailed) {
        if (jdbc.update("update async_jobs set status=?,error_code=?,error_message=?,completed_at=CURRENT_TIMESTAMP where id=? and status='RUNNING'",
                generationFailed ? "FAILED" : "SUCCEEDED", generationFailed ? "VALUES_GENERATION_FAILED" : null,
                generationFailed ? "AI 제안 검증을 완료하지 못했습니다. 상세 결과를 확인해 주세요." : null, id) == 1)
            jdbc.update("update values_assistance_jobs set result_payload=?,request_payload=null where job_id=?", result, id);
        else jdbc.update("update values_assistance_jobs set request_payload=null where job_id=?", id);
    }

    /** 기술 오류 원문 대신 일반화된 사유를 기록한다. */
    @Override @Transactional
    public void fail(UUID id, String reason) {
        jdbc.update("update async_jobs set status='FAILED',completed_at=CURRENT_TIMESTAMP,error_code='VALUES_ASSISTANCE_FAILED',error_message=? where id=? and status in ('PENDING','RUNNING')", reason, id);
        jdbc.update("update values_assistance_jobs set request_payload=null where job_id=?", id);
    }

    /** terminal 작업의 원문은 지우고 암호화 결과도 7일 보관 후 정리한다. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${aiops.values-assistance.cleanup-interval-ms:3600000}")
    public void cleanExpiredPayloads() {
        jdbc.update("update values_assistance_jobs set request_payload=null where request_payload is not null and job_id in (select id from async_jobs where status not in ('PENDING','RUNNING'))");
        jdbc.update("update values_assistance_jobs set result_payload=null where result_payload is not null and job_id in (select id from async_jobs where completed_at < ?)",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(7))));
    }
}
