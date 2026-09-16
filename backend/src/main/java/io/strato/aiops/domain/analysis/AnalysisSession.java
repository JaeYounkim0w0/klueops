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

    /** AnalysisSession 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalysisSession(UUID id, UUID clusterId, UUID applicationId, String namespace, AnalysisStatus status,
                           String aiProvider, String aiModel, String promptVersion, String schemaVersion,
                           String resultSummary, String resultJson, String createdBy, Instant createdAt) {
        this(id, null, clusterId, applicationId, namespace, status, aiProvider, aiModel, promptVersion, schemaVersion,
                SupportedLocale.ENGLISH.tag(), resultSummary, resultJson, createdBy, createdAt);
    }

    /** AnalysisSession 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** AnalysisSession 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** AnalysisSession의 running 처리의 핵심 작업 흐름을 실행한다. */
    public static AnalysisSession running(UUID asyncJobId, UUID clusterId, UUID applicationId, String namespace, String actor) {
        return running(asyncJobId, clusterId, applicationId, namespace, SupportedLocale.ENGLISH, actor);
    }

    /** AnalysisSession의 running 처리의 핵심 작업 흐름을 실행한다. */
    public static AnalysisSession running(UUID asyncJobId, UUID clusterId, UUID applicationId, String namespace,
                                          SupportedLocale locale, String actor) {
        return new AnalysisSession(UUID.randomUUID(), asyncJobId, clusterId, applicationId, namespace, AnalysisStatus.RUNNING,
                "ollama", "configured-model", "v1", "analysis-result.v1", locale.tag(),
                "AI analysis is running", null, actor, Instant.now());
    }

    /** AnalysisSession의 succeeded 처리에 필요한 업무 로직을 수행한다. */
    public static AnalysisSession succeeded(UUID clusterId, UUID applicationId, String namespace, String resultJson, String actor) {
        return succeeded(clusterId, applicationId, namespace, resultJson, "AI analysis completed", "analysis-result.v1", actor);
    }

    /** AnalysisSession의 succeeded 처리에 필요한 업무 로직을 수행한다. */
    public static AnalysisSession succeeded(UUID clusterId, UUID applicationId, String namespace, String resultJson,
                                            String resultSummary, String schemaVersion, String actor) {
        return succeeded(clusterId, applicationId, namespace, resultJson, resultSummary, schemaVersion,
                SupportedLocale.ENGLISH, actor);
    }

    /** AnalysisSession의 succeeded 처리에 필요한 업무 로직을 수행한다. */
    public static AnalysisSession succeeded(UUID clusterId, UUID applicationId, String namespace, String resultJson,
                                            String resultSummary, String schemaVersion, SupportedLocale locale,
                                            String actor) {
        return new AnalysisSession(UUID.randomUUID(), null, clusterId, applicationId, namespace, AnalysisStatus.SUCCEEDED,
                "ollama", "configured-model", "v1", schemaVersion, locale.tag(), resultSummary, resultJson,
                actor, Instant.now());
    }

    /** AnalysisSession의 succeeded 처리에 필요한 업무 로직을 수행한다. */
    public AnalysisSession succeeded(String resultJson, String resultSummary, String schemaVersion) {
        return new AnalysisSession(id, asyncJobId, clusterId, applicationId, namespace, AnalysisStatus.SUCCEEDED,
                aiProvider, aiModel, promptVersion, schemaVersion, locale, resultSummary, resultJson, createdBy, createdAt);
    }

    /** AnalysisSession의 failed 처리에 필요한 업무 로직을 수행한다. */
    public AnalysisSession failed(String resultJson, String resultSummary) {
        return new AnalysisSession(id, asyncJobId, clusterId, applicationId, namespace, AnalysisStatus.FAILED,
                aiProvider, aiModel, promptVersion, schemaVersion, locale, resultSummary, resultJson, createdBy, createdAt);
    }

    /** AnalysisSession의 withResult 처리에 필요한 업무 로직을 수행한다. */
    public AnalysisSession withResult(String resultJson, String resultSummary, String schemaVersion) {
        return new AnalysisSession(id, asyncJobId, clusterId, applicationId, namespace, status,
                aiProvider, aiModel, promptVersion, schemaVersion, locale, resultSummary, resultJson, createdBy, createdAt);
    }

    /** AnalysisSession의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() { return id; }
    /** AnalysisSession의 asyncJobId 처리에 필요한 업무 로직을 수행한다. */
    public UUID asyncJobId() { return asyncJobId; }
    /** AnalysisSession의 clusterId 처리에 필요한 업무 로직을 수행한다. */
    public UUID clusterId() { return clusterId; }
    /** AnalysisSession의 applicationId 처리에 필요한 업무 로직을 수행한다. */
    public UUID applicationId() { return applicationId; }
    /** AnalysisSession의 namespace 처리에 필요한 업무 로직을 수행한다. */
    public String namespace() { return namespace; }
    /** AnalysisSession의 status 처리에 필요한 업무 로직을 수행한다. */
    public AnalysisStatus status() { return status; }
    /** AnalysisSession의 aiProvider 처리에 필요한 업무 로직을 수행한다. */
    public String aiProvider() { return aiProvider; }
    /** AnalysisSession의 aiModel 처리에 필요한 업무 로직을 수행한다. */
    public String aiModel() { return aiModel; }
    /** AnalysisSession의 promptVersion 처리에 필요한 업무 로직을 수행한다. */
    public String promptVersion() { return promptVersion; }
    /** AnalysisSession의 schemaVersion 처리에 필요한 업무 로직을 수행한다. */
    public String schemaVersion() { return schemaVersion; }
    /** AnalysisSession의 locale 처리에 필요한 업무 로직을 수행한다. */
    public String locale() { return locale; }
    /** AnalysisSession의 resultSummary 처리에 필요한 업무 로직을 수행한다. */
    public String resultSummary() { return resultSummary; }
    /** AnalysisSession의 resultJson 처리에 필요한 업무 로직을 수행한다. */
    public String resultJson() { return resultJson; }
    /** AnalysisSession의 createdBy 처리에 필요한 데이터를 생성하거나 저장한다. */
    public String createdBy() { return createdBy; }
    /** AnalysisSession의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() { return createdAt; }
}
