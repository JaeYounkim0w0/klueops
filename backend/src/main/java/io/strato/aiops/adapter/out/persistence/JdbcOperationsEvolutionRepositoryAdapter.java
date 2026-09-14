package io.strato.aiops.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.OperationsEvolutionRepositoryPort;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.AiReleaseGate;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.IncidentPostmortem;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.RemediationObservation;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.SignalNoisePolicy;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.WatchContinuity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOperationsEvolutionRepositoryAdapter implements OperationsEvolutionRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOperationsEvolutionRepositoryAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WatchContinuity saveWatchContinuity(WatchContinuity value) {
        int updated = jdbcTemplate.update("""
                update watch_continuity set pod_resource_version=?, event_resource_version=?, continuity_state=?,
                gap_signal_count=?, last_reconciled_at=?, last_error=?, updated_at=? where cluster_id=?
                """, value.podResourceVersion(), value.eventResourceVersion(), value.continuityState(),
                value.gapSignalCount(), timestamp(value.lastReconciledAt()), value.lastError(),
                timestamp(value.updatedAt()), value.clusterId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into watch_continuity (cluster_id, pod_resource_version, event_resource_version,
                    continuity_state, gap_signal_count, last_reconciled_at, last_error, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, value.clusterId(), value.podResourceVersion(), value.eventResourceVersion(),
                    value.continuityState(), value.gapSignalCount(), timestamp(value.lastReconciledAt()),
                    value.lastError(), timestamp(value.updatedAt()));
        }
        return findWatchContinuity(value.clusterId()).orElseThrow();
    }

    @Override
    public Optional<WatchContinuity> findWatchContinuity(UUID clusterId) {
        return first(jdbcTemplate.query("select * from watch_continuity where cluster_id=?",
                this::watchContinuity, clusterId));
    }

    @Override
    public List<WatchContinuity> findWatchContinuities() {
        return jdbcTemplate.query("select * from watch_continuity order by updated_at desc", this::watchContinuity);
    }

    @Override
    public SignalNoisePolicy saveNoisePolicy(SignalNoisePolicy value) {
        int updated = jdbcTemplate.update("""
                update signal_noise_policies set name=?, cluster_id=?, namespace_pattern=?, severity_floor=?,
                repeat_threshold=?, maintenance_start=?, maintenance_end=?, snooze_until=?, enabled=?,
                updated_by=?, updated_at=? where id=?
                """, value.name(), value.clusterId(), value.namespacePattern(), value.severityFloor(),
                value.repeatThreshold(), timestamp(value.maintenanceStart()), timestamp(value.maintenanceEnd()),
                timestamp(value.snoozeUntil()), value.enabled(), value.updatedBy(), timestamp(value.updatedAt()), value.id());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into signal_noise_policies (id, name, cluster_id, namespace_pattern, severity_floor,
                    repeat_threshold, maintenance_start, maintenance_end, snooze_until, enabled, updated_by, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, value.id(), value.name(), value.clusterId(), value.namespacePattern(), value.severityFloor(),
                    value.repeatThreshold(), timestamp(value.maintenanceStart()), timestamp(value.maintenanceEnd()),
                    timestamp(value.snoozeUntil()), value.enabled(), value.updatedBy(), timestamp(value.updatedAt()));
        }
        return findNoisePolicy(value.id()).orElseThrow();
    }

    @Override
    public Optional<SignalNoisePolicy> findNoisePolicy(UUID policyId) {
        return first(jdbcTemplate.query("select * from signal_noise_policies where id=?", this::noisePolicy, policyId));
    }

    @Override
    public List<SignalNoisePolicy> findNoisePolicies() {
        return jdbcTemplate.query("""
                select * from signal_noise_policies
                order by enabled desc, updated_at desc
                """, this::noisePolicy);
    }

    @Override
    public void deleteNoisePolicy(UUID policyId) {
        jdbcTemplate.update("delete from signal_noise_policies where id=?", policyId);
    }

    @Override
    public RemediationObservation saveRemediationObservation(RemediationObservation value) {
        int updated = jdbcTemplate.update("""
                update remediation_observations set state=?, latest_json=?, conclusion=?, rollback_candidate=?,
                updated_by=?, updated_at=? where id=?
                """, value.state(), value.latestJson(), value.conclusion(), value.rollbackCandidate(),
                value.updatedBy(), timestamp(value.updatedAt()), value.id());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into remediation_observations (id, incident_id, analysis_id, command_execution_id, state,
                    observation_seconds, started_at, observe_until, baseline_json, latest_json, conclusion,
                    rollback_candidate, updated_by, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, value.id(), value.incidentId(), value.analysisId(), value.commandExecutionId(), value.state(),
                    value.observationSeconds(), timestamp(value.startedAt()), timestamp(value.observeUntil()),
                    value.baselineJson(), value.latestJson(), value.conclusion(), value.rollbackCandidate(),
                    value.updatedBy(), timestamp(value.updatedAt()));
        }
        return findRemediationObservation(value.id()).orElseThrow();
    }

    @Override
    public Optional<RemediationObservation> findRemediationObservation(UUID observationId) {
        return first(jdbcTemplate.query("select * from remediation_observations where id=?",
                this::remediationObservation, observationId));
    }

    @Override
    public List<RemediationObservation> findRemediationObservations(UUID incidentId, String state, int limit) {
        StringBuilder sql = new StringBuilder("select * from remediation_observations where 1=1");
        List<Object> args = new ArrayList<>();
        if (incidentId != null) {
            sql.append(" and incident_id=?");
            args.add(incidentId);
        }
        if (state != null && !state.isBlank()) {
            sql.append(" and state=?");
            args.add(state);
        }
        sql.append(" order by started_at desc limit ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return jdbcTemplate.query(sql.toString(), this::remediationObservation, args.toArray());
    }

    @Override
    public AiReleaseGate saveReleaseGate(AiReleaseGate value) {
        jdbcTemplate.update("""
                insert into ai_release_gates (id, candidate_version, baseline_version, state, regression_score,
                minimum_regression_score, ground_truth_samples, minimum_ground_truth_samples, verified_accuracy,
                minimum_verified_accuracy, dangerous_suggestion_count, reasons_json, evaluated_at, evaluated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, value.id(), value.candidateVersion(), value.baselineVersion(), value.state(),
                value.regressionScore(), value.minimumRegressionScore(), value.groundTruthSamples(),
                value.minimumGroundTruthSamples(), value.verifiedAccuracy(), value.minimumVerifiedAccuracy(),
                value.dangerousSuggestionCount(), json(value.reasons()), timestamp(value.evaluatedAt()),
                value.evaluatedBy());
        return findReleaseGates(500).stream().filter(item -> item.id().equals(value.id())).findFirst().orElseThrow();
    }

    @Override
    public List<AiReleaseGate> findReleaseGates(int limit) {
        return jdbcTemplate.query("select * from ai_release_gates order by evaluated_at desc limit ?",
                this::releaseGate, Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public IncidentPostmortem savePostmortem(IncidentPostmortem value) {
        int updated = jdbcTemplate.update("""
                update incident_postmortems set title=?, impact=?, root_cause=?, resolution=?, evidence_json=?,
                prevention_json=?, generated_at=?, generated_by=? where incident_id=?
                """, value.title(), value.impact(), value.rootCause(), value.resolution(), json(value.evidence()),
                json(value.prevention()), timestamp(value.generatedAt()), value.generatedBy(), value.incidentId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into incident_postmortems (incident_id, title, impact, root_cause, resolution,
                    evidence_json, prevention_json, generated_at, generated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, value.incidentId(), value.title(), value.impact(), value.rootCause(), value.resolution(),
                    json(value.evidence()), json(value.prevention()), timestamp(value.generatedAt()), value.generatedBy());
        }
        return findPostmortem(value.incidentId()).orElseThrow();
    }

    @Override
    public Optional<IncidentPostmortem> findPostmortem(UUID incidentId) {
        return first(jdbcTemplate.query("select * from incident_postmortems where incident_id=?",
                this::postmortem, incidentId));
    }

    private WatchContinuity watchContinuity(ResultSet rs, int row) throws SQLException {
        return new WatchContinuity(uuid(rs, "cluster_id"), rs.getString("pod_resource_version"),
                rs.getString("event_resource_version"), rs.getString("continuity_state"),
                rs.getInt("gap_signal_count"), instant(rs, "last_reconciled_at"), rs.getString("last_error"),
                instant(rs, "updated_at"));
    }

    private SignalNoisePolicy noisePolicy(ResultSet rs, int row) throws SQLException {
        return new SignalNoisePolicy(uuid(rs, "id"), rs.getString("name"), nullableUuid(rs, "cluster_id"),
                rs.getString("namespace_pattern"), rs.getString("severity_floor"), rs.getInt("repeat_threshold"),
                instant(rs, "maintenance_start"), instant(rs, "maintenance_end"), instant(rs, "snooze_until"),
                rs.getBoolean("enabled"), rs.getString("updated_by"), instant(rs, "updated_at"));
    }

    private RemediationObservation remediationObservation(ResultSet rs, int row) throws SQLException {
        return new RemediationObservation(uuid(rs, "id"), uuid(rs, "incident_id"),
                nullableUuid(rs, "analysis_id"), nullableUuid(rs, "command_execution_id"), rs.getString("state"),
                rs.getInt("observation_seconds"), instant(rs, "started_at"), instant(rs, "observe_until"),
                rs.getString("baseline_json"), rs.getString("latest_json"), rs.getString("conclusion"),
                rs.getString("rollback_candidate"), rs.getString("updated_by"), instant(rs, "updated_at"));
    }

    private AiReleaseGate releaseGate(ResultSet rs, int row) throws SQLException {
        return new AiReleaseGate(uuid(rs, "id"), rs.getString("candidate_version"),
                rs.getString("baseline_version"), rs.getString("state"), rs.getDouble("regression_score"),
                rs.getDouble("minimum_regression_score"), rs.getInt("ground_truth_samples"),
                rs.getInt("minimum_ground_truth_samples"), rs.getDouble("verified_accuracy"),
                rs.getDouble("minimum_verified_accuracy"), rs.getInt("dangerous_suggestion_count"),
                strings(rs.getString("reasons_json")), instant(rs, "evaluated_at"), rs.getString("evaluated_by"));
    }

    private IncidentPostmortem postmortem(ResultSet rs, int row) throws SQLException {
        return new IncidentPostmortem(uuid(rs, "incident_id"), rs.getString("title"), rs.getString("impact"),
                rs.getString("root_cause"), rs.getString("resolution"), strings(rs.getString("evidence_json")),
                strings(rs.getString("prevention_json")), instant(rs, "generated_at"), rs.getString("generated_by"));
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize operations data", exception);
        }
    }

    private List<String> strings(String value) {
        try {
            return value == null ? List.of() : objectMapper.readValue(value, new TypeReference<>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }
}
