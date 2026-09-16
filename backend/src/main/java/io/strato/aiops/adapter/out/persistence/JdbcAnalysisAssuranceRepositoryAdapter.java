package io.strato.aiops.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.AnalysisAssuranceRepositoryPort;
import io.strato.aiops.domain.operations.OperationsModels.RegressionCaseResult;
import io.strato.aiops.domain.operations.OperationsModels.RegressionRun;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignalGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAnalysisAssuranceRepositoryAdapter implements AnalysisAssuranceRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** JdbcAnalysisAssuranceRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JdbcAnalysisAssuranceRepositoryAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 saveWatchSignal 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public void saveWatchSignal(WatchSignal signal) {
        jdbcTemplate.update("""
                insert into kubernetes_watch_signals
                (id, cluster_id, namespace, resource_kind, resource_name, action, reason, status, summary, observed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, signal.id(), signal.clusterId(), signal.namespace(), signal.resourceKind(), signal.resourceName(),
                signal.action(), signal.reason(), signal.status(), signal.summary(), timestamp(signal.observedAt()));
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 findWatchSignals 처리 결과를 조회해 반환한다. */
    @Override
    public List<WatchSignal> findWatchSignals(UUID clusterId, String namespace, int limit) {
        StringBuilder sql = new StringBuilder("""
                select s.*, c.name cluster_name from kubernetes_watch_signals s
                join clusters c on c.id=s.cluster_id where 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (clusterId != null) {
            sql.append(" and s.cluster_id=?");
            args.add(clusterId);
        }
        if (namespace != null && !namespace.isBlank()) {
            sql.append(" and s.namespace=?");
            args.add(namespace);
        }
        sql.append(" order by s.observed_at desc limit ?");
        args.add(Math.max(1, Math.min(500, limit)));
        return jdbcTemplate.query(sql.toString(), (rs, row) -> new WatchSignal(
                uuid(rs.getObject("id")), uuid(rs.getObject("cluster_id")), rs.getString("cluster_name"),
                rs.getString("namespace"), rs.getString("resource_kind"), rs.getString("resource_name"),
                rs.getString("action"), rs.getString("reason"), rs.getString("status"), rs.getString("summary"),
                instant(rs.getTimestamp("observed_at"))), args.toArray());
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 saveWatchSignalGroup 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    @Transactional
    public WatchSignalGroup saveWatchSignalGroup(WatchSignalGroup group) {
        int updated = jdbcTemplate.update("""
                update watch_signal_groups set severity=?, state=?, reason=?, summary=?, occurrence_count=?,
                last_observed_at=?, incident_id=?, updated_by=?, updated_at=? where id=?
                """, group.severity(), group.state(), group.reason(), group.summary(), group.occurrenceCount(),
                timestamp(group.lastObservedAt()), group.incidentId(), group.updatedBy(), timestamp(group.updatedAt()),
                group.id());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into watch_signal_groups
                    (id, fingerprint, cluster_id, namespace, resource_kind, resource_name, category, severity, state,
                     reason, summary, occurrence_count, first_observed_at, last_observed_at, incident_id, updated_by,
                     updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, group.id(), group.fingerprint(), group.clusterId(), group.namespace(), group.resourceKind(),
                    group.resourceName(), group.category(), group.severity(), group.state(), group.reason(),
                    group.summary(), group.occurrenceCount(), timestamp(group.firstObservedAt()),
                    timestamp(group.lastObservedAt()), group.incidentId(), group.updatedBy(), timestamp(group.updatedAt()));
        }
        return findWatchSignalGroup(group.id()).orElseThrow();
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 findWatchSignalGroup 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<WatchSignalGroup> findWatchSignalGroup(UUID groupId) {
        return jdbcTemplate.query("""
                select g.*, c.name cluster_name from watch_signal_groups g
                join clusters c on c.id=g.cluster_id where g.id=?
                """, signalGroupMapper(), groupId).stream().findFirst();
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 findWatchSignalGroupByFingerprint 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<WatchSignalGroup> findWatchSignalGroupByFingerprint(String fingerprint) {
        return jdbcTemplate.query("""
                select g.*, c.name cluster_name from watch_signal_groups g
                join clusters c on c.id=g.cluster_id where g.fingerprint=?
                """, signalGroupMapper(), fingerprint).stream().findFirst();
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 findWatchSignalGroups 처리 결과를 조회해 반환한다. */
    @Override
    public List<WatchSignalGroup> findWatchSignalGroups(UUID clusterId, String namespace, String state, int limit) {
        StringBuilder sql = new StringBuilder("""
                select g.*, c.name cluster_name from watch_signal_groups g
                join clusters c on c.id=g.cluster_id where 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (clusterId != null) {
            sql.append(" and g.cluster_id=?");
            args.add(clusterId);
        }
        if (namespace != null && !namespace.isBlank()) {
            sql.append(" and g.namespace=?");
            args.add(namespace);
        }
        if (state != null && !state.isBlank()) {
            sql.append(" and g.state=?");
            args.add(state.toUpperCase(java.util.Locale.ROOT));
        }
        sql.append("""
                 order by case g.severity when 'CRITICAL' then 4 when 'HIGH' then 3 when 'MEDIUM' then 2 else 1 end desc,
                 g.last_observed_at desc limit ?
                """);
        args.add(Math.max(1, Math.min(500, limit)));
        return jdbcTemplate.query(sql.toString(), signalGroupMapper(), args.toArray());
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 saveRegressionRun 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    @Transactional
    public RegressionRun saveRegressionRun(RegressionRun run) {
        jdbcTemplate.update("""
                insert into analysis_regression_runs
                (id, status, passed_cases, total_cases, score, baseline_version, triggered_by, started_at, completed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, run.id(), run.status(), run.passedCases(), run.totalCases(), run.score(), run.baselineVersion(),
                run.triggeredBy(), timestamp(run.startedAt()), timestamp(run.completedAt()));
        for (RegressionCaseResult result : run.cases()) {
            jdbcTemplate.update("""
                    insert into analysis_regression_case_results
                    (id, run_id, case_id, title, category, status, score, assertions_json, failures_json, duration_ms)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, result.id(), run.id(), result.caseId(), result.title(), result.category(), result.status(),
                    result.score(), json(result.assertions()), json(result.failures()), result.durationMs());
        }
        return findRegressionRun(run.id()).orElseThrow();
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 findRegressionRun 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<RegressionRun> findRegressionRun(UUID runId) {
        List<RegressionRun> runs = jdbcTemplate.query("select * from analysis_regression_runs where id=?",
                (rs, row) -> new RegressionRun(uuid(rs.getObject("id")), rs.getString("status"),
                        rs.getInt("passed_cases"), rs.getInt("total_cases"), rs.getDouble("score"),
                        rs.getString("baseline_version"), rs.getString("triggered_by"),
                        instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at")), List.of()), runId);
        if (runs.isEmpty()) return Optional.empty();
        RegressionRun run = runs.get(0);
        return Optional.of(new RegressionRun(run.id(), run.status(), run.passedCases(), run.totalCases(), run.score(),
                run.baselineVersion(), run.triggeredBy(), run.startedAt(), run.completedAt(), findCases(run.id())));
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 findRegressionRuns 처리 결과를 조회해 반환한다. */
    @Override
    public List<RegressionRun> findRegressionRuns(int limit) {
        List<RegressionRun> runs = jdbcTemplate.query(
                "select * from analysis_regression_runs order by started_at desc limit ?",
                (rs, row) -> new RegressionRun(uuid(rs.getObject("id")), rs.getString("status"),
                        rs.getInt("passed_cases"), rs.getInt("total_cases"), rs.getDouble("score"),
                        rs.getString("baseline_version"), rs.getString("triggered_by"),
                        instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at")), List.of()),
                Math.max(1, Math.min(100, limit)));
        if (runs.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", Collections.nCopies(runs.size(), "?"));
        Object[] runIds = runs.stream().map(RegressionRun::id).toArray();
        List<RegressionCaseResult> caseResults = jdbcTemplate.query("""
                        select * from analysis_regression_case_results
                        where run_id in (%s)
                        order by run_id, case_id
                        """.formatted(placeholders),
                (rs, row) -> regressionCase(rs), runIds);
        Map<UUID, List<RegressionCaseResult>> casesByRun = new HashMap<>();
        for (RegressionCaseResult result : caseResults) {
            casesByRun.computeIfAbsent(result.runId(), ignored -> new ArrayList<>()).add(result);
        }
        return runs.stream()
                .map(run -> new RegressionRun(run.id(), run.status(), run.passedCases(), run.totalCases(), run.score(),
                        run.baselineVersion(), run.triggeredBy(), run.startedAt(), run.completedAt(),
                        List.copyOf(casesByRun.getOrDefault(run.id(), List.of()))))
                .toList();
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 findCases 처리 결과를 조회해 반환한다. */
    private List<RegressionCaseResult> findCases(UUID runId) {
        return jdbcTemplate.query("select * from analysis_regression_case_results where run_id=? order by case_id",
                (rs, row) -> regressionCase(rs), runId);
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 regressionCase 처리에 필요한 업무 로직을 수행한다. */
    private RegressionCaseResult regressionCase(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RegressionCaseResult(uuid(rs.getObject("id")), uuid(rs.getObject("run_id")),
                rs.getString("case_id"), rs.getString("title"), rs.getString("category"),
                rs.getString("status"), rs.getInt("score"), strings(rs.getString("assertions_json")),
                strings(rs.getString("failures_json")), rs.getLong("duration_ms"));
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 signalGroupMapper 처리에 필요한 업무 로직을 수행한다. */
    private org.springframework.jdbc.core.RowMapper<WatchSignalGroup> signalGroupMapper() {
        return (rs, row) -> new WatchSignalGroup(
                uuid(rs.getObject("id")), rs.getString("fingerprint"), uuid(rs.getObject("cluster_id")),
                rs.getString("cluster_name"), rs.getString("namespace"), rs.getString("resource_kind"),
                rs.getString("resource_name"), rs.getString("category"), rs.getString("severity"),
                rs.getString("state"), rs.getString("reason"), rs.getString("summary"),
                rs.getInt("occurrence_count"), instant(rs.getTimestamp("first_observed_at")),
                instant(rs.getTimestamp("last_observed_at")),
                rs.getObject("incident_id") == null ? null : uuid(rs.getObject("incident_id")),
                rs.getString("updated_by"), instant(rs.getTimestamp("updated_at")));
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 json 처리에 필요한 업무 로직을 수행한다. */
    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize regression result", exception);
        }
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 strings 처리에 필요한 업무 로직을 수행한다. */
    private List<String> strings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 uuid 처리에 필요한 업무 로직을 수행한다. */
    private UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 timestamp 처리에 필요한 업무 로직을 수행한다. */
    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    /** JdbcAnalysisAssuranceRepositoryAdapter의 instant 처리에 필요한 업무 로직을 수행한다. */
    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
