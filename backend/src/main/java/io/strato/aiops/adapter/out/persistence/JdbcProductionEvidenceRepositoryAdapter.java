package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ProductionEvidenceRepositoryPort;
import io.strato.aiops.domain.operations.ProductionEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcProductionEvidenceRepositoryAdapter implements ProductionEvidenceRepositoryPort {

    private final JdbcTemplate jdbc;

    /** JdbcProductionEvidenceRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JdbcProductionEvidenceRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public ProductionEvidence.Run save(ProductionEvidence.Run run) {
        jdbc.update("delete from production_evidence_runs where id=?", run.id());
        jdbc.update("""
                insert into production_evidence_runs
                (id, release_name, environment_name, state, triggered_by, started_at, completed_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, run.id(), run.releaseName(), run.environment(), run.state().name(), run.triggeredBy(),
                timestamp(run.startedAt()), timestamp(run.completedAt()), timestamp(run.expiresAt()));
        for (ProductionEvidence.Check check : run.checks()) {
            jdbc.update("""
                    insert into production_evidence_checks
                    (id, run_id, category, code, state, title, detail, observed_value, action, duration_ms, checked_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, check.id(), run.id(), check.category(), check.code(), check.state().name(), check.title(),
                    check.detail(), check.observedValue(), check.action(), check.durationMs(), timestamp(check.checkedAt()));
            for (ProductionEvidence.Artifact artifact : check.artifacts()) {
                jdbc.update("""
                        insert into production_evidence_artifacts
                        (id, check_id, file_name, media_type, checksum, size_bytes, reference_value, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """, artifact.id(), check.id(), artifact.fileName(), artifact.mediaType(), artifact.checksum(),
                        artifact.sizeBytes(), artifact.reference(), timestamp(artifact.createdAt()));
            }
        }
        return findById(run.id()).orElseThrow();
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<ProductionEvidence.Run> findById(UUID id) {
        List<ProductionEvidence.Run> runs = jdbc.query(
                "select * from production_evidence_runs where id=?", this::run, id);
        return runs.isEmpty() ? Optional.empty() : Optional.of(withChecks(runs.get(0)));
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 findRecent 처리 결과를 조회해 반환한다. */
    @Override
    public List<ProductionEvidence.Run> findRecent(int limit) {
        return jdbc.query("select * from production_evidence_runs order by started_at desc limit ?", this::run, limit)
                .stream().map(this::withChecks).toList();
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 findByImportKey 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<ProductionEvidence.Run> findByImportKey(String importKey) {
        List<UUID> ids = jdbc.query("select run_id from production_evidence_import_keys where import_key=?",
                (rs, row) -> uuid(rs.getObject("run_id")), importKey);
        return ids.isEmpty() ? Optional.empty() : findById(ids.get(0));
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 saveImportKey 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public void saveImportKey(String importKey, UUID runId) {
        jdbc.update("insert into production_evidence_import_keys(import_key, run_id, created_at) values (?, ?, ?)",
                importKey, runId, Timestamp.from(Instant.now()));
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 withChecks 처리에 필요한 업무 로직을 수행한다. */
    private ProductionEvidence.Run withChecks(ProductionEvidence.Run run) {
        List<ProductionEvidence.Check> checks = jdbc.query(
                "select * from production_evidence_checks where run_id=? order by checked_at, code", this::check, run.id());
        return new ProductionEvidence.Run(run.id(), run.releaseName(), run.environment(), run.state(),
                run.triggeredBy(), run.startedAt(), run.completedAt(), run.expiresAt(), checks);
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 run 처리의 핵심 작업 흐름을 실행한다. */
    private ProductionEvidence.Run run(ResultSet rs, int row) throws SQLException {
        return new ProductionEvidence.Run(uuid(rs.getObject("id")), rs.getString("release_name"),
                rs.getString("environment_name"), ProductionEvidence.State.valueOf(rs.getString("state")),
                rs.getString("triggered_by"), instant(rs, "started_at"), instant(rs, "completed_at"),
                instant(rs, "expires_at"), List.of());
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 check 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ProductionEvidence.Check check(ResultSet rs, int row) throws SQLException {
        UUID id = uuid(rs.getObject("id"));
        List<ProductionEvidence.Artifact> artifacts = jdbc.query(
                "select * from production_evidence_artifacts where check_id=? order by created_at", this::artifact, id);
        return new ProductionEvidence.Check(id, uuid(rs.getObject("run_id")), rs.getString("category"),
                rs.getString("code"), ProductionEvidence.State.valueOf(rs.getString("state")),
                rs.getString("title"), rs.getString("detail"), rs.getString("observed_value"),
                rs.getString("action"), rs.getLong("duration_ms"), instant(rs, "checked_at"), artifacts);
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 artifact 처리에 필요한 업무 로직을 수행한다. */
    private ProductionEvidence.Artifact artifact(ResultSet rs, int row) throws SQLException {
        return new ProductionEvidence.Artifact(uuid(rs.getObject("id")), uuid(rs.getObject("check_id")),
                rs.getString("file_name"), rs.getString("media_type"), rs.getString("checksum"),
                rs.getLong("size_bytes"), rs.getString("reference_value"), instant(rs, "created_at"));
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 uuid 처리에 필요한 업무 로직을 수행한다. */
    private UUID uuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 timestamp 처리에 필요한 업무 로직을 수행한다. */
    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    /** JdbcProductionEvidenceRepositoryAdapter의 instant 처리에 필요한 업무 로직을 수행한다. */
    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
