package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.IncidentCollaboration;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.IncidentLink;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.ManagedRunbook;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.RunbookVersion;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.SearchResult;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperatorWorkspaceRepositoryPort {

    /** OperatorWorkspaceRepositoryPort의 search 처리 계약을 정의한다. */
    List<SearchResult> search(String query, int limit);

    /** OperatorWorkspaceRepositoryPort의 findLatestResource 처리 결과를 조회해 반환한다. */
    Optional<KubernetesResourceSnapshot> findLatestResource(UUID clusterId, String namespace, String kind, String name);

    /** OperatorWorkspaceRepositoryPort의 findResourceHistory 처리 결과를 조회해 반환한다. */
    List<KubernetesResourceSnapshot> findResourceHistory(UUID clusterId, String namespace, String kind, String name, int limit);

    /** OperatorWorkspaceRepositoryPort의 findLatestNamespaceResources 처리 결과를 조회해 반환한다. */
    List<KubernetesResourceSnapshot> findLatestNamespaceResources(UUID clusterId, String namespace, int limit);

    /** OperatorWorkspaceRepositoryPort의 saveCollaboration 처리에 필요한 데이터를 생성하거나 저장한다. */
    IncidentCollaboration saveCollaboration(IncidentCollaboration collaboration);

    /** OperatorWorkspaceRepositoryPort의 findCollaboration 처리 결과를 조회해 반환한다. */
    IncidentCollaboration findCollaboration(UUID incidentId);

    /** OperatorWorkspaceRepositoryPort의 findIncidentLinks 처리 결과를 조회해 반환한다. */
    List<IncidentLink> findIncidentLinks(UUID incidentId);

    /** OperatorWorkspaceRepositoryPort의 saveIncidentLink 처리에 필요한 데이터를 생성하거나 저장한다. */
    void saveIncidentLink(IncidentLink link);

    /** OperatorWorkspaceRepositoryPort의 deleteIncidentLink 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteIncidentLink(UUID incidentId, UUID relatedIncidentId);

    /** OperatorWorkspaceRepositoryPort의 findRunbookLibrary 처리 결과를 조회해 반환한다. */
    List<ManagedRunbook> findRunbookLibrary();

    /** OperatorWorkspaceRepositoryPort의 findManagedRunbook 처리 결과를 조회해 반환한다. */
    Optional<ManagedRunbook> findManagedRunbook(String id);

    /** OperatorWorkspaceRepositoryPort의 saveCustomRunbook 처리에 필요한 데이터를 생성하거나 저장한다. */
    ManagedRunbook saveCustomRunbook(ManagedRunbook runbook, String changeNote, String changedBy);

    /** OperatorWorkspaceRepositoryPort의 findRunbookVersions 처리 결과를 조회해 반환한다. */
    List<RunbookVersion> findRunbookVersions(String runbookId);

    /** OperatorWorkspaceRepositoryPort의 findRunbookVersion 처리 결과를 조회해 반환한다. */
    Optional<ManagedRunbook> findRunbookVersion(String runbookId, int version);

    /** OperatorWorkspaceRepositoryPort의 deleteCustomRunbook 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteCustomRunbook(String runbookId);

    /** OperatorWorkspaceRepositoryPort의 findIncidentsForResource 처리 결과를 조회해 반환한다. */
    List<Incident> findIncidentsForResource(UUID clusterId, String namespace, String kind, String name, int limit);
}
