package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.operations.OperationsModels.AnalysisFeedback;
import io.strato.aiops.domain.operations.OperationsModels.CleanupPreview;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentEvidence;
import io.strato.aiops.domain.operations.OperationsModels.IncidentRecovery;
import io.strato.aiops.domain.operations.OperationsModels.Notification;
import io.strato.aiops.domain.operations.OperationsModels.OperationSettings;
import io.strato.aiops.domain.operations.OperationsModels.PolicyDefinition;
import io.strato.aiops.domain.operations.OperationsModels.PolicyEvaluation;
import io.strato.aiops.domain.operations.OperationsModels.ResourceBaseline;
import io.strato.aiops.domain.operations.OperationsModels.ResourceChange;
import io.strato.aiops.domain.operations.OperationsModels.RunbookTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OperationsRepositoryPort {

    List<Incident> findIncidents(UUID clusterId, String namespace, String state, String severity, int limit);

    Optional<Incident> findIncidentById(UUID incidentId);

    Optional<Incident> findIncidentByFingerprint(String fingerprint);

    Incident saveIncident(Incident incident);

    void saveIncidentEvidence(IncidentEvidence evidence);

    List<IncidentEvidence> findIncidentEvidence(UUID incidentId);

    void saveIncidentActivity(IncidentActivity activity);

    List<IncidentActivity> findIncidentActivities(UUID incidentId);

    List<IncidentActivity> findIncidentActivitiesByIncidentIds(Set<UUID> incidentIds);

    Optional<IncidentRecovery> findIncidentRecovery(UUID incidentId);

    IncidentRecovery saveIncidentRecovery(IncidentRecovery recovery);

    Notification saveNotification(Notification notification);

    Optional<Notification> findNotificationByDedupKey(String dedupKey);

    List<Notification> findNotifications(boolean unreadOnly, int limit);

    long countUnreadNotifications();

    void markNotificationRead(UUID notificationId);

    void markAllNotificationsRead();

    List<PolicyDefinition> findPolicyDefinitions();

    Optional<PolicyDefinition> findPolicyDefinition(String policyId);

    PolicyDefinition savePolicyDefinition(PolicyDefinition definition);

    void replacePolicyEvaluations(UUID clusterId, List<PolicyEvaluation> evaluations);

    List<PolicyEvaluation> findPolicyEvaluations(UUID clusterId, String namespace, String result, int limit);

    List<ResourceBaseline> findResourceBaselines(UUID clusterId);

    void saveResourceBaseline(ResourceBaseline baseline);

    void deleteResourceBaseline(UUID baselineId);

    void saveResourceChange(ResourceChange change);

    List<ResourceChange> findResourceChanges(UUID clusterId, String namespace, int limit);

    Optional<ResourceChange> findResourceChangeById(UUID changeId);

    List<RunbookTemplate> findRunbooks(String signal, String category);

    Optional<RunbookTemplate> findRunbookById(String runbookId);

    Optional<AnalysisFeedback> findAnalysisFeedback(UUID analysisId);

    AnalysisFeedback saveAnalysisFeedback(AnalysisFeedback feedback);

    List<AnalysisFeedback> findAnalysisFeedback(int limit);

    OperationSettings getOperationSettings();

    OperationSettings saveOperationSettings(OperationSettings settings);

    List<AuditLog> findAuditLogs(String actor, String action, String targetType, String targetId,
                                 String requestId, Instant from, Instant to, int limit);

    CleanupPreview cleanup(OperationSettings settings, boolean execute);
}
