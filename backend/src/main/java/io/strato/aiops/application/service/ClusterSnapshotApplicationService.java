package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.ClusterSyncStatusResult;
import io.strato.aiops.application.port.in.ClusterResourcePageResult;
import io.strato.aiops.application.port.in.GetClusterSnapshotUseCase;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesEventSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.SyncJobRepositoryPort;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import io.strato.aiops.domain.sync.SyncJob;
import io.strato.aiops.domain.sync.SyncJobStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClusterSnapshotApplicationService implements GetClusterSnapshotUseCase {

    private static final int DEFAULT_QUERY_LIMIT = 500;

    private final ClusterRepositoryPort clusterRepositoryPort;
    private final KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort;
    private final KubernetesEventSnapshotRepositoryPort eventSnapshotRepositoryPort;
    private final SyncJobRepositoryPort syncJobRepositoryPort;

    public ClusterSnapshotApplicationService(
            ClusterRepositoryPort clusterRepositoryPort,
            KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort,
            KubernetesEventSnapshotRepositoryPort eventSnapshotRepositoryPort,
            SyncJobRepositoryPort syncJobRepositoryPort
    ) {
        this.clusterRepositoryPort = clusterRepositoryPort;
        this.resourceSnapshotRepositoryPort = resourceSnapshotRepositoryPort;
        this.eventSnapshotRepositoryPort = eventSnapshotRepositoryPort;
        this.syncJobRepositoryPort = syncJobRepositoryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KubernetesResourceSnapshot> listResources(UUID clusterId, String namespace, String resourceType) {
        requireCluster(clusterId);
        return latestSucceededSyncJob(clusterId)
                .map(syncJob -> resourceSnapshotRepositoryPort.findBySyncJobId(
                        syncJob.id(),
                        blankToNull(namespace),
                        blankToNull(resourceType),
                        DEFAULT_QUERY_LIMIT
                ))
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public ClusterResourcePageResult pageResources(UUID clusterId, String namespace, String resourceType, int page, int size) {
        requireCluster(clusterId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(20, Math.min(size, 200));
        String safeNamespace = blankToNull(namespace);
        String safeResourceType = blankToNull(resourceType);
        return latestSucceededSyncJob(clusterId)
                .map(syncJob -> {
                    var resourcePage = resourceSnapshotRepositoryPort.findPageBySyncJobId(
                            syncJob.id(), safeNamespace, safeResourceType, safePage, safeSize
                    );
                    return new ClusterResourcePageResult(
                            resourcePage.items(),
                            safePage,
                            safeSize,
                            resourcePage.totalElements(),
                            resourcePage.totalPages(),
                            resourceSnapshotRepositoryPort.countProblemsBySyncJobId(syncJob.id(), safeNamespace, safeResourceType),
                            resourceSnapshotRepositoryPort.countNamespacesBySyncJobId(syncJob.id()).stream()
                                    .map(facet -> new ClusterResourcePageResult.Facet(facet.value(), facet.count()))
                                    .toList(),
                            resourceSnapshotRepositoryPort.countResourceTypesBySyncJobId(syncJob.id(), safeNamespace).stream()
                                    .map(facet -> new ClusterResourcePageResult.Facet(facet.value(), facet.count()))
                                    .toList()
                    );
                })
                .orElseGet(() -> new ClusterResourcePageResult(List.of(), safePage, safeSize, 0, 0, 0, List.of(), List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KubernetesEventSnapshot> listEvents(UUID clusterId, String namespace) {
        requireCluster(clusterId);
        return latestSucceededSyncJob(clusterId)
                .map(syncJob -> eventSnapshotRepositoryPort.findBySyncJobId(
                        syncJob.id(),
                        blankToNull(namespace),
                        DEFAULT_QUERY_LIMIT
                ))
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClusterSyncStatusResult> getLatestSyncStatus(UUID clusterId) {
        requireCluster(clusterId);
        return syncJobRepositoryPort.findLatestByClusterId(clusterId)
                .map(this::toResult);
    }

    private ClusterSyncStatusResult toResult(SyncJob syncJob) {
        return new ClusterSyncStatusResult(
                syncJob.id(),
                syncJob.asyncJobId(),
                syncJob.clusterId(),
                syncJob.syncType(),
                syncJob.status(),
                syncJob.resourceCount(),
                syncJob.eventCount(),
                syncJob.startedAt(),
                syncJob.completedAt(),
                syncJob.errorMessage(),
                syncJob.createdAt()
        );
    }

    private void requireCluster(UUID clusterId) {
        clusterRepositoryPort.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    private Optional<SyncJob> latestSucceededSyncJob(UUID clusterId) {
        return syncJobRepositoryPort.findLatestByClusterIdAndStatusIn(clusterId, EnumSet.of(SyncJobStatus.SUCCEEDED));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
