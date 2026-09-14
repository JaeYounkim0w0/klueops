package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.GetClusterSyncSettingsUseCase;
import io.strato.aiops.application.port.in.UpdateClusterSyncSettingsCommand;
import io.strato.aiops.application.port.in.UpdateClusterSyncSettingsUseCase;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.ClusterSyncSettingRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.ClusterSyncSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ClusterSyncSettingsApplicationService implements UpdateClusterSyncSettingsUseCase, GetClusterSyncSettingsUseCase {

    private static final int MIN_SYNC_INTERVAL_SECONDS = 60;
    private static final int MAX_SYNC_INTERVAL_SECONDS = 3600;

    private final ClusterRepositoryPort clusterRepositoryPort;
    private final ClusterSyncSettingRepositoryPort clusterSyncSettingRepositoryPort;
    private final AuditLogRepositoryPort auditLogRepositoryPort;

    public ClusterSyncSettingsApplicationService(
            ClusterRepositoryPort clusterRepositoryPort,
            ClusterSyncSettingRepositoryPort clusterSyncSettingRepositoryPort,
            AuditLogRepositoryPort auditLogRepositoryPort
    ) {
        this.clusterRepositoryPort = clusterRepositoryPort;
        this.clusterSyncSettingRepositoryPort = clusterSyncSettingRepositoryPort;
        this.auditLogRepositoryPort = auditLogRepositoryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public ClusterSyncSetting getClusterSyncSettings(UUID clusterId) {
        clusterRepositoryPort.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
        return clusterSyncSettingRepositoryPort.findByClusterId(clusterId)
                .orElseGet(() -> ClusterSyncSetting.create(clusterId, true, 300));
    }

    @Override
    @Transactional
    public ClusterSyncSetting updateClusterSyncSettings(UUID clusterId, UpdateClusterSyncSettingsCommand command, String actor, String requestId) {
        clusterRepositoryPort.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
        validate(command);

        ClusterSyncSetting existing = clusterSyncSettingRepositoryPort.findByClusterId(clusterId)
                .orElseGet(() -> ClusterSyncSetting.create(clusterId, true, 300));
        ClusterSyncSetting saved = clusterSyncSettingRepositoryPort.save(existing.update(command.autoSyncEnabled(), command.syncIntervalSeconds()));

        auditLogRepositoryPort.save(AuditLog.create(
                "CLUSTER_SYNC_SETTINGS_UPDATED",
                "CLUSTER",
                clusterId.toString(),
                actor,
                requestId
        ));
        return saved;
    }

    private void validate(UpdateClusterSyncSettingsCommand command) {
        Integer syncIntervalSeconds = command.syncIntervalSeconds();
        if (syncIntervalSeconds != null && (syncIntervalSeconds < MIN_SYNC_INTERVAL_SECONDS || syncIntervalSeconds > MAX_SYNC_INTERVAL_SECONDS)) {
            throw new IllegalArgumentException("syncIntervalSeconds must be between 60 and 3600");
        }
    }
}
