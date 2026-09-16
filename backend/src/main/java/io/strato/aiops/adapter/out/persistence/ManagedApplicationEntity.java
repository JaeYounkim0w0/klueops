package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.application.ApplicationDeploymentType;
import io.strato.aiops.domain.application.ApplicationStatus;
import io.strato.aiops.domain.application.ManagedApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "managed_applications")
class ManagedApplicationEntity {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID clusterId;
    @Column(nullable = false)
    private String namespace;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationDeploymentType deploymentType;
    private String image;
    private String helmReleaseName;
    private String helmChart;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;
    @Column(nullable = false)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant lastSyncedAt;
    private String lastSyncStatus;
    @Column(length = 1000)
    private String lastSyncError;
    private Integer currentReleaseRevision;
    private UUID chartVersionId;
    private UUID valuesRevisionId;
    private Instant archivedAt;

    /** ManagedApplicationEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected ManagedApplicationEntity() {
    }

    /** ManagedApplicationEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private ManagedApplicationEntity(UUID id, UUID clusterId, String namespace, String name, ApplicationDeploymentType deploymentType,
                                     String image, String helmReleaseName, String helmChart, ApplicationStatus status, String createdBy,
                                     Instant createdAt, Instant updatedAt, Instant lastSyncedAt, String lastSyncStatus, String lastSyncError) {
        this.id = id;
        this.clusterId = clusterId;
        this.namespace = namespace;
        this.name = name;
        this.deploymentType = deploymentType;
        this.image = image;
        this.helmReleaseName = helmReleaseName;
        this.helmChart = helmChart;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastSyncedAt = lastSyncedAt;
        this.lastSyncStatus = lastSyncStatus;
        this.lastSyncError = lastSyncError;
    }

    /** ManagedApplicationEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static ManagedApplicationEntity fromDomain(ManagedApplication application) {
        ManagedApplicationEntity entity = new ManagedApplicationEntity(application.id(), application.clusterId(), application.namespace(), application.name(),
                application.deploymentType(), application.image(), application.helmReleaseName(), application.helmChart(),
                application.status(), application.createdBy(), application.createdAt(), application.updatedAt(),
                application.lastSyncedAt(), application.lastSyncStatus(), application.lastSyncError());
        entity.currentReleaseRevision = application.currentReleaseRevision();
        entity.chartVersionId = application.chartVersionId();
        entity.valuesRevisionId = application.valuesRevisionId();
        entity.archivedAt = application.archivedAt();
        return entity;
    }

    /** ManagedApplicationEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    ManagedApplication toDomain() {
        return new ManagedApplication(id, clusterId, namespace, name, deploymentType, image, helmReleaseName, helmChart,
                status, createdBy, createdAt, updatedAt, lastSyncedAt, lastSyncStatus, lastSyncError,
                currentReleaseRevision, chartVersionId, valuesRevisionId, archivedAt);
    }
}
