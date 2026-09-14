package io.strato.aiops.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.OperatorWorkspaceRepositoryPort;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.IncidentCollaboration;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.IncidentLink;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.ManagedRunbook;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.RunbookVersion;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.SearchResult;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOperatorWorkspaceRepositoryAdapter implements OperatorWorkspaceRepositoryPort {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcOperatorWorkspaceRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SearchResult> search(String query, int limit) {
        String pattern = "%" + query.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        int perType = Math.max(5, Math.min(50, limit));
        List<SearchResult> results = new ArrayList<>();
        results.addAll(jdbc.query("""
                select id::text id, 'CLUSTER' type, name title, coalesce(description, '') description,
                       id cluster_id, name cluster_name, null namespace, null resource_kind, null resource_name,
                       status, '/clusters/' || id::text target_path, updated_at
                from clusters where lower(name) like ? or lower(coalesce(description, '')) like ?
                order by updated_at desc limit ?
                """, this::searchResult, pattern, pattern, perType));
        results.addAll(jdbc.query("""
                select distinct on (s.cluster_id, coalesce(s.namespace, ''), s.resource_type, s.resource_name)
                       s.id::text id, 'RESOURCE' type, s.resource_type || '/' || s.resource_name title,
                       coalesce(s.status, '') description, s.cluster_id, c.name cluster_name, s.namespace,
                       s.resource_type resource_kind, s.resource_name, s.status,
                       '/clusters/' || s.cluster_id::text || '?namespace=' || coalesce(s.namespace, '') target_path,
                       s.collected_at updated_at
                from kubernetes_resource_snapshots s join clusters c on c.id=s.cluster_id
                where lower(s.resource_name) like ? or lower(s.resource_type) like ?
                   or lower(coalesce(s.status, '')) like ?
                order by s.cluster_id, coalesce(s.namespace, ''), s.resource_type, s.resource_name, s.collected_at desc
                limit ?
                """, this::searchResult, pattern, pattern, pattern, perType));
        results.addAll(jdbc.query("""
                select i.id::text id, 'INCIDENT' type, i.title, coalesce(i.summary, '') description,
                       i.cluster_id, c.name cluster_name, i.namespace, i.resource_kind, i.resource_name,
                       i.state status, '/incidents/' || i.id::text target_path, i.last_detected_at updated_at
                from incidents i join clusters c on c.id=i.cluster_id
                where lower(i.title) like ? or lower(coalesce(i.summary, '')) like ?
                   or lower(coalesce(i.resource_name, '')) like ?
                order by i.last_detected_at desc limit ?
                """, this::searchResult, pattern, pattern, pattern, perType));
        results.addAll(jdbc.query("""
                select a.id::text id, 'ANALYSIS' type, 'AI Analysis' title, coalesce(a.result_summary, '') description,
                       a.cluster_id, c.name cluster_name, a.namespace, null resource_kind, null resource_name,
                       a.status, '/analysis?analysisId=' || a.id::text target_path, a.created_at updated_at
                from analysis_sessions a join clusters c on c.id=a.cluster_id
                where lower(coalesce(a.result_summary, '')) like ? or lower(coalesce(a.namespace, '')) like ?
                order by a.created_at desc limit ?
                """, this::searchResult, pattern, pattern, perType));
        results.addAll(jdbc.query("""
                select id, 'RUNBOOK' type, title, beginner_explanation description,
                       null::uuid cluster_id, null cluster_name, null namespace, resource_kind, null resource_name,
                       case when enabled then 'ENABLED' else 'DISABLED' end status,
                       '/runbooks?runbookId=' || id target_path, null::timestamptz updated_at
                from runbook_templates where lower(title) like ? or lower(signal) like ? or lower(category) like ?
                union all
                select id, 'RUNBOOK' type, title, beginner_explanation, null::uuid, null, null, resource_kind, null,
                       case when enabled then 'ENABLED' else 'DISABLED' end,
                       '/runbooks?runbookId=' || id, updated_at
                from custom_runbooks where lower(title) like ? or lower(signal) like ? or lower(category) like ?
                limit ?
                """, this::searchResult, pattern, pattern, pattern, pattern, pattern, pattern, perType));
        return results.stream()
                .sorted((left, right) -> compareNullable(right.updatedAt(), left.updatedAt()))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<KubernetesResourceSnapshot> findLatestResource(UUID clusterId, String namespace, String kind, String name) {
        return first(jdbc.query("""
                select * from kubernetes_resource_snapshots
                where cluster_id=? and coalesce(namespace, '')=coalesce(?, '')
                  and lower(resource_type)=lower(?) and resource_name=?
                order by collected_at desc limit 1
                """, this::resourceSnapshot, clusterId, namespace, kind, name));
    }

    @Override
    public List<KubernetesResourceSnapshot> findResourceHistory(UUID clusterId, String namespace, String kind, String name, int limit) {
        return jdbc.query("""
                select * from kubernetes_resource_snapshots
                where cluster_id=? and coalesce(namespace, '')=coalesce(?, '')
                  and lower(resource_type)=lower(?) and resource_name=?
                order by collected_at desc limit ?
                """, this::resourceSnapshot, clusterId, namespace, kind, name, Math.max(1, Math.min(limit, 20)));
    }

    @Override
    public List<KubernetesResourceSnapshot> findLatestNamespaceResources(UUID clusterId, String namespace, int limit) {
        return jdbc.query("""
                select distinct on (resource_type, resource_name) * from kubernetes_resource_snapshots
                where cluster_id=? and coalesce(namespace, '')=coalesce(?, '')
                order by resource_type, resource_name, collected_at desc limit ?
                """, this::resourceSnapshot, clusterId, namespace, limit);
    }

    @Override
    @Transactional
    public IncidentCollaboration saveCollaboration(IncidentCollaboration value) {
        int updated = jdbc.update("""
                update incident_collaboration set assignee=?, tags_json=?, acknowledge_due_at=?, resolve_due_at=?,
                    updated_by=?, updated_at=? where incident_id=?
                """, value.assignee(), json(value.tags()), timestamp(value.acknowledgeDueAt()),
                timestamp(value.resolveDueAt()), value.updatedBy(), timestamp(value.updatedAt()), value.incidentId());
        if (updated == 0) {
            jdbc.update("""
                    insert into incident_collaboration (incident_id, assignee, tags_json, acknowledge_due_at,
                        resolve_due_at, updated_by, updated_at) values (?, ?, ?, ?, ?, ?, ?)
                    """, value.incidentId(), value.assignee(), json(value.tags()), timestamp(value.acknowledgeDueAt()),
                    timestamp(value.resolveDueAt()), value.updatedBy(), timestamp(value.updatedAt()));
        }
        return findCollaboration(value.incidentId());
    }

    @Override
    public IncidentCollaboration findCollaboration(UUID incidentId) {
        List<IncidentCollaboration> rows = jdbc.query("select * from incident_collaboration where incident_id=?",
                (rs, rowNum) -> new IncidentCollaboration(uuid(rs, "incident_id"), rs.getString("assignee"),
                        strings(rs.getString("tags_json")), instant(rs, "acknowledge_due_at"),
                        instant(rs, "resolve_due_at"), rs.getString("updated_by"), instant(rs, "updated_at"), List.of()),
                incidentId);
        IncidentCollaboration base = rows.isEmpty()
                ? new IncidentCollaboration(incidentId, null, List.of(), null, null, null, null, List.of())
                : rows.get(0);
        return new IncidentCollaboration(base.incidentId(), base.assignee(), base.tags(), base.acknowledgeDueAt(),
                base.resolveDueAt(), base.updatedBy(), base.updatedAt(), findIncidentLinks(incidentId));
    }

    @Override
    public List<IncidentLink> findIncidentLinks(UUID incidentId) {
        return jdbc.query("""
                select l.*, i.title related_title, i.state related_state from incident_links l
                join incidents i on i.id=l.related_incident_id where l.incident_id=? order by l.created_at desc
                """, (rs, rowNum) -> new IncidentLink(uuid(rs, "incident_id"), uuid(rs, "related_incident_id"),
                        rs.getString("relation_type"), rs.getString("related_title"), rs.getString("related_state"),
                        rs.getString("created_by"), instant(rs, "created_at")), incidentId);
    }

    @Override
    public void saveIncidentLink(IncidentLink link) {
        jdbc.update("""
                insert into incident_links (incident_id, related_incident_id, relation_type, created_by, created_at)
                values (?, ?, ?, ?, ?) on conflict (incident_id, related_incident_id)
                do update set relation_type=excluded.relation_type, created_by=excluded.created_by, created_at=excluded.created_at
                """, link.incidentId(), link.relatedIncidentId(), link.relationType(), link.createdBy(), timestamp(link.createdAt()));
    }

    @Override
    public void deleteIncidentLink(UUID incidentId, UUID relatedIncidentId) {
        jdbc.update("delete from incident_links where (incident_id=? and related_incident_id=?) or (incident_id=? and related_incident_id=?)",
                incidentId, relatedIncidentId, relatedIncidentId, incidentId);
    }

    @Override
    public List<ManagedRunbook> findRunbookLibrary() {
        List<ManagedRunbook> result = new ArrayList<>(jdbc.query("""
                select id, 'SYSTEM' source_type, signal, category, resource_kind, title, beginner_explanation,
                       verification_command, expected_result, safe_action, validation_command, rollback_guidance,
                       safety_level, version, enabled, 'system' owner, null::timestamptz created_at, null::timestamptz updated_at
                from runbook_templates order by category, title
                """, this::managedRunbook));
        result.addAll(jdbc.query("""
                select id, 'CUSTOM' source_type, signal, category, resource_kind, title, beginner_explanation,
                       verification_command, expected_result, safe_action, validation_command, rollback_guidance,
                       safety_level, version, enabled, owner, created_at, updated_at
                from custom_runbooks order by updated_at desc
                """, this::managedRunbook));
        return result;
    }

    @Override
    public Optional<ManagedRunbook> findManagedRunbook(String id) {
        Optional<ManagedRunbook> custom = first(jdbc.query("""
                select id, 'CUSTOM' source_type, signal, category, resource_kind, title, beginner_explanation,
                       verification_command, expected_result, safe_action, validation_command, rollback_guidance,
                       safety_level, version, enabled, owner, created_at, updated_at from custom_runbooks where id=?
                """, this::managedRunbook, id));
        if (custom.isPresent()) return custom;
        return first(jdbc.query("""
                select id, 'SYSTEM' source_type, signal, category, resource_kind, title, beginner_explanation,
                       verification_command, expected_result, safe_action, validation_command, rollback_guidance,
                       safety_level, version, enabled, 'system' owner, null::timestamptz created_at, null::timestamptz updated_at
                from runbook_templates where id=?
                """, this::managedRunbook, id));
    }

    @Override
    @Transactional
    public ManagedRunbook saveCustomRunbook(ManagedRunbook value, String changeNote, String changedBy) {
        int updated = jdbc.update("""
                update custom_runbooks set signal=?, category=?, resource_kind=?, title=?, beginner_explanation=?,
                    verification_command=?, expected_result=?, safe_action=?, validation_command=?, rollback_guidance=?,
                    safety_level=?, version=?, enabled=?, owner=?, updated_at=? where id=?
                """, value.signal(), value.category(), value.resourceKind(), value.title(), value.beginnerExplanation(),
                value.verificationCommand(), value.expectedResult(), value.safeAction(), value.validationCommand(),
                value.rollbackGuidance(), value.safetyLevel(), value.version(), value.enabled(), value.owner(),
                timestamp(value.updatedAt()), value.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into custom_runbooks (id, signal, category, resource_kind, title, beginner_explanation,
                        verification_command, expected_result, safe_action, validation_command, rollback_guidance,
                        safety_level, version, enabled, owner, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, value.id(), value.signal(), value.category(), value.resourceKind(), value.title(),
                    value.beginnerExplanation(), value.verificationCommand(), value.expectedResult(), value.safeAction(),
                    value.validationCommand(), value.rollbackGuidance(), value.safetyLevel(), value.version(),
                    value.enabled(), value.owner(), timestamp(value.createdAt()), timestamp(value.updatedAt()));
        }
        jdbc.update("""
                insert into custom_runbook_versions (id, runbook_id, version, snapshot_json, change_note, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), value.id(), value.version(), json(value), changeNote, changedBy, timestamp(value.updatedAt()));
        return findManagedRunbook(value.id()).orElseThrow();
    }

    @Override
    public List<RunbookVersion> findRunbookVersions(String runbookId) {
        return jdbc.query("""
                select id, runbook_id, version, change_note, created_by, created_at
                from custom_runbook_versions where runbook_id=? order by version desc
                """, (rs, rowNum) -> new RunbookVersion(uuid(rs, "id"), rs.getString("runbook_id"),
                        rs.getInt("version"), rs.getString("change_note"), rs.getString("created_by"),
                        instant(rs, "created_at")), runbookId);
    }

    @Override
    public Optional<ManagedRunbook> findRunbookVersion(String runbookId, int version) {
        List<String> rows = jdbc.query("select snapshot_json from custom_runbook_versions where runbook_id=? and version=?",
                (rs, rowNum) -> rs.getString(1), runbookId, version);
        if (rows.isEmpty()) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(rows.get(0), ManagedRunbook.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored runbook version is invalid", exception);
        }
    }

    @Override
    public void deleteCustomRunbook(String runbookId) {
        jdbc.update("delete from custom_runbooks where id=?", runbookId);
    }

    @Override
    public List<Incident> findIncidentsForResource(UUID clusterId, String namespace, String kind, String name, int limit) {
        return jdbc.query("""
                select i.*, c.name cluster_name from incidents i join clusters c on c.id=i.cluster_id
                where i.cluster_id=? and coalesce(i.namespace, '')=coalesce(?, '')
                  and lower(coalesce(i.resource_kind, ''))=lower(?) and i.resource_name=?
                order by i.last_detected_at desc limit ?
                """, this::incident, clusterId, namespace, kind, name, limit);
    }

    private SearchResult searchResult(ResultSet rs, int rowNum) throws SQLException {
        return new SearchResult(rs.getString("id"), rs.getString("type"), rs.getString("title"),
                rs.getString("description"), uuid(rs, "cluster_id"), rs.getString("cluster_name"),
                rs.getString("namespace"), rs.getString("resource_kind"), rs.getString("resource_name"),
                rs.getString("status"), rs.getString("target_path"), instant(rs, "updated_at"));
    }

    private KubernetesResourceSnapshot resourceSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new KubernetesResourceSnapshot(uuid(rs, "id"), uuid(rs, "cluster_id"), uuid(rs, "sync_job_id"),
                rs.getString("namespace"), rs.getString("resource_type"), rs.getString("resource_name"),
                rs.getString("resource_uid"), rs.getString("status"), rs.getString("summary_json"),
                rs.getString("raw_json"), rs.getBoolean("truncated"), instant(rs, "collected_at"));
    }

    private ManagedRunbook managedRunbook(ResultSet rs, int rowNum) throws SQLException {
        return new ManagedRunbook(rs.getString("id"), rs.getString("source_type"), rs.getString("signal"),
                rs.getString("category"), rs.getString("resource_kind"), rs.getString("title"),
                rs.getString("beginner_explanation"), rs.getString("verification_command"),
                rs.getString("expected_result"), rs.getString("safe_action"), rs.getString("validation_command"),
                rs.getString("rollback_guidance"), rs.getString("safety_level"), rs.getInt("version"),
                rs.getBoolean("enabled"), rs.getString("owner"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private Incident incident(ResultSet rs, int rowNum) throws SQLException {
        return new Incident(uuid(rs, "id"), rs.getString("fingerprint"), uuid(rs, "cluster_id"),
                rs.getString("cluster_name"), rs.getString("namespace"), rs.getString("resource_kind"),
                rs.getString("resource_name"), rs.getString("category"), rs.getString("severity"),
                IncidentState.valueOf(rs.getString("state")), rs.getString("title"), rs.getString("summary"),
                rs.getString("next_action"), rs.getInt("occurrence_count"), rs.getInt("reopen_count"),
                uuid(rs, "source_analysis_id"), instant(rs, "first_detected_at"), instant(rs, "last_detected_at"),
                rs.getString("updated_by"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Value cannot be serialized", exception);
        }
    }

    private List<String> strings(String value) {
        try {
            return value == null ? List.of() : objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private static UUID uuid(ResultSet rs, String name) throws SQLException {
        Object value = rs.getObject(name);
        return value == null ? null : value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private static int compareNullable(Instant left, Instant right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        return left.compareTo(right);
    }
}
