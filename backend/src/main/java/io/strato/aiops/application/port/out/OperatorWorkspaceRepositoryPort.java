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

    List<SearchResult> search(String query, int limit);

    Optional<KubernetesResourceSnapshot> findLatestResource(UUID clusterId, String namespace, String kind, String name);

    List<KubernetesResourceSnapshot> findResourceHistory(UUID clusterId, String namespace, String kind, String name, int limit);

    List<KubernetesResourceSnapshot> findLatestNamespaceResources(UUID clusterId, String namespace, int limit);

    IncidentCollaboration saveCollaboration(IncidentCollaboration collaboration);

    IncidentCollaboration findCollaboration(UUID incidentId);

    List<IncidentLink> findIncidentLinks(UUID incidentId);

    void saveIncidentLink(IncidentLink link);

    void deleteIncidentLink(UUID incidentId, UUID relatedIncidentId);

    List<ManagedRunbook> findRunbookLibrary();

    Optional<ManagedRunbook> findManagedRunbook(String id);

    ManagedRunbook saveCustomRunbook(ManagedRunbook runbook, String changeNote, String changedBy);

    List<RunbookVersion> findRunbookVersions(String runbookId);

    Optional<ManagedRunbook> findRunbookVersion(String runbookId, int version);

    void deleteCustomRunbook(String runbookId);

    List<Incident> findIncidentsForResource(UUID clusterId, String namespace, String kind, String name, int limit);
}
