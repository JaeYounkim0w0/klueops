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

    /** ManagedApplication 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ManagedApplication(UUID id, UUID clusterId, String namespace, String name, ApplicationDeploymentType deploymentType,
                              String image, String helmReleaseName, String helmChart, ApplicationStatus status, String createdBy,
                              Instant createdAt, Instant updatedAt, Instant lastSyncedAt, String lastSyncStatus, String lastSyncError) {
        this(id, clusterId, namespace, name, deploymentType, image, helmReleaseName, helmChart, status, createdBy,
                createdAt, updatedAt, lastSyncedAt, lastSyncStatus, lastSyncError, null, null, null, null);
    }

    /** ManagedApplication 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** ManagedApplication의 dockerImage 처리에 필요한 업무 로직을 수행한다. */
    public static ManagedApplication dockerImage(UUID clusterId, String namespace, String name, String image, String actor) {
        Instant now = Instant.now();
        return new ManagedApplication(UUID.randomUUID(), clusterId, namespace, name, ApplicationDeploymentType.DOCKER_IMAGE,
                image, null, null, ApplicationStatus.DEPLOY_REQUESTED, actor, now, now, null, null, null);
    }

    /** ManagedApplication의 helmChart 처리에 필요한 업무 로직을 수행한다. */
    public static ManagedApplication helmChart(UUID clusterId, String namespace, String name, String releaseName, String chart, String actor) {
        Instant now = Instant.now();
        return new ManagedApplication(UUID.randomUUID(), clusterId, namespace, name, ApplicationDeploymentType.HELM_CHART,
                null, releaseName, chart, ApplicationStatus.DEPLOY_REQUESTED, actor, now, now, null, null, null);
    }

    /** ManagedApplication의 synced 처리의 핵심 작업 흐름을 실행한다. */
    public ManagedApplication synced(ApplicationStatus status, Instant syncedAt, String syncStatus, String syncError) {
        return new ManagedApplication(id, clusterId, namespace, name, deploymentType, image, helmReleaseName, helmChart,
                status, createdBy, createdAt, Instant.now(), syncedAt, syncStatus, syncError,
                currentReleaseRevision, chartVersionId, valuesRevisionId, archivedAt);
    }

    /** ManagedApplication의 withStatus 처리에 필요한 업무 로직을 수행한다. */
    public ManagedApplication withStatus(ApplicationStatus status, String syncStatus, String syncError) {
        return new ManagedApplication(id, clusterId, namespace, name, deploymentType, image, helmReleaseName, helmChart,
                status, createdBy, createdAt, Instant.now(), Instant.now(), syncStatus, syncError,
                currentReleaseRevision, chartVersionId, valuesRevisionId, archivedAt);
    }

    /** ManagedApplication의 withReleaseMetadata 처리에 필요한 업무 로직을 수행한다. */
    public ManagedApplication withReleaseMetadata(Integer revision, UUID versionId, UUID valuesId) {
        return new ManagedApplication(id, clusterId, namespace, name, deploymentType, image, helmReleaseName, helmChart,
                status, createdBy, createdAt, Instant.now(), lastSyncedAt, lastSyncStatus, lastSyncError,
                revision, versionId, valuesId, archivedAt);
    }

    /** ManagedApplication의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() { return id; }
    /** ManagedApplication의 clusterId 처리에 필요한 업무 로직을 수행한다. */
    public UUID clusterId() { return clusterId; }
    /** ManagedApplication의 namespace 처리에 필요한 업무 로직을 수행한다. */
    public String namespace() { return namespace; }
    /** ManagedApplication의 name 처리에 필요한 업무 로직을 수행한다. */
    public String name() { return name; }
    /** ManagedApplication의 deploymentType 처리에 필요한 업무 로직을 수행한다. */
    public ApplicationDeploymentType deploymentType() { return deploymentType; }
    /** ManagedApplication의 image 처리에 필요한 업무 로직을 수행한다. */
    public String image() { return image; }
    /** ManagedApplication의 helmReleaseName 처리에 필요한 업무 로직을 수행한다. */
    public String helmReleaseName() { return helmReleaseName; }
    /** ManagedApplication의 helmChart 처리에 필요한 업무 로직을 수행한다. */
    public String helmChart() { return helmChart; }
    /** ManagedApplication의 status 처리에 필요한 업무 로직을 수행한다. */
    public ApplicationStatus status() { return status; }
    /** ManagedApplication의 createdBy 처리에 필요한 데이터를 생성하거나 저장한다. */
    public String createdBy() { return createdBy; }
    /** ManagedApplication의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() { return createdAt; }
    /** ManagedApplication의 updatedAt 처리 대상의 상태를 갱신한다. */
    public Instant updatedAt() { return updatedAt; }
    /** ManagedApplication의 lastSyncedAt 처리에 필요한 업무 로직을 수행한다. */
    public Instant lastSyncedAt() { return lastSyncedAt; }
    /** ManagedApplication의 lastSyncStatus 처리에 필요한 업무 로직을 수행한다. */
    public String lastSyncStatus() { return lastSyncStatus; }
    /** ManagedApplication의 lastSyncError 처리에 필요한 업무 로직을 수행한다. */
    public String lastSyncError() { return lastSyncError; }
    /** ManagedApplication의 currentReleaseRevision 처리에 필요한 업무 로직을 수행한다. */
    public Integer currentReleaseRevision() { return currentReleaseRevision; }
    /** ManagedApplication의 chartVersionId 처리에 필요한 업무 로직을 수행한다. */
    public UUID chartVersionId() { return chartVersionId; }
    /** ManagedApplication의 valuesRevisionId 처리에 필요한 업무 로직을 수행한다. */
    public UUID valuesRevisionId() { return valuesRevisionId; }
    /** ManagedApplication의 archivedAt 처리에 필요한 업무 로직을 수행한다. */
    public Instant archivedAt() { return archivedAt; }
}
