package io.strato.aiops.domain.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ManagedApplication {

    private final UUID id;
    private final UUID clusterId;
    private final String namespace;
    private final String name;
    private final ApplicationDeploymentType deploymentType;
    private final String image;
    private final String helmReleaseName;
    private final String helmChart;
    private final ApplicationStatus status;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastSyncedAt;
    private final String lastSyncStatus;
    private final String lastSyncError;
    private final Integer currentReleaseRevision;
    private final UUID chartVersionId;
    private final UUID valuesRevisionId;
    private final Instant archivedAt;

    public ManagedApplication(UUID id, UUID clusterId, String namespace, String name, ApplicationDeploymentType deploymentType,
                              String image, String helmReleaseName, String helmChart, ApplicationStatus status, String createdBy,
                              Instant createdAt, Instant updatedAt, Instant lastSyncedAt, String lastSyncStatus, String lastSyncError) {
        this(id, clusterId, namespace, name, deploymentType, image, helmReleaseName, helmChart, status, createdBy,
                createdAt, updatedAt, lastSyncedAt, lastSyncStatus, lastSyncError, null, null, null, null);
    }

    public ManagedApplication(UUID id, UUID clusterId, String namespace, String name, ApplicationDeploymentType deploymentType,
                              String image, String helmReleaseName, String helmChart, ApplicationStatus status, String createdBy,
                              Instant createdAt, Instant updatedAt, Instant lastSyncedAt, String lastSyncStatus,
                              String lastSyncError, Integer currentReleaseRevision, UUID chartVersionId,
                              UUID valuesRevisionId, Instant archivedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.deploymentType = Objects.requireNonNull(deploymentType, "deploymentType must not be null");
        this.image = image;
        this.helmReleaseName = helmReleaseName;
        this.helmChart = helmChart;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.lastSyncedAt = lastSyncedAt;
        this.lastSyncStatus = lastSyncStatus;
        this.lastSyncError = lastSyncError;
        this.currentReleaseRevision = currentReleaseRevision;
        this.chartVersionId = chartVersionId;
        this.valuesRevisionId = valuesRevisionId;
        this.archivedAt = archivedAt;
    }

    public static ManagedApplication dockerImage(UUID clusterId, String namespace, String name, String image, String actor) {
        Instant now = Instant.now();
        return new ManagedApplication(UUID.randomUUID(), clusterId, namespace, name, ApplicationDeploymentType.DOCKER_IMAGE,
                image, null, null, ApplicationStatus.DEPLOY_REQUESTED, actor, now, now, null, null, null);
    }

    public static ManagedApplication helmChart(UUID clusterId, String namespace, String name, String releaseName, String chart, String actor) {
        Instant now = Instant.now();
        return new ManagedApplication(UUID.randomUUID(), clusterId, namespace, name, ApplicationDeploymentType.HELM_CHART,
                null, releaseName, chart, ApplicationStatus.DEPLOY_REQUESTED, actor, now, now, null, null, null);
    }

    public ManagedApplication synced(ApplicationStatus status, Instant syncedAt, String syncStatus, String syncError) {
        return new ManagedApplication(id, clusterId, namespace, name, deploymentType, image, helmReleaseName, helmChart,
                status, createdBy, createdAt, Instant.now(), syncedAt, syncStatus, syncError,
                currentReleaseRevision, chartVersionId, valuesRevisionId, archivedAt);
    }

    public ManagedApplication withStatus(ApplicationStatus status, String syncStatus, String syncError) {
        return new ManagedApplication(id, clusterId, namespace, name, deploymentType, image, helmReleaseName, helmChart,
                status, createdBy, createdAt, Instant.now(), Instant.now(), syncStatus, syncError,
                currentReleaseRevision, chartVersionId, valuesRevisionId, archivedAt);
    }

    public ManagedApplication withReleaseMetadata(Integer revision, UUID versionId, UUID valuesId) {
        return new ManagedApplication(id, clusterId, namespace, name, deploymentType, image, helmReleaseName, helmChart,
                status, createdBy, createdAt, Instant.now(), lastSyncedAt, lastSyncStatus, lastSyncError,
                revision, versionId, valuesId, archivedAt);
    }

    public UUID id() { return id; }
    public UUID clusterId() { return clusterId; }
    public String namespace() { return namespace; }
    public String name() { return name; }
    public ApplicationDeploymentType deploymentType() { return deploymentType; }
    public String image() { return image; }
    public String helmReleaseName() { return helmReleaseName; }
    public String helmChart() { return helmChart; }
    public ApplicationStatus status() { return status; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant lastSyncedAt() { return lastSyncedAt; }
    public String lastSyncStatus() { return lastSyncStatus; }
    public String lastSyncError() { return lastSyncError; }
    public Integer currentReleaseRevision() { return currentReleaseRevision; }
    public UUID chartVersionId() { return chartVersionId; }
    public UUID valuesRevisionId() { return valuesRevisionId; }
    public Instant archivedAt() { return archivedAt; }
}
