package io.strato.aiops.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.OperationsReadinessRepositoryPort;
import io.strato.aiops.domain.operations.OperationsReadinessModels.LiveValidationRun;
import io.strato.aiops.domain.operations.OperationsReadinessModels.OutcomeAggregate;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ReliabilityDay;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ReliabilityScope;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ReliabilityTrendData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOperationsReadinessRepositoryAdapter implements OperationsReadinessRepositoryPort {

    private static final int MAX_TREND_INCIDENTS = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOperationsReadinessRepositoryAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public LiveValidationRun saveLiveValidationRun(LiveValidationRun value) {
        int updated = jdbcTemplate.update("""
                update live_validation_runs set state=?, safety_checks_json=?, resources_json=?, observed_signal=?,
                detail=?, cleanup_required=?, completed_at=? where id=?
                """, value.state(), json(value.safetyChecks()), json(value.resources()), value.observedSignal(),
                value.detail(), value.cleanupRequired(), timestamp(value.completedAt()), value.id());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into live_validation_runs (id, cluster_id, cluster_name, scenario_id, namespace, state,
                    safety_mode, ttl_seconds, safety_checks_json, resources_json, observed_signal, detail,
                    cleanup_required, started_at, expires_at, completed_at, triggered_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, value.id(), value.clusterId(), value.clusterName(), value.scenarioId(), value.namespace(),
                    value.state(), value.safetyMode(), value.ttlSeconds(), json(value.safetyChecks()),
                    json(value.resources()), value.observedSignal(), value.detail(), value.cleanupRequired(),
                    timestamp(value.startedAt()), timestamp(value.expiresAt()), timestamp(value.completedAt()),
                    value.triggeredBy());
        }
        return findLiveValidationRun(value.id()).orElseThrow();
    }

    @Override
    public Optional<LiveValidationRun> findLiveValidationRun(UUID runId) {
        List<LiveValidationRun> values = jdbcTemplate.query(
                "select * from live_validation_runs where id=?", this::liveValidationRun, runId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    @Override
    public List<LiveValidationRun> findActiveLiveValidationRuns(UUID clusterId) {
        return jdbcTemplate.query("""
                select * from live_validation_runs
                where cluster_id=? and cleanup_required=true
                order by started_at desc limit 10
                """, this::liveValidationRun, clusterId);
    }

    @Override
    public List<LiveValidationRun> findExpiredLiveValidationRuns(Instant now, int limit) {
        return jdbcTemplate.query("""
                select * from live_validation_runs
                where cleanup_required=true and expires_at <= ?
                order by expires_at asc limit ?
                """, this::liveValidationRun, timestamp(now), Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public List<OutcomeAggregate> aggregateRemediationOutcomes(String category, String resourceKind, int limit) {
        StringBuilder sql = new StringBuilder("""
                select i.category, i.resource_kind, coalesce(c.command_text, 'GUIDED_VERIFICATION') action,
                count(*) samples,
                sum(case when r.state='SUCCEEDED' then 1 else 0 end) succeeded,
                sum(case when r.state='FAILED' then 1 else 0 end) failed,
                sum(case when r.state='INCONCLUSIVE' then 1 else 0 end) inconclusive,
                avg(r.observation_seconds) average_seconds
                from remediation_observations r
                join incidents i on i.id=r.incident_id
                left join analysis_command_executions c on c.id=r.command_execution_id
                where r.state in ('SUCCEEDED','FAILED','INCONCLUSIVE')
                """);
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            sql.append(" and i.category=?");
            args.add(category);
        }
        if (resourceKind != null && !resourceKind.isBlank()) {
            sql.append(" and i.resource_kind=?");
            args.add(resourceKind);
        }
        sql.append("""

                group by i.category, i.resource_kind, coalesce(c.command_text, 'GUIDED_VERIFICATION')
                order by succeeded desc, samples desc limit ?
                """);
        args.add(Math.max(1, Math.min(limit, 50)));
        return jdbcTemplate.query(sql.toString(), (rs, row) -> new OutcomeAggregate(
                rs.getString("category"), rs.getString("resource_kind"), rs.getString("action"),
                rs.getInt("samples"), rs.getInt("succeeded"), rs.getInt("failed"),
                rs.getInt("inconclusive"), Math.round(rs.getDouble("average_seconds"))), args.toArray());
    }

    @Override
    public ReliabilityTrendData queryReliabilityTrend(UUID clusterId, String namespace, Instant from) {
        StringBuilder sql = new StringBuilder("""
                select i.id, i.cluster_id, c.name cluster_name, i.namespace, i.first_detected_at,
                i.state, i.reopen_count,
                (select min(a.created_at) from incident_activities a
                  where a.incident_id=i.id and a.to_state in ('ACKNOWLEDGED','INVESTIGATING','MITIGATING',
                  'MONITORING','RESOLVED')) acknowledged_at,
                (select min(a.created_at) from incident_activities a
                  where a.incident_id=i.id and a.to_state='RESOLVED') resolved_at
                from incidents i join clusters c on c.id=i.cluster_id
                where i.first_detected_at >= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(timestamp(from));
        if (clusterId != null) {
            sql.append(" and i.cluster_id=?");
            args.add(clusterId);
        }
        if (namespace != null && !namespace.isBlank()) {
            sql.append(" and i.namespace=?");
            args.add(namespace);
        }
        sql.append(" order by i.first_detected_at desc limit ?");
        args.add(MAX_TREND_INCIDENTS);
        List<TrendIncident> incidents = jdbcTemplate.query(sql.toString(), this::trendIncident, args.toArray());

        long acknowledgeMinutes = averageMinutes(incidents, true);
        long resolveMinutes = averageMinutes(incidents, false);
        int resolved = (int) incidents.stream().filter(item -> item.resolvedAt() != null).count();
        int recurring = (int) incidents.stream().filter(item -> item.reopenCount() > 0).count();

        Map<LocalDate, int[]> daily = new LinkedHashMap<>();
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            daily.put(day, new int[3]);
        }
        Map<String, ScopeCounter> scopes = new LinkedHashMap<>();
        incidents.forEach(item -> {
            int[] bucket = daily.get(item.detectedAt().atZone(ZoneOffset.UTC).toLocalDate());
            if (bucket != null) {
                bucket[0]++;
                if (item.resolvedAt() != null) bucket[1]++;
                if (item.reopenCount() > 0) bucket[2]++;
            }
            String key = item.clusterId() + "|" + text(item.namespace());
            ScopeCounter scope = scopes.computeIfAbsent(key, ignored -> new ScopeCounter(
                    item.clusterId(), item.clusterName(), item.namespace()));
            scope.detected++;
            if (item.resolvedAt() == null) scope.open++;
            else scope.resolved++;
            if (item.reopenCount() > 0) scope.recurred++;
        });

        int[] remediation = remediationCounts(clusterId, namespace, from);
        List<ReliabilityDay> days = daily.entrySet().stream()
                .map(entry -> new ReliabilityDay(entry.getKey().toString(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[2]))
                .toList();
        List<ReliabilityScope> scopeValues = scopes.values().stream()
                .map(value -> new ReliabilityScope(value.clusterId, value.clusterName, value.namespace,
                        value.detected, value.open, value.resolved, value.recurred))
                .sorted((left, right) -> Integer.compare(right.detected(), left.detected()))
                .limit(30)
                .toList();
        return new ReliabilityTrendData(incidents.size(), resolved, recurring, acknowledgeMinutes, resolveMinutes,
                remediation[0], remediation[1], days, scopeValues);
    }

    private int[] remediationCounts(UUID clusterId, String namespace, Instant from) {
        StringBuilder sql = new StringBuilder("""
                select count(*) samples,
                sum(case when r.state='SUCCEEDED' then 1 else 0 end) succeeded
                from remediation_observations r join incidents i on i.id=r.incident_id
                where r.started_at >= ? and r.state in ('SUCCEEDED','FAILED','INCONCLUSIVE')
                """);
        List<Object> args = new ArrayList<>();
        args.add(timestamp(from));
        if (clusterId != null) {
            sql.append(" and i.cluster_id=?");
            args.add(clusterId);
        }
        if (namespace != null && !namespace.isBlank()) {
            sql.append(" and i.namespace=?");
            args.add(namespace);
        }
        return jdbcTemplate.query(sql.toString(), rs -> {
            if (!rs.next()) return new int[2];
            return new int[]{rs.getInt("samples"), rs.getInt("succeeded")};
        }, args.toArray());
    }

    private long averageMinutes(List<TrendIncident> incidents, boolean acknowledge) {
        return Math.round(incidents.stream()
                .filter(item -> acknowledge ? item.acknowledgedAt() != null : item.resolvedAt() != null)
                .mapToLong(item -> Duration.between(item.detectedAt(),
                        acknowledge ? item.acknowledgedAt() : item.resolvedAt()).toMinutes())
                .filter(value -> value >= 0)
                .average().orElse(0));
    }

    private LiveValidationRun liveValidationRun(ResultSet rs, int row) throws SQLException {
        return new LiveValidationRun(uuid(rs, "id"), uuid(rs, "cluster_id"), rs.getString("cluster_name"),
                rs.getString("scenario_id"), rs.getString("namespace"), rs.getString("state"),
                rs.getString("safety_mode"), rs.getInt("ttl_seconds"), strings(rs.getString("safety_checks_json")),
                strings(rs.getString("resources_json")), rs.getString("observed_signal"), rs.getString("detail"),
                rs.getBoolean("cleanup_required"), instant(rs, "started_at"), instant(rs, "expires_at"),
                instant(rs, "completed_at"), rs.getString("triggered_by"));
    }

    private TrendIncident trendIncident(ResultSet rs, int row) throws SQLException {
        return new TrendIncident(uuid(rs, "id"), uuid(rs, "cluster_id"), rs.getString("cluster_name"),
                rs.getString("namespace"), instant(rs, "first_detected_at"), instant(rs, "acknowledged_at"),
                instant(rs, "resolved_at"), rs.getInt("reopen_count"));
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize readiness data", exception);
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
        Object value = rs.getObject(column);
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private record TrendIncident(UUID id, UUID clusterId, String clusterName, String namespace, Instant detectedAt,
                                 Instant acknowledgedAt, Instant resolvedAt, int reopenCount) {
    }

    private static final class ScopeCounter {
        private final UUID clusterId;
        private final String clusterName;
        private final String namespace;
        private int detected;
        private int open;
        private int resolved;
        private int recurred;

        private ScopeCounter(UUID clusterId, String clusterName, String namespace) {
            this.clusterId = clusterId;
            this.clusterName = clusterName;
            this.namespace = namespace;
        }
    }
}
