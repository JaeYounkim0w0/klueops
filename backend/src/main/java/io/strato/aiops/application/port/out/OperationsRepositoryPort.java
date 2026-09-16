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

    /** OperationsRepositoryPort의 findIncidents 처리 결과를 조회해 반환한다. */
    List<Incident> findIncidents(UUID clusterId, String namespace, String state, String severity, int limit);

    /** OperationsRepositoryPort의 findIncidentById 처리 결과를 조회해 반환한다. */
    Optional<Incident> findIncidentById(UUID incidentId);

    /** OperationsRepositoryPort의 findIncidentByFingerprint 처리 결과를 조회해 반환한다. */
    Optional<Incident> findIncidentByFingerprint(String fingerprint);

    /** OperationsRepositoryPort의 saveIncident 처리에 필요한 데이터를 생성하거나 저장한다. */
    Incident saveIncident(Incident incident);

    /** OperationsRepositoryPort의 saveIncidentEvidence 처리에 필요한 데이터를 생성하거나 저장한다. */
    void saveIncidentEvidence(IncidentEvidence evidence);

    /** OperationsRepositoryPort의 findIncidentEvidence 처리 결과를 조회해 반환한다. */
    List<IncidentEvidence> findIncidentEvidence(UUID incidentId);

    /** OperationsRepositoryPort의 saveIncidentActivity 처리에 필요한 데이터를 생성하거나 저장한다. */
    void saveIncidentActivity(IncidentActivity activity);

    /** OperationsRepositoryPort의 findIncidentActivities 처리 결과를 조회해 반환한다. */
    List<IncidentActivity> findIncidentActivities(UUID incidentId);

    /** OperationsRepositoryPort의 findIncidentActivitiesByIncidentIds 처리 결과를 조회해 반환한다. */
    List<IncidentActivity> findIncidentActivitiesByIncidentIds(Set<UUID> incidentIds);

    /** OperationsRepositoryPort의 findIncidentRecovery 처리 결과를 조회해 반환한다. */
    Optional<IncidentRecovery> findIncidentRecovery(UUID incidentId);

    /** OperationsRepositoryPort의 saveIncidentRecovery 처리에 필요한 데이터를 생성하거나 저장한다. */
    IncidentRecovery saveIncidentRecovery(IncidentRecovery recovery);

    /** OperationsRepositoryPort의 saveNotification 처리에 필요한 데이터를 생성하거나 저장한다. */
    Notification saveNotification(Notification notification);

    /** OperationsRepositoryPort의 findNotificationByDedupKey 처리 결과를 조회해 반환한다. */
    Optional<Notification> findNotificationByDedupKey(String dedupKey);

    /** OperationsRepositoryPort의 findNotifications 처리 결과를 조회해 반환한다. */
    List<Notification> findNotifications(boolean unreadOnly, int limit);

    /** OperationsRepositoryPort의 countUnreadNotifications 처리 계약을 정의한다. */
    long countUnreadNotifications();

    /** OperationsRepositoryPort의 markNotificationRead 처리 계약을 정의한다. */
    void markNotificationRead(UUID notificationId);

    /** OperationsRepositoryPort의 markAllNotificationsRead 처리 계약을 정의한다. */
    void markAllNotificationsRead();

    /** OperationsRepositoryPort의 findPolicyDefinitions 처리 결과를 조회해 반환한다. */
    List<PolicyDefinition> findPolicyDefinitions();

    /** OperationsRepositoryPort의 findPolicyDefinition 처리 결과를 조회해 반환한다. */
    Optional<PolicyDefinition> findPolicyDefinition(String policyId);

    /** OperationsRepositoryPort의 savePolicyDefinition 처리에 필요한 데이터를 생성하거나 저장한다. */
    PolicyDefinition savePolicyDefinition(PolicyDefinition definition);

    /** OperationsRepositoryPort의 replacePolicyEvaluations 처리 계약을 정의한다. */
    void replacePolicyEvaluations(UUID clusterId, List<PolicyEvaluation> evaluations);

    /** OperationsRepositoryPort의 findPolicyEvaluations 처리 결과를 조회해 반환한다. */
    List<PolicyEvaluation> findPolicyEvaluations(UUID clusterId, String namespace, String result, int limit);

    /** OperationsRepositoryPort의 findResourceBaselines 처리 결과를 조회해 반환한다. */
    List<ResourceBaseline> findResourceBaselines(UUID clusterId);

    /** OperationsRepositoryPort의 saveResourceBaseline 처리에 필요한 데이터를 생성하거나 저장한다. */
    void saveResourceBaseline(ResourceBaseline baseline);

    /** OperationsRepositoryPort의 deleteResourceBaseline 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteResourceBaseline(UUID baselineId);

    /** OperationsRepositoryPort의 saveResourceChange 처리에 필요한 데이터를 생성하거나 저장한다. */
    void saveResourceChange(ResourceChange change);

    /** OperationsRepositoryPort의 findResourceChanges 처리 결과를 조회해 반환한다. */
    List<ResourceChange> findResourceChanges(UUID clusterId, String namespace, int limit);

    /** OperationsRepositoryPort의 findResourceChangeById 처리 결과를 조회해 반환한다. */
    Optional<ResourceChange> findResourceChangeById(UUID changeId);

    /** OperationsRepositoryPort의 findRunbooks 처리 결과를 조회해 반환한다. */
    List<RunbookTemplate> findRunbooks(String signal, String category);

    /** OperationsRepositoryPort의 findRunbookById 처리 결과를 조회해 반환한다. */
    Optional<RunbookTemplate> findRunbookById(String runbookId);

    /** OperationsRepositoryPort의 findAnalysisFeedback 처리 결과를 조회해 반환한다. */
    Optional<AnalysisFeedback> findAnalysisFeedback(UUID analysisId);

    /** OperationsRepositoryPort의 saveAnalysisFeedback 처리에 필요한 데이터를 생성하거나 저장한다. */
    AnalysisFeedback saveAnalysisFeedback(AnalysisFeedback feedback);

    /** OperationsRepositoryPort의 findAnalysisFeedback 처리 결과를 조회해 반환한다. */
    List<AnalysisFeedback> findAnalysisFeedback(int limit);

    /** OperationsRepositoryPort의 getOperationSettings 처리 결과를 조회해 반환한다. */
    OperationSettings getOperationSettings();

    /** OperationsRepositoryPort의 saveOperationSettings 처리에 필요한 데이터를 생성하거나 저장한다. */
    OperationSettings saveOperationSettings(OperationSettings settings);

    /** OperationsRepositoryPort의 findAuditLogs 처리 결과를 조회해 반환한다. */
    List<AuditLog> findAuditLogs(String actor, String action, String targetType, String targetId,
                                 String requestId, Instant from, Instant to, int limit);

    /** OperationsRepositoryPort의 cleanup 처리 계약을 정의한다. */
    CleanupPreview cleanup(OperationSettings settings, boolean execute);
}
