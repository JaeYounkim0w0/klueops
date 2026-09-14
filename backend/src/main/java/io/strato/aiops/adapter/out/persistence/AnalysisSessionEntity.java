package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.analysis.AnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_sessions")
class AnalysisSessionEntity {

    @Id
    private UUID id;
    private UUID asyncJobId;
    @Column(nullable = false)
    private UUID clusterId;
    private UUID applicationId;
    private String namespace;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;
    @Column(nullable = false)
    private String aiProvider;
    @Column(nullable = false)
    private String aiModel;
    @Column(nullable = false)
    private String promptVersion;
    @Column(nullable = false)
    private String schemaVersion;
    @Column(nullable = false, length = 10)
    private String locale;
    @Column(length = 1000)
    private String resultSummary;
    @Column(columnDefinition = "text")
    private String resultJson;
    @Column(nullable = false)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;

    protected AnalysisSessionEntity() {
    }

    private AnalysisSessionEntity(UUID id, UUID asyncJobId, UUID clusterId, UUID applicationId, String namespace,
                                  AnalysisStatus status, String aiProvider, String aiModel, String promptVersion,
                                  String schemaVersion, String locale, String resultSummary, String resultJson, String createdBy,
                                  Instant createdAt) {
        this.id = id;
        this.asyncJobId = asyncJobId;
        this.clusterId = clusterId;
        this.applicationId = applicationId;
        this.namespace = namespace;
        this.status = status;
        this.aiProvider = aiProvider;
        this.aiModel = aiModel;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.locale = locale;
        this.resultSummary = resultSummary;
        this.resultJson = resultJson;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    static AnalysisSessionEntity fromDomain(AnalysisSession analysisSession) {
        return new AnalysisSessionEntity(analysisSession.id(), analysisSession.asyncJobId(), analysisSession.clusterId(),
                analysisSession.applicationId(), analysisSession.namespace(), analysisSession.status(),
                analysisSession.aiProvider(), analysisSession.aiModel(), analysisSession.promptVersion(),
                analysisSession.schemaVersion(), analysisSession.locale(), analysisSession.resultSummary(),
                analysisSession.resultJson(), analysisSession.createdBy(), analysisSession.createdAt());
    }

    AnalysisSession toDomain() {
        return new AnalysisSession(id, asyncJobId, clusterId, applicationId, namespace, status, aiProvider, aiModel,
                promptVersion, schemaVersion, locale, resultSummary, resultJson, createdBy, createdAt);
    }
}
