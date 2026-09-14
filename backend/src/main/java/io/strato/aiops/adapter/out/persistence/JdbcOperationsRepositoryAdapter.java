package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.operations.OperationsModels.AnalysisFeedback;
import io.strato.aiops.domain.operations.OperationsModels.CleanupPreview;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentEvidence;
import io.strato.aiops.domain.operations.OperationsModels.IncidentRecovery;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operations.OperationsModels.Notification;
import io.strato.aiops.domain.operations.OperationsModels.OperationSettings;
import io.strato.aiops.domain.operations.OperationsModels.PolicyDefinition;
import io.strato.aiops.domain.operations.OperationsModels.PolicyEvaluation;
import io.strato.aiops.domain.operations.OperationsModels.PolicyResult;
import io.strato.aiops.domain.operations.OperationsModels.ResourceBaseline;
import io.strato.aiops.domain.operations.OperationsModels.ResourceChange;
import io.strato.aiops.domain.operations.OperationsModels.RunbookTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JdbcOperationsRepositoryAdapter implements OperationsRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOperationsRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Incident> findIncidents(UUID clusterId, String namespace, String state, String severity, int limit) {
        StringBuilder sql = new StringBuilder("""
                select i.*, c.name cluster_name from incidents i
                join clusters c on c.id = i.cluster_id where 1=1
                """);
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, "i.cluster_id", clusterId);
        appendFilter(sql, args, "i.namespace", namespace);
        appendFilter(sql, args, "i.state", state);
        appendFilter(sql, args, "i.severity", severity);
        sql.append(" order by case i.severity when 'CRITICAL' then 4 when 'HIGH' then 3 when 'MEDIUM' then 2 else 1 end desc, i.last_detected_at desc limit ?");
        args.add(safeLimit(limit));
        return jdbcTemplate.query(sql.toString(), incidentMapper(), args.toArray());
    }

    @Override
    public Optional<Incident> findIncidentById(UUID incidentId) {
        return first(jdbcTemplate.query("""
                select i.*, c.name cluster_name from incidents i
                join clusters c on c.id = i.cluster_id where i.id = ?
                """, incidentMapper(), incidentId));
    }

    @Override
    public Optional<Incident> findIncidentByFingerprint(String fingerprint) {
        return first(jdbcTemplate.query("""
                select i.*, c.name cluster_name from incidents i
                join clusters c on c.id = i.cluster_id where i.fingerprint = ?
                """, incidentMapper(), fingerprint));
    }

    @Override
    public Incident saveIncident(Incident incident) {
        int updated = jdbcTemplate.update("""
                update incidents set namespace=?, resource_kind=?, resource_name=?, category=?, severity=?, state=?,
                title=?, summary=?, next_action=?, occurrence_count=?, reopen_count=?, source_analysis_id=?,
                first_detected_at=?, last_detected_at=?, updated_by=? where id=?
                """, incident.namespace(), incident.resourceKind(), incident.resourceName(), incident.category(),
                incident.severity(), incident.state().name(), incident.title(), incident.summary(), incident.nextAction(),
                incident.occurrenceCount(), incident.reopenCount(), incident.sourceAnalysisId(),
                timestamp(incident.firstDetectedAt()), timestamp(incident.lastDetectedAt()), incident.updatedBy(), incident.id());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into incidents (id, fingerprint, cluster_id, namespace, resource_kind, resource_name,
                    category, severity, state, title, summary, next_action, occurrence_count, reopen_count,
                    source_analysis_id, first_detected_at, last_detected_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, incident.id(), incident.fingerprint(), incident.clusterId(), incident.namespace(),
                    incident.resourceKind(), incident.resourceName(), incident.category(), incident.severity(),
                    incident.state().name(), incident.title(), incident.summary(), incident.nextAction(),
                    incident.occurrenceCount(), incident.reopenCount(), incident.sourceAnalysisId(),
                    timestamp(incident.firstDetectedAt()), timestamp(incident.lastDetectedAt()), incident.updatedBy());
        }
        return findIncidentById(incident.id()).orElseThrow();
    }

    @Override
    public void saveIncidentEvidence(IncidentEvidence evidence) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from incident_evidence where incident_id=? and evidence_key=?",
                Integer.class, evidence.incidentId(), evidence.evidenceKey());
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                insert into incident_evidence (id, incident_id, evidence_key, evidence_type, source_ref, summary, factual, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, evidence.id(), evidence.incidentId(), evidence.evidenceKey(), evidence.evidenceType(),
                evidence.sourceRef(), evidence.summary(), evidence.factual(), timestamp(evidence.occurredAt()));
    }

    @Override
    public List<IncidentEvidence> findIncidentEvidence(UUID incidentId) {
        return jdbcTemplate.query("select * from incident_evidence where incident_id=? order by occurred_at desc",
                (rs, rowNum) -> new IncidentEvidence(uuid(rs, "id"), uuid(rs, "incident_id"),
                        rs.getString("evidence_key"), rs.getString("evidence_type"), rs.getString("source_ref"),
                        rs.getString("summary"), rs.getBoolean("factual"), instant(rs, "occurred_at")), incidentId);
    }

    @Override
    public void saveIncidentActivity(IncidentActivity activity) {
        jdbcTemplate.update("""
                insert into incident_activities (id, incident_id, activity_type, from_state, to_state, note, actor, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, activity.id(), activity.incidentId(), activity.activityType(), enumName(activity.fromState()),
                enumName(activity.toState()), activity.note(), activity.actor(), timestamp(activity.createdAt()));
    }

    @Override
    public List<IncidentActivity> findIncidentActivities(UUID incidentId) {
        return jdbcTemplate.query("select * from incident_activities where incident_id=? order by created_at desc",
                incidentActivityMapper(), incidentId);
    }

    @Override
    public List<IncidentActivity> findIncidentActivitiesByIncidentIds(Set<UUID> incidentIds) {
        if (incidentIds == null || incidentIds.isEmpty()) {
            return List.of();
        }
        String placeholders = incidentIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        return jdbcTemplate.query("select * from incident_activities where incident_id in (" + placeholders
                        + ") order by created_at desc", incidentActivityMapper(), incidentIds.toArray());
    }

    @Override
    public Optional<IncidentRecovery> findIncidentRecovery(UUID incidentId) {
        return first(jdbcTemplate.query("select * from incident_recovery_observations where incident_id=?",
                (rs, rowNum) -> new IncidentRecovery(uuid(rs, "incident_id"),
                        rs.getInt("consecutive_healthy_count"), 2,
                        instant(rs, "first_healthy_at"), instant(rs, "last_observed_at"),
                        rs.getString("last_observed_status"), true), incidentId));
    }

    @Override
    public IncidentRecovery saveIncidentRecovery(IncidentRecovery recovery) {
        int updated = jdbcTemplate.update("""
                update incident_recovery_observations set consecutive_healthy_count=?, first_healthy_at=?,
                last_observed_at=?, last_observed_status=? where incident_id=?
                """, recovery.consecutiveHealthyCount(), timestamp(recovery.firstHealthyAt()),
                timestamp(recovery.lastObservedAt()), recovery.lastObservedStatus(), recovery.incidentId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into incident_recovery_observations
                    (incident_id, consecutive_healthy_count, first_healthy_at, last_observed_at, last_observed_status)
                    values (?, ?, ?, ?, ?)
                    """, recovery.incidentId(), recovery.consecutiveHealthyCount(),
                    timestamp(recovery.firstHealthyAt()), timestamp(recovery.lastObservedAt()),
                    recovery.lastObservedStatus());
        }
        return findIncidentRecovery(recovery.incidentId()).orElseThrow();
    }

    @Override
    public Notification saveNotification(Notification notification) {
        int updated = jdbcTemplate.update("""
                update operation_notifications set notification_type=?, severity=?, title=?, message=?, target_path=?,
                is_read=?, occurrence_count=?, updated_at=? where dedup_key=?
                """, notification.notificationType(), notification.severity(), notification.title(),
                notification.message(), notification.targetPath(), notification.read(), notification.occurrenceCount(),
                timestamp(notification.updatedAt()), notification.dedupKey());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into operation_notifications (id, dedup_key, notification_type, severity, title, message,
                    target_path, is_read, occurrence_count, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, notification.id(), notification.dedupKey(), notification.notificationType(),
                    notification.severity(), notification.title(), notification.message(), notification.targetPath(),
                    notification.read(), notification.occurrenceCount(), timestamp(notification.createdAt()),
                    timestamp(notification.updatedAt()));
        }
        return findNotificationByDedupKey(notification.dedupKey()).orElseThrow();
    }

    @Override
    public Optional<Notification> findNotificationByDedupKey(String dedupKey) {
        return first(jdbcTemplate.query("select * from operation_notifications where dedup_key=?",
                notificationMapper(), dedupKey));
    }

    @Override
    public List<Notification> findNotifications(boolean unreadOnly, int limit) {
        String sql = "select * from operation_notifications" + (unreadOnly ? " where is_read=false" : "")
                + " order by updated_at desc limit ?";
        return jdbcTemplate.query(sql, notificationMapper(), safeLimit(limit));
    }

    @Override
    public long countUnreadNotifications() {
        Long count = jdbcTemplate.queryForObject("select count(*) from operation_notifications where is_read=false", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public void markNotificationRead(UUID notificationId) {
        jdbcTemplate.update("update operation_notifications set is_read=true, updated_at=? where id=?",
                timestamp(Instant.now()), notificationId);
    }

    @Override
    public void markAllNotificationsRead() {
        jdbcTemplate.update("update operation_notifications set is_read=true, updated_at=? where is_read=false",
                timestamp(Instant.now()));
    }

    @Override
    public List<PolicyDefinition> findPolicyDefinitions() {
        return jdbcTemplate.query("select * from policy_definitions order by category, name", policyDefinitionMapper());
    }

    @Override
    public Optional<PolicyDefinition> findPolicyDefinition(String policyId) {
        return first(jdbcTemplate.query("select * from policy_definitions where id=?", policyDefinitionMapper(), policyId));
    }

    @Override
    public PolicyDefinition savePolicyDefinition(PolicyDefinition definition) {
        jdbcTemplate.update("""
                update policy_definitions set name=?, description=?, category=?, severity=?, enabled=? where id=?
                """, definition.name(), definition.description(), definition.category(), definition.severity(),
                definition.enabled(), definition.id());
        return findPolicyDefinition(definition.id()).orElseThrow();
    }

    @Override
    @Transactional
    public void replacePolicyEvaluations(UUID clusterId, List<PolicyEvaluation> evaluations) {
        jdbcTemplate.update("delete from policy_evaluations where cluster_id=?", clusterId);
        jdbcTemplate.batchUpdate("""
                insert into policy_evaluations (id, policy_id, cluster_id, namespace, resource_kind, resource_name,
                result, evidence, recommendation, evaluated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, evaluations, 100, (ps, evaluation) -> {
            ps.setObject(1, evaluation.id());
            ps.setString(2, evaluation.policyId());
            ps.setObject(3, evaluation.clusterId());
            ps.setString(4, evaluation.namespace());
            ps.setString(5, evaluation.resourceKind());
            ps.setString(6, evaluation.resourceName());
            ps.setString(7, evaluation.result().name());
            ps.setString(8, evaluation.evidence());
            ps.setString(9, evaluation.recommendation());
            ps.setTimestamp(10, timestamp(evaluation.evaluatedAt()));
        });
    }

    @Override
    public List<PolicyEvaluation> findPolicyEvaluations(UUID clusterId, String namespace, String result, int limit) {
        StringBuilder sql = new StringBuilder("""
                select e.*, c.name cluster_name from policy_evaluations e
                join clusters c on c.id=e.cluster_id where 1=1
                """);
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, "e.cluster_id", clusterId);
        appendFilter(sql, args, "e.namespace", namespace);
        appendFilter(sql, args, "e.result", result);
        sql.append(" order by case e.result when 'FAIL' then 3 when 'WARN' then 2 when 'NOT_APPLICABLE' then 1 else 0 end desc, e.evaluated_at desc limit ?");
        args.add(safeLimit(limit));
        return jdbcTemplate.query(sql.toString(), policyEvaluationMapper(), args.toArray());
    }

    @Override
    public List<ResourceBaseline> findResourceBaselines(UUID clusterId) {
        return jdbcTemplate.query("select * from resource_baselines where cluster_id=?", baselineMapper(), clusterId);
    }

    @Override
    public void saveResourceBaseline(ResourceBaseline baseline) {
        int updated = jdbcTemplate.update("""
                update resource_baselines set status=?, summary_hash=?, summary_json=?, collected_at=?
                where cluster_id=? and namespace_key=? and resource_kind=? and resource_name=?
                """, baseline.status(), baseline.summaryHash(), baseline.summaryJson(), timestamp(baseline.collectedAt()),
                baseline.clusterId(), namespaceKey(baseline.namespace()), baseline.resourceKind(), baseline.resourceName());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into resource_baselines (id, cluster_id, namespace_key, resource_kind, resource_name, status,
                    summary_hash, summary_json, collected_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, baseline.id(), baseline.clusterId(), namespaceKey(baseline.namespace()), baseline.resourceKind(),
                    baseline.resourceName(), baseline.status(), baseline.summaryHash(), baseline.summaryJson(),
                    timestamp(baseline.collectedAt()));
        }
    }

    @Override
    public void deleteResourceBaseline(UUID baselineId) {
        jdbcTemplate.update("delete from resource_baselines where id=?", baselineId);
    }

    @Override
    public void saveResourceChange(ResourceChange change) {
        jdbcTemplate.update("""
                insert into resource_change_events (id, cluster_id, namespace, resource_kind, resource_name, change_type,
                previous_status, current_status, previous_hash, current_hash, summary, detected_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, change.id(), change.clusterId(), change.namespace(), change.resourceKind(), change.resourceName(),
                change.changeType(), change.previousStatus(), change.currentStatus(), change.previousHash(),
                change.currentHash(), change.summary(), timestamp(change.detectedAt()));
    }

    @Override
    public List<ResourceChange> findResourceChanges(UUID clusterId, String namespace, int limit) {
        StringBuilder sql = new StringBuilder("""
                select e.*, c.name cluster_name from resource_change_events e
                join clusters c on c.id=e.cluster_id where 1=1
                """);
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, "e.cluster_id", clusterId);
        appendFilter(sql, args, "e.namespace", namespace);
        sql.append(" order by e.detected_at desc limit ?");
        args.add(safeLimit(limit));
        return jdbcTemplate.query(sql.toString(), changeMapper(), args.toArray());
    }

    @Override
    public Optional<ResourceChange> findResourceChangeById(UUID changeId) {
        return first(jdbcTemplate.query("""
                select e.*, c.name cluster_name from resource_change_events e
                join clusters c on c.id=e.cluster_id where e.id=?
                """, changeMapper(), changeId));
    }

    @Override
    public List<RunbookTemplate> findRunbooks(String signal, String category) {
        StringBuilder sql = new StringBuilder("""
                select * from (
                    select id, signal, category, resource_kind, title, beginner_explanation, verification_command,
                           expected_result, safe_action, validation_command, rollback_guidance, safety_level, version, enabled
                    from runbook_templates
                    union all
                    select id, signal, category, resource_kind, title, beginner_explanation, verification_command,
                           expected_result, safe_action, validation_command, rollback_guidance, safety_level, version, enabled
                    from custom_runbooks
                ) runbook where enabled=true
                """);
        List<Object> args = new ArrayList<>();
        if (signal != null && !signal.isBlank()) {
            sql.append(" and lower(signal)=lower(?)");
            args.add(signal);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" and lower(category)=lower(?)");
            args.add(category);
        }
        sql.append(" order by category, title");
        return jdbcTemplate.query(sql.toString(), runbookMapper(), args.toArray());
    }

    @Override
    public Optional<RunbookTemplate> findRunbookById(String runbookId) {
        return first(jdbcTemplate.query("""
                select * from (
                    select id, signal, category, resource_kind, title, beginner_explanation, verification_command,
                           expected_result, safe_action, validation_command, rollback_guidance, safety_level, version, enabled
                    from runbook_templates
                    union all
                    select id, signal, category, resource_kind, title, beginner_explanation, verification_command,
                           expected_result, safe_action, validation_command, rollback_guidance, safety_level, version, enabled
                    from custom_runbooks
                ) runbook where id=?
                """, runbookMapper(), runbookId));
    }

    @Override
    public Optional<AnalysisFeedback> findAnalysisFeedback(UUID analysisId) {
        return first(jdbcTemplate.query("select * from analysis_feedback where analysis_id=?", feedbackMapper(), analysisId));
    }

    @Override
    public AnalysisFeedback saveAnalysisFeedback(AnalysisFeedback feedback) {
        int updated = jdbcTemplate.update("""
                update analysis_feedback set accuracy=?, outcome=?, dangerous_suggestion=?, comment=?,
                actual_root_cause=?, actual_resolution=?, validated_resource_kind=?, validated_resource_name=?,
                confidence_expectation=?, submitted_by=?, updated_at=?
                where analysis_id=?
                """, feedback.accuracy(), feedback.outcome(), feedback.dangerousSuggestion(), feedback.comment(),
                feedback.actualRootCause(), feedback.actualResolution(), feedback.validatedResourceKind(),
                feedback.validatedResourceName(), feedback.confidenceExpectation(), feedback.submittedBy(),
                timestamp(feedback.updatedAt()), feedback.analysisId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into analysis_feedback (analysis_id, accuracy, outcome, dangerous_suggestion, comment,
                    actual_root_cause, actual_resolution, validated_resource_kind, validated_resource_name,
                    confidence_expectation, submitted_by, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, feedback.analysisId(), feedback.accuracy(), feedback.outcome(), feedback.dangerousSuggestion(),
                    feedback.comment(), feedback.actualRootCause(), feedback.actualResolution(),
                    feedback.validatedResourceKind(), feedback.validatedResourceName(), feedback.confidenceExpectation(),
                    feedback.submittedBy(), timestamp(feedback.updatedAt()));
        }
        return findAnalysisFeedback(feedback.analysisId()).orElseThrow();
    }

    @Override
    public List<AnalysisFeedback> findAnalysisFeedback(int limit) {
        return jdbcTemplate.query("select * from analysis_feedback order by updated_at desc limit ?", feedbackMapper(),
                safeLimit(limit));
    }

    @Override
    public OperationSettings getOperationSettings() {
        return jdbcTemplate.queryForObject("select * from operation_settings where id=1", settingsMapper());
    }

    @Override
    public OperationSettings saveOperationSettings(OperationSettings settings) {
        jdbcTemplate.update("""
                update operation_settings set event_retention_days=?, analysis_retention_days=?, job_retention_days=?,
                notification_retention_days=?, resolved_incident_retention_days=?, change_retention_days=?,
                audit_retention_days=?, command_retention_days=?,
                notification_suppress_minutes=?, stale_sync_minutes=?, long_running_job_seconds=?, updated_by=?, updated_at=?
                where id=1
                """, settings.eventRetentionDays(), settings.analysisRetentionDays(), settings.jobRetentionDays(),
                settings.notificationRetentionDays(), settings.resolvedIncidentRetentionDays(), settings.changeRetentionDays(),
                settings.auditRetentionDays(), settings.commandRetentionDays(),
                settings.notificationSuppressMinutes(), settings.staleSyncMinutes(), settings.longRunningJobSeconds(),
                settings.updatedBy(), timestamp(settings.updatedAt()));
        return getOperationSettings();
    }

    @Override
    public List<AuditLog> findAuditLogs(String actor, String action, String targetType, String targetId,
                                        String requestId, Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder("select * from audit_logs where 1=1");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, "actor", actor);
        appendFilter(sql, args, "action", action);
        appendFilter(sql, args, "target_type", targetType);
        appendFilter(sql, args, "target_id", targetId);
        appendFilter(sql, args, "request_id", requestId);
        if (from != null) {
            sql.append(" and created_at>=?");
            args.add(timestamp(from));
        }
        if (to != null) {
            sql.append(" and created_at<=?");
            args.add(timestamp(to));
        }
        sql.append(" order by created_at desc limit ?");
        args.add(safeLimit(limit));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AuditLog(uuid(rs, "id"),
                rs.getString("action"), rs.getString("target_type"), rs.getString("target_id"),
                rs.getString("actor"), rs.getString("request_id"), instant(rs, "created_at")), args.toArray());
    }

    @Override
    @Transactional
    public CleanupPreview cleanup(OperationSettings settings, boolean execute) {
        Instant now = Instant.now();
        Instant eventCutoff = now.minus(settings.eventRetentionDays(), ChronoUnit.DAYS);
        Instant jobCutoff = now.minus(settings.jobRetentionDays(), ChronoUnit.DAYS);
        Instant notificationCutoff = now.minus(settings.notificationRetentionDays(), ChronoUnit.DAYS);
        Instant changeCutoff = now.minus(settings.changeRetentionDays(), ChronoUnit.DAYS);
        Instant incidentCutoff = now.minus(settings.resolvedIncidentRetentionDays(), ChronoUnit.DAYS);
        Instant analysisCutoff = now.minus(settings.analysisRetentionDays(), ChronoUnit.DAYS);
        Instant auditCutoff = now.minus(settings.auditRetentionDays(), ChronoUnit.DAYS);
        Instant commandCutoff = now.minus(settings.commandRetentionDays(), ChronoUnit.DAYS);
        String oldSyncJobPredicate = """
                coalesce(completed_at, created_at) < ?
                and created_at < (select max(latest.created_at) from sync_jobs latest where latest.cluster_id=sync_jobs.cluster_id)
                """;
        long eventSnapshots = count("select count(*) from kubernetes_event_snapshots where collected_at<?", eventCutoff);
        List<UUID> eligibleJobIds = jdbcTemplate.queryForList("""
                select j.id from async_jobs j
                where j.created_at < ?
                  and j.status not in ('PENDING', 'RUNNING')
                  and not exists (
                    select 1 from analysis_sessions a
                    where a.async_job_id=j.id and a.created_at >= ?
                  )
                  and not exists (
                    select 1 from sync_jobs s
                    where s.async_job_id=j.id
                      and (coalesce(s.completed_at, s.created_at) >= ?
                        or s.created_at >= (select max(latest.created_at) from sync_jobs latest where latest.cluster_id=s.cluster_id))
                  )
                """, UUID.class, timestamp(jobCutoff), timestamp(analysisCutoff), timestamp(jobCutoff));
        long notifications = count("select count(*) from operation_notifications where updated_at<?", notificationCutoff);
        long changes = count("select count(*) from resource_change_events where detected_at<?", changeCutoff);
        long incidents = count("select count(*) from incidents where state='RESOLVED' and last_detected_at<?", incidentCutoff);
        long evaluations = count("select count(*) from policy_evaluations where evaluated_at<?", changeCutoff);
        long analyses = count("select count(*) from analysis_sessions where created_at<?", analysisCutoff);
        long watchSignals = count("select count(*) from kubernetes_watch_signals where observed_at<?", eventCutoff);
        long regressionRuns = count("""
                select count(*) from analysis_regression_runs where started_at<?
                and started_at < (select max(latest.started_at) from analysis_regression_runs latest)
                """, analysisCutoff);
        long auditLogs = count("select count(*) from audit_logs where created_at<?", auditCutoff);
        long commandExecutions = count("""
                select count(*) from command_executions
                where created_at<? and status not in ('QUEUED', 'RUNNING')
                """, commandCutoff);
        if (execute) {
            jdbcTemplate.update("delete from kubernetes_event_snapshots where collected_at<?", timestamp(eventCutoff));
            jdbcTemplate.update("delete from operation_notifications where updated_at<?", timestamp(notificationCutoff));
            jdbcTemplate.update("delete from resource_change_events where detected_at<?", timestamp(changeCutoff));
            jdbcTemplate.update("delete from incidents where state='RESOLVED' and last_detected_at<?", timestamp(incidentCutoff));
            jdbcTemplate.update("delete from policy_evaluations where evaluated_at<?", timestamp(changeCutoff));
            jdbcTemplate.update("delete from analysis_sessions where created_at<?", timestamp(analysisCutoff));
            jdbcTemplate.update("delete from kubernetes_watch_signals where observed_at<?", timestamp(eventCutoff));
            jdbcTemplate.update("""
                    delete from analysis_regression_runs where started_at<?
                    and started_at < (select max(latest.started_at) from analysis_regression_runs latest)
                    """, timestamp(analysisCutoff));
            jdbcTemplate.update("delete from kubernetes_resource_snapshots where sync_job_id in "
                    + "(select id from sync_jobs where " + oldSyncJobPredicate + ")", timestamp(jobCutoff));
            jdbcTemplate.update("delete from kubernetes_event_snapshots where sync_job_id in "
                    + "(select id from sync_jobs where " + oldSyncJobPredicate + ")", timestamp(jobCutoff));
            jdbcTemplate.update("delete from sync_jobs where " + oldSyncJobPredicate, timestamp(jobCutoff));
            eligibleJobIds.forEach(jobId -> jdbcTemplate.update("delete from async_jobs where id=?", jobId));
            jdbcTemplate.update("delete from command_execution_rate_events where created_at<?", timestamp(commandCutoff));
            jdbcTemplate.update("delete from command_executions where created_at<? and status not in ('QUEUED', 'RUNNING')",
                    timestamp(commandCutoff));
            jdbcTemplate.update("delete from audit_logs where created_at<?", timestamp(auditCutoff));
        }
        return new CleanupPreview(eventSnapshots, eligibleJobIds.size(), notifications, changes, incidents,
                evaluations, analyses, watchSignals, regressionRuns, auditLogs, commandExecutions, execute);
    }

    private long count(String sql, Instant cutoff) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, timestamp(cutoff));
        return count == null ? 0 : count;
    }

    private RowMapper<Incident> incidentMapper() {
        return (rs, rowNum) -> new Incident(uuid(rs, "id"), rs.getString("fingerprint"), uuid(rs, "cluster_id"),
                rs.getString("cluster_name"), rs.getString("namespace"), rs.getString("resource_kind"),
                rs.getString("resource_name"), rs.getString("category"), rs.getString("severity"),
                IncidentState.valueOf(rs.getString("state")), rs.getString("title"), rs.getString("summary"),
                rs.getString("next_action"), rs.getInt("occurrence_count"), rs.getInt("reopen_count"),
                nullableUuid(rs, "source_analysis_id"), instant(rs, "first_detected_at"),
                instant(rs, "last_detected_at"), rs.getString("updated_by"));
    }

    private RowMapper<IncidentActivity> incidentActivityMapper() {
        return (rs, rowNum) -> new IncidentActivity(uuid(rs, "id"), uuid(rs, "incident_id"),
                rs.getString("activity_type"), incidentState(rs.getString("from_state")),
                incidentState(rs.getString("to_state")), rs.getString("note"), rs.getString("actor"),
                instant(rs, "created_at"));
    }

    private RowMapper<Notification> notificationMapper() {
        return (rs, rowNum) -> new Notification(uuid(rs, "id"), rs.getString("dedup_key"),
                rs.getString("notification_type"), rs.getString("severity"), rs.getString("title"),
                rs.getString("message"), rs.getString("target_path"), rs.getBoolean("is_read"),
                rs.getInt("occurrence_count"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private RowMapper<PolicyDefinition> policyDefinitionMapper() {
        return (rs, rowNum) -> new PolicyDefinition(rs.getString("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("category"), rs.getString("severity"),
                rs.getBoolean("enabled"));
    }

    private RowMapper<PolicyEvaluation> policyEvaluationMapper() {
        return (rs, rowNum) -> new PolicyEvaluation(uuid(rs, "id"), rs.getString("policy_id"),
                uuid(rs, "cluster_id"), rs.getString("cluster_name"), rs.getString("namespace"),
                rs.getString("resource_kind"), rs.getString("resource_name"),
                PolicyResult.valueOf(rs.getString("result")), rs.getString("evidence"),
                rs.getString("recommendation"), instant(rs, "evaluated_at"));
    }

    private RowMapper<ResourceBaseline> baselineMapper() {
        return (rs, rowNum) -> new ResourceBaseline(uuid(rs, "id"), uuid(rs, "cluster_id"),
                emptyToNull(rs.getString("namespace_key")), rs.getString("resource_kind"),
                rs.getString("resource_name"), rs.getString("status"), rs.getString("summary_hash"),
                rs.getString("summary_json"), instant(rs, "collected_at"));
    }

    private RowMapper<ResourceChange> changeMapper() {
        return (rs, rowNum) -> new ResourceChange(uuid(rs, "id"), uuid(rs, "cluster_id"),
                rs.getString("cluster_name"), rs.getString("namespace"), rs.getString("resource_kind"),
                rs.getString("resource_name"), rs.getString("change_type"), rs.getString("previous_status"),
                rs.getString("current_status"), rs.getString("previous_hash"), rs.getString("current_hash"),
                rs.getString("summary"), instant(rs, "detected_at"));
    }

    private RowMapper<RunbookTemplate> runbookMapper() {
        return (rs, rowNum) -> new RunbookTemplate(rs.getString("id"), rs.getString("signal"),
                rs.getString("category"), rs.getString("resource_kind"), rs.getString("title"),
                rs.getString("beginner_explanation"), rs.getString("verification_command"),
                rs.getString("expected_result"), rs.getString("safe_action"),
                rs.getString("validation_command"), rs.getString("rollback_guidance"),
                rs.getString("safety_level"), rs.getInt("version"), rs.getBoolean("enabled"));
    }

    private RowMapper<AnalysisFeedback> feedbackMapper() {
        return (rs, rowNum) -> new AnalysisFeedback(uuid(rs, "analysis_id"), rs.getString("accuracy"),
                rs.getString("outcome"), rs.getBoolean("dangerous_suggestion"), rs.getString("comment"),
                rs.getString("actual_root_cause"), rs.getString("actual_resolution"),
                rs.getString("validated_resource_kind"), rs.getString("validated_resource_name"),
                rs.getString("confidence_expectation"), rs.getString("submitted_by"), instant(rs, "updated_at"));
    }

    private RowMapper<OperationSettings> settingsMapper() {
        return (rs, rowNum) -> new OperationSettings(rs.getInt("event_retention_days"),
                rs.getInt("analysis_retention_days"), rs.getInt("job_retention_days"),
                rs.getInt("notification_retention_days"), rs.getInt("resolved_incident_retention_days"),
                rs.getInt("change_retention_days"), rs.getInt("audit_retention_days"),
                rs.getInt("command_retention_days"), rs.getInt("notification_suppress_minutes"),
                rs.getInt("stale_sync_minutes"), rs.getInt("long_running_job_seconds"),
                rs.getString("updated_by"), instant(rs, "updated_at"));
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String column, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            return;
        }
        sql.append(" and ").append(column).append("=?");
        args.add(value);
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
    }

    private <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column) == null ? null : rs.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private IncidentState incidentState(String value) {
        return value == null ? null : IncidentState.valueOf(value);
    }

    private String namespaceKey(String namespace) {
        return namespace == null ? "" : namespace;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
