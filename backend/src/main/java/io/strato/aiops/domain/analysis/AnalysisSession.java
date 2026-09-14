package io.strato.aiops.domain.analysis;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AnalysisSession {

    private final UUID id;
    private final UUID asyncJobId;
    private final UUID clusterId;
    private final UUID applicationId;
    private final String namespace;
    private final AnalysisStatus status;
    private final String aiProvider;
    private final String aiModel;
    private final String promptVersion;
    private final String schemaVersion;
    private final String locale;
    private final String resultSummary;
    private final String resultJson;
    private final String createdBy;
    private final Instant createdAt;

    public AnalysisSession(UUID id, UUID clusterId, UUID applicationId, String namespace, AnalysisStatus status,
                           String aiProvider, String aiModel, String promptVersion, String schemaVersion,
                           String resultSummary, String resultJson, String createdBy, Instant createdAt) {
        this(id, null, clusterId, applicationId, namespace, status, aiProvider, aiModel, promptVersion, schemaVersion,
                SupportedLocale.ENGLISH.tag(), resultSummary, resultJson, createdBy, createdAt);
    }

    public AnalysisSession(UUID id, UUID asyncJobId, UUID clusterId, UUID applicationId, String namespace, AnalysisStatus status,
                           String aiProvider, String aiModel, String promptVersion, String schemaVersion,
                           String resultSummary, String resultJson, String createdBy, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.asyncJobId = asyncJobId;
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
        this.applicationId = applicationId;
        this.namespace = namespace;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.aiProvider = Objects.requireNonNull(aiProvider, "aiProvider must not be null");
        this.aiModel = Objects.requireNonNull(aiModel, "aiModel must not be null");
        this.promptVersion = Objects.requireNonNull(promptVersion, "promptVersion must not be null");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        this.locale = SupportedLocale.ENGLISH.tag();
        this.resultSummary = resultSummary;
        this.resultJson = resultJson;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public AnalysisSession(UUID id, UUID asyncJobId, UUID clusterId, UUID applicationId, String namespace, AnalysisStatus status,
                           String aiProvider, String aiModel, String promptVersion, String schemaVersion, String locale,
                           String resultSummary, String resultJson, String createdBy, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.asyncJobId = asyncJobId;
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
        this.applicationId = applicationId;
        this.namespace = namespace;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.aiProvider = Objects.requireNonNull(aiProvider, "aiProvider must not be null");
        this.aiModel = Objects.requireNonNull(aiModel, "aiModel must not be null");
        this.promptVersion = Objects.requireNonNull(promptVersion, "promptVersion must not be null");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        this.locale = Objects.requireNonNull(locale, "locale must not be null");
        this.resultSummary = resultSummary;
        this.resultJson = resultJson;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static AnalysisSession running(UUID asyncJobId, UUID clusterId, UUID applicationId, String namespace, String actor) {
        return running(asyncJobId, clusterId, applicationId, namespace, SupportedLocale.ENGLISH, actor);
    }

    public static AnalysisSession running(UUID asyncJobId, UUID clusterId, UUID applicationId, String namespace,
                                          SupportedLocale locale, String actor) {
        return new AnalysisSession(UUID.randomUUID(), asyncJobId, clusterId, applicationId, namespace, AnalysisStatus.RUNNING,
                "ollama", "configured-model", "v1", "analysis-result.v1", locale.tag(),
                "AI analysis is running", null, actor, Instant.now());
    }

    public static AnalysisSession succeeded(UUID clusterId, UUID applicationId, String namespace, String resultJson, String actor) {
        return succeeded(clusterId, applicationId, namespace, resultJson, "AI analysis completed", "analysis-result.v1", actor);
    }

    public static AnalysisSession succeeded(UUID clusterId, UUID applicationId, String namespace, String resultJson,
                                            String resultSummary, String schemaVersion, String actor) {
        return succeeded(clusterId, applicationId, namespace, resultJson, resultSummary, schemaVersion,
                SupportedLocale.ENGLISH, actor);
    }

    public static AnalysisSession succeeded(UUID clusterId, UUID applicationId, String namespace, String resultJson,
                                            String resultSummary, String schemaVersion, SupportedLocale locale,
                                            String actor) {
        return new AnalysisSession(UUID.randomUUID(), null, clusterId, applicationId, namespace, AnalysisStatus.SUCCEEDED,
                "ollama", "configured-model", "v1", schemaVersion, locale.tag(), resultSummary, resultJson,
                actor, Instant.now());
    }

    public AnalysisSession succeeded(String resultJson, String resultSummary, String schemaVersion) {
        return new AnalysisSession(id, asyncJobId, clusterId, applicationId, namespace, AnalysisStatus.SUCCEEDED,
                aiProvider, aiModel, promptVersion, schemaVersion, locale, resultSummary, resultJson, createdBy, createdAt);
    }

    public AnalysisSession failed(String resultJson, String resultSummary) {
        return new AnalysisSession(id, asyncJobId, clusterId, applicationId, namespace, AnalysisStatus.FAILED,
                aiProvider, aiModel, promptVersion, schemaVersion, locale, resultSummary, resultJson, createdBy, createdAt);
    }

    public AnalysisSession withResult(String resultJson, String resultSummary, String schemaVersion) {
        return new AnalysisSession(id, asyncJobId, clusterId, applicationId, namespace, status,
                aiProvider, aiModel, promptVersion, schemaVersion, locale, resultSummary, resultJson, createdBy, createdAt);
    }

    public UUID id() { return id; }
    public UUID asyncJobId() { return asyncJobId; }
    public UUID clusterId() { return clusterId; }
    public UUID applicationId() { return applicationId; }
    public String namespace() { return namespace; }
    public AnalysisStatus status() { return status; }
    public String aiProvider() { return aiProvider; }
    public String aiModel() { return aiModel; }
    public String promptVersion() { return promptVersion; }
    public String schemaVersion() { return schemaVersion; }
    public String locale() { return locale; }
    public String resultSummary() { return resultSummary; }
    public String resultJson() { return resultJson; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
}
