package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.AiChatContextSelection;
import io.strato.aiops.application.port.in.AiChatSendMessageResult;
import io.strato.aiops.application.port.in.AiChatUseCase;
import io.strato.aiops.application.port.in.CreateAiChatConversationCommand;
import io.strato.aiops.application.port.in.SendAiChatMessageCommand;
import io.strato.aiops.application.port.out.AiChatCompletion;
import io.strato.aiops.application.port.out.AiChatContextReferenceRepositoryPort;
import io.strato.aiops.application.port.out.AiChatConversationRepositoryPort;
import io.strato.aiops.application.port.out.AiChatMessageRepositoryPort;
import io.strato.aiops.application.port.out.AiChatPort;
import io.strato.aiops.application.port.out.AiChatPrompt;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesResourceManifest;
import io.strato.aiops.application.port.out.KubernetesResourceManifestPort;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnosticsPort;
import io.strato.aiops.application.port.out.KubernetesPodLogs;
import io.strato.aiops.application.port.out.KubernetesEventSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.ManagedApplicationRepositoryPort;
import io.strato.aiops.domain.application.ManagedApplication;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.chat.AiChatContextReference;
import io.strato.aiops.domain.chat.AiChatConversation;
import io.strato.aiops.domain.chat.AiChatMessage;
import io.strato.aiops.domain.chat.AiChatReferenceType;
import io.strato.aiops.domain.chat.AiChatMode;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class AiChatApplicationService implements AiChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiChatApplicationService.class);
    private static final int RECENT_CONVERSATION_LIMIT = 100;
    private static final int MESSAGE_LIMIT = 100;
    private static final int CONTEXT_RESOURCE_LIMIT = 100;
    private static final int CONTEXT_EVENT_LIMIT = 100;
    private static final int MAX_PROMPT_CONTEXT_CHARS = 24_000;
    private static final String PROMPT_VERSION = "ai-chat.v1";

    private final AiChatConversationRepositoryPort conversationRepositoryPort;
    private final AiChatMessageRepositoryPort messageRepositoryPort;
    private final AiChatContextReferenceRepositoryPort contextReferenceRepositoryPort;
    private final ClusterRepositoryPort clusterRepositoryPort;
    private final ManagedApplicationRepositoryPort applicationRepositoryPort;
    private final KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort;
    private final KubernetesEventSnapshotRepositoryPort eventSnapshotRepositoryPort;
    private final AiChatPort aiChatPort;
    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final ClusterCredentialRepositoryPort clusterCredentialRepositoryPort;
    private final SecretCryptoPort secretCryptoPort;
    private final KubernetesResourceManifestPort kubernetesResourceManifestPort;
    private final KubernetesNamespaceDiagnosticsPort kubernetesNamespaceDiagnosticsPort;
    private final TransactionTemplate transactionTemplate;

    public AiChatApplicationService(AiChatConversationRepositoryPort conversationRepositoryPort,
                                    AiChatMessageRepositoryPort messageRepositoryPort,
                                    AiChatContextReferenceRepositoryPort contextReferenceRepositoryPort,
                                    ClusterRepositoryPort clusterRepositoryPort,
                                    ManagedApplicationRepositoryPort applicationRepositoryPort,
                                    KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort,
                                    KubernetesEventSnapshotRepositoryPort eventSnapshotRepositoryPort,
                                    AiChatPort aiChatPort,
                                    AuditLogRepositoryPort auditLogRepositoryPort,
                                    ClusterCredentialRepositoryPort clusterCredentialRepositoryPort,
                                    SecretCryptoPort secretCryptoPort,
                                    KubernetesResourceManifestPort kubernetesResourceManifestPort,
                                    KubernetesNamespaceDiagnosticsPort kubernetesNamespaceDiagnosticsPort,
                                    PlatformTransactionManager transactionManager) {
        this.conversationRepositoryPort = conversationRepositoryPort;
        this.messageRepositoryPort = messageRepositoryPort;
        this.contextReferenceRepositoryPort = contextReferenceRepositoryPort;
        this.clusterRepositoryPort = clusterRepositoryPort;
        this.applicationRepositoryPort = applicationRepositoryPort;
        this.resourceSnapshotRepositoryPort = resourceSnapshotRepositoryPort;
        this.eventSnapshotRepositoryPort = eventSnapshotRepositoryPort;
        this.aiChatPort = aiChatPort;
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.clusterCredentialRepositoryPort = clusterCredentialRepositoryPort;
        this.secretCryptoPort = secretCryptoPort;
        this.kubernetesResourceManifestPort = kubernetesResourceManifestPort;
        this.kubernetesNamespaceDiagnosticsPort = kubernetesNamespaceDiagnosticsPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public AiChatConversation createConversation(CreateAiChatConversationCommand command, String actor, String requestId) {
        validateMode(command.mode(), command.clusterId(), command.applicationId());
        UUID clusterId = command.clusterId();
        String namespace = normalizeNamespace(command.namespace());
        if (command.applicationId() != null) {
            ManagedApplication application = requireApplication(command.applicationId());
            rejectMismatchedApplicationScope(clusterId, namespace, application);
            clusterId = application.clusterId();
            namespace = application.namespace();
        }
        if (clusterId != null) {
            requireCluster(clusterId);
        }
        String title = command.title() == null || command.title().isBlank() ? "New AI chat" : command.title();
        AiChatConversation conversation = conversationRepositoryPort.save(AiChatConversation.create(
                title, command.mode(), clusterId, namespace, command.applicationId(), actor));
        audit("AI_CHAT_CONVERSATION_CREATED", "AI_CHAT_CONVERSATION", conversation.id(), actor, requestId);
        return conversation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiChatConversation> listConversations(String actor, boolean archived) {
        return conversationRepositoryPort.findRecent(actor, archived, RECENT_CONVERSATION_LIMIT);
    }

    @Override
    @Transactional(readOnly = true)
    public AiChatConversation getConversation(UUID conversationId, String actor) {
        return requireConversation(conversationId, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiChatMessage> listMessages(UUID conversationId, String actor) {
        requireConversation(conversationId, actor);
        return messageRepositoryPort.findByConversationId(conversationId, MESSAGE_LIMIT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiChatContextReference> listContextReferences(UUID messageId, String actor) {
        AiChatMessage message = messageRepositoryPort.findById(messageId)
                .orElseThrow(() -> new NoSuchElementException("AI chat message not found: " + messageId));
        requireConversation(message.conversationId(), actor);
        return contextReferenceRepositoryPort.findByMessageId(messageId);
    }

    @Override
    @Transactional(readOnly = true)
    public AiChatConversation getConversationByMessage(UUID messageId, String actor) {
        AiChatMessage message = messageRepositoryPort.findById(messageId)
                .orElseThrow(() -> new NoSuchElementException("AI chat message not found: " + messageId));
        return requireConversation(message.conversationId(), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiChatContextReference> listConversationContextReferences(UUID conversationId, String actor) {
        requireConversation(conversationId, actor);
        return contextReferenceRepositoryPort.findByConversationId(conversationId);
    }

    @Override
    @Transactional
    public AiChatConversation updateConversation(UUID conversationId, String title, Boolean favorite, Boolean archived,
                                                 String actor, String requestId) {
        AiChatConversation updated = conversationRepositoryPort.save(
                requireConversation(conversationId, actor).update(title, favorite, archived));
        audit("AI_CHAT_CONVERSATION_UPDATED", "AI_CHAT_CONVERSATION", conversationId, actor, requestId);
        return updated;
    }

    @Override
    @Transactional
    public void deleteConversation(UUID conversationId, String actor, String requestId) {
        requireConversation(conversationId, actor);
        contextReferenceRepositoryPort.deleteByConversationId(conversationId);
        messageRepositoryPort.deleteByConversationId(conversationId);
        conversationRepositoryPort.deleteById(conversationId);
        audit("AI_CHAT_CONVERSATION_DELETED", "AI_CHAT_CONVERSATION", conversationId, actor, requestId);
    }

    @Override
    @Transactional
    public AiChatSendMessageResult sendMessage(SendAiChatMessageCommand command, String actor, String requestId) {
        long requestStartedAt = System.nanoTime();
        AiChatConversation conversation = requireConversation(command.conversationId(), actor);
        String userContent = sanitizeUserMessage(command.message());
        logRequestStarted(conversation, requestId, false);
        long contextStartedAt = System.nanoTime();
        AiChatContext context;
        try {
            context = buildContext(conversation, command.contextSelection(), command.locale(), userContent);
        } catch (RuntimeException exception) {
            logRequestFailed(conversation, requestId, 0, requestStartedAt, false, "context", exception);
            throw exception;
        }
        logContextReady(conversation, requestId, context, elapsedMs(contextStartedAt));

        try {
            AiChatCompletion completion = aiChatPort.complete(new AiChatPrompt(
                    conversation.id(), userContent, context.promptContext(), PROMPT_VERSION));
            long totalLatencyMs = elapsedMs(requestStartedAt);
            AiChatSendMessageResult result = persistCompletion(conversation, context, userContent, completion,
                    totalLatencyMs, actor, requestId);
            logRequestCompleted(conversation, requestId, context, completion, totalLatencyMs, false);
            return result;
        } catch (RuntimeException exception) {
            logRequestFailed(conversation, requestId, context.promptContext().length(), requestStartedAt,
                    false, "completion", exception);
            throw exception;
        }
    }

    @Override
    public AiChatSendMessageResult streamMessage(SendAiChatMessageCommand command, String actor, String requestId,
                                                 Consumer<String> onDelta) {
        long requestStartedAt = System.nanoTime();
        AiChatConversation conversation = requireConversation(command.conversationId(), actor);
        String userContent = sanitizeUserMessage(command.message());
        logRequestStarted(conversation, requestId, true);
        long contextStartedAt = System.nanoTime();
        AiChatContext context;
        try {
            context = buildContext(conversation, command.contextSelection(), command.locale(), userContent);
        } catch (RuntimeException exception) {
            logRequestFailed(conversation, requestId, 0, requestStartedAt, true, "context", exception);
            throw exception;
        }
        logContextReady(conversation, requestId, context, elapsedMs(contextStartedAt));
        StringBuilder partialContent = new StringBuilder();
        AtomicBoolean firstTokenLogged = new AtomicBoolean();
        try {
            AiChatCompletion completion = aiChatPort.stream(new AiChatPrompt(
                    conversation.id(), userContent, context.promptContext(), PROMPT_VERSION), delta -> {
                if (firstTokenLogged.compareAndSet(false, true)) {
                    log.info("event=ai_chat_first_token requestId={} conversationId={} elapsedMs={}",
                            requestId, conversation.id(), elapsedMs(requestStartedAt));
                }
                partialContent.append(delta);
                onDelta.accept(delta);
            });
            long totalLatencyMs = elapsedMs(requestStartedAt);
            AiChatSendMessageResult result = transactionTemplate.execute(status ->
                    persistCompletion(conversation, context, userContent, completion, totalLatencyMs,
                            actor, requestId));
            logRequestCompleted(conversation, requestId, context, completion, totalLatencyMs, true);
            return result;
        } catch (RuntimeException exception) {
            persistInterruptedTurn(conversation, context, userContent, partialContent.toString(), actor, requestId,
                    elapsedMs(requestStartedAt), exception);
            logRequestFailed(conversation, requestId, context.promptContext().length(), requestStartedAt,
                    true, "completion", exception);
            throw exception;
        }
    }

    private void persistInterruptedTurn(AiChatConversation conversation, AiChatContext context, String userContent,
                                        String partialContent, String actor, String requestId,
                                        long totalLatencyMs, RuntimeException originalException) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                messageRepositoryPort.save(AiChatMessage.user(conversation.id(), userContent, actor));
                AiChatMessage assistantMessage = messageRepositoryPort.save(
                        AiChatMessage.interruptedAssistant(conversation.id(), partialContent, PROMPT_VERSION,
                                totalLatencyMs, context.promptContext().length()));
                contextReferenceRepositoryPort.saveAll(context.references().stream()
                        .map(reference -> AiChatContextReference.create(assistantMessage.id(), reference.referenceType(),
                                reference.referenceId(), reference.label()))
                        .toList());
                audit("AI_CHAT_MESSAGE_STREAM_FAILED", "AI_CHAT_CONVERSATION", conversation.id(), actor, requestId);
            });
        } catch (RuntimeException persistenceException) {
            originalException.addSuppressed(persistenceException);
        }
    }

    private AiChatSendMessageResult persistCompletion(AiChatConversation conversation, AiChatContext context,
                                                      String userContent, AiChatCompletion completion,
                                                      long totalLatencyMs,
                                                      String actor, String requestId) {
        AiChatMessage userMessage = messageRepositoryPort.save(AiChatMessage.user(
                conversation.id(), userContent, actor));
        AiChatMessage assistantMessage = messageRepositoryPort.save(AiChatMessage.assistant(
                conversation.id(), completion.content(), completion.model(), PROMPT_VERSION,
                completion.finishReason(), completion.latencyMs(), completion.firstTokenLatencyMs(),
                totalLatencyMs, context.promptContext().length(), "ollama"));
        List<AiChatContextReference> references = contextReferenceRepositoryPort.saveAll(context.references().stream()
                .map(reference -> AiChatContextReference.create(assistantMessage.id(), reference.referenceType(), reference.referenceId(), reference.label()))
                .toList());

        audit("AI_CHAT_MESSAGE_SENT", "AI_CHAT_CONVERSATION", conversation.id(), actor, requestId);
        return new AiChatSendMessageResult(conversation.id(), userMessage, assistantMessage, references);
    }

    private void logRequestStarted(AiChatConversation conversation, String requestId, boolean streaming) {
        log.info("event=ai_chat_request_started requestId={} conversationId={} mode={} clusterId={} namespace={} streaming={}",
                requestId, conversation.id(), conversation.mode(), conversation.clusterId(), conversation.namespace(), streaming);
    }

    private void logContextReady(AiChatConversation conversation, String requestId, AiChatContext context,
                                 long contextBuildLatencyMs) {
        log.info("event=ai_chat_context_ready requestId={} conversationId={} contextChars={} contextBuildLatencyMs={} references={}",
                requestId, conversation.id(), context.promptContext().length(), contextBuildLatencyMs,
                context.references().size());
    }

    private void logRequestCompleted(AiChatConversation conversation, String requestId, AiChatContext context,
                                     AiChatCompletion completion, long totalLatencyMs, boolean streaming) {
        log.info("event=ai_chat_request_completed requestId={} conversationId={} model={} streaming={} firstTokenLatencyMs={} llmLatencyMs={} totalLatencyMs={} contextChars={} responseChars={}",
                requestId, conversation.id(), completion.model(), streaming, completion.firstTokenLatencyMs(),
                completion.latencyMs(), totalLatencyMs, context.promptContext().length(), completion.content().length());
    }

    private void logRequestFailed(AiChatConversation conversation, String requestId, int contextChars,
                                  long requestStartedAt, boolean streaming, String phase,
                                  RuntimeException exception) {
        log.warn("event=ai_chat_request_failed requestId={} conversationId={} streaming={} phase={} totalLatencyMs={} contextChars={} exception={}",
                requestId, conversation.id(), streaming, phase, elapsedMs(requestStartedAt), contextChars,
                exception.getClass().getSimpleName());
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private AiChatContext buildContext(AiChatConversation conversation, AiChatContextSelection selection,
                                       io.strato.aiops.domain.analysis.SupportedLocale locale, String userContent) {
        UUID clusterId = selection != null && selection.clusterId() != null ? selection.clusterId() : conversation.clusterId();
        String namespace = selection != null && selection.namespace() != null ? selection.namespace() : conversation.namespace();
        UUID applicationId = selection != null && selection.applicationId() != null ? selection.applicationId() : conversation.applicationId();
        boolean includeRecentEvents = selection == null || selection.includeRecentEvents();

        List<PendingReference> references = new ArrayList<>();
        StringBuilder context = new StringBuilder(8192);
        context.append("mode=").append(conversation.mode()).append('\n');
        context.append(conversation.mode() == AiChatMode.GENERAL
                ? "You are a general-purpose AI assistant. Provider is Ollama only.\n"
                : "You are a Kubernetes operations assistant. Answer only from the supplied evidence and clearly separate facts from inference.\n");
        context.append("Never claim that you executed kubectl or helm commands. Provide recommendations only.\n");
        context.append("Respond in ").append(locale.responseLanguage()).append(". ")
                .append("Preserve Kubernetes resource names, logs, YAML, JSON keys, commands, IDs, and enum codes exactly.\n");

        if (conversation.mode() == AiChatMode.GENERAL) {
            return new AiChatContext(truncate(context.toString(), MAX_PROMPT_CONTEXT_CHARS), references);
        }

        if (applicationId != null) {
            ManagedApplication application = requireApplication(applicationId);
            rejectMismatchedApplicationScope(clusterId, namespace, application);
            clusterId = application.clusterId();
            namespace = application.namespace();
            context.append("application=")
                    .append(application.name())
                    .append(" deploymentType=").append(application.deploymentType())
                    .append(" status=").append(application.status())
                    .append('\n');
            references.add(new PendingReference(AiChatReferenceType.APPLICATION, application.id(), "Application " + application.name()));
        }

        if (clusterId != null) {
            Cluster cluster = requireCluster(clusterId);
            context.append("cluster=")
                    .append(cluster.name())
                    .append(" environment=").append(cluster.environment())
                    .append(" provider=").append(cluster.provider())
                    .append(" region=").append(cluster.region())
                    .append('\n');
            references.add(new PendingReference(AiChatReferenceType.CLUSTER, cluster.id(), "Cluster " + cluster.name()));
        }
        if (namespace != null && !namespace.isBlank()) {
            context.append("namespace=").append(namespace).append('\n');
            references.add(new PendingReference(AiChatReferenceType.NAMESPACE, null, "Namespace " + namespace));
        }

        if (clusterId != null) {
            appendKubernetesEvidence(context, references, clusterId, namespace, selection, userContent, includeRecentEvents);
        }
        return new AiChatContext(truncate(context.toString(), MAX_PROMPT_CONTEXT_CHARS), references);
    }

    private void appendKubernetesEvidence(StringBuilder context, List<PendingReference> references, UUID clusterId,
                                          String namespace, AiChatContextSelection selection, String userContent,
                                          boolean includeRecentEvents) {
        List<KubernetesResourceSnapshot> resources = resourceSnapshotRepositoryPort.findLatest(
                clusterId, namespace, null, CONTEXT_RESOURCE_LIMIT);
        ResourceTarget target = resolveTarget(selection, userContent, resources);
        validateResourceScope(namespace, target);
        context.append("kubernetesEvidence:\n");

        if (target != null) {
            boolean liveAdded = false;
            try {
                KubernetesResourceManifest manifest = kubernetesResourceManifestPort.getAiSafeResourceManifest(
                        connectionCredential(clusterId), namespace, target.resourceType(), target.resourceName());
                context.append("- source=LIVE resource=").append(manifest.resourceType()).append('/')
                        .append(manifest.resourceName()).append(" collectedAt=").append(manifest.collectedAt())
                        .append(" secretRedacted=").append(manifest.secretRedacted()).append('\n')
                        .append(truncate(manifest.manifestYaml(), 10000)).append('\n');
                references.add(new PendingReference(AiChatReferenceType.LIVE_RESOURCE, null,
                        "LIVE " + manifest.resourceType() + "/" + manifest.resourceName() + " at " + manifest.collectedAt()));
                liveAdded = true;
            } catch (RuntimeException exception) {
                context.append("- source=LIVE status=UNAVAILABLE resource=").append(target.resourceType()).append('/')
                        .append(target.resourceName()).append(" reason=").append(truncate(exception.getMessage(), 300)).append('\n');
            }
            if (!liveAdded) {
                resources.stream().filter(resource -> sameTarget(resource, target)).findFirst()
                        .ifPresent(resource -> appendResourceSnapshot(context, references, resource));
            }
            if (selection != null && selection.includeLogs()) {
                appendLiveLogs(context, references, clusterId, namespace, target, selection.logLineLimit());
            }
        } else if (namespace != null && !namespace.isBlank()) {
            appendLiveNamespaceEvidence(context, references, clusterId, namespace, resources, includeRecentEvents,
                    selection != null && selection.includeLogs());
        } else {
            resources.stream().limit(40).forEach(resource -> appendResourceSnapshot(context, references, resource));
        }

        if (includeRecentEvents && (target != null || namespace == null || namespace.isBlank())) {
            eventSnapshotRepositoryPort.findLatest(clusterId, namespace, CONTEXT_EVENT_LIMIT).stream()
                    .filter(event -> target == null || target.resourceName().equals(event.involvedName()))
                    .limit(30)
                    .forEach(event -> {
                        context.append("- source=SNAPSHOT event=").append(event.type()).append(' ')
                                .append(event.reason()).append(' ').append(event.involvedKind()).append('/')
                                .append(event.involvedName()).append(" message=").append(truncate(event.message(), 500)).append('\n');
                        references.add(new PendingReference(AiChatReferenceType.EVENT_SNAPSHOT, event.id(),
                                "Event " + nullToEmpty(event.reason()) + " " + nullToEmpty(event.involvedName())));
                    });
        }
    }

    private void appendLiveNamespaceEvidence(StringBuilder context, List<PendingReference> references, UUID clusterId,
                                             String namespace, List<KubernetesResourceSnapshot> snapshots,
                                             boolean includeRecentEvents, boolean includeLogs) {
        try {
            var diagnostics = kubernetesNamespaceDiagnosticsPort.collectNamespaceDiagnostics(
                    connectionCredential(clusterId), namespace, includeLogs);
            context.append("- source=LIVE_NAMESPACE collectedAt=").append(diagnostics.collectedAt()).append('\n');
            diagnostics.resources().stream()
                    .sorted(Comparator.comparingInt(this::diagnosticResourcePriority).reversed())
                    .limit(15).forEach(resource -> {
                context.append("- source=LIVE resource=").append(resource.resourceType()).append('/')
                        .append(resource.resourceName()).append(" status=").append(resource.status())
                        .append(" summary=").append(truncate(resource.summaryJson(), 700)).append('\n');
                references.add(new PendingReference(AiChatReferenceType.LIVE_RESOURCE, null,
                        "LIVE " + resource.resourceType() + "/" + resource.resourceName() + " at " + diagnostics.collectedAt()));
            });
            if (includeRecentEvents) {
                diagnostics.events().stream()
                        .sorted(Comparator.comparingInt(this::diagnosticEventPriority).reversed())
                        .limit(15).forEach(event -> {
                    context.append("- source=LIVE_EVENT event=").append(event.type()).append(' ')
                            .append(event.reason()).append(' ').append(event.involvedKind()).append('/')
                            .append(event.involvedName()).append(" count=").append(event.count())
                            .append(" message=").append(truncate(event.message(), 400)).append('\n');
                    references.add(new PendingReference(AiChatReferenceType.LIVE_EVENT, null,
                            "LIVE event " + nullToEmpty(event.reason()) + " " + nullToEmpty(event.involvedName())));
                });
            }
            if (includeLogs) {
                diagnostics.podLogs().stream().limit(4).forEach(log -> {
                    context.append("- source=LIVE_LOG pod=").append(log.podName()).append(" container=")
                            .append(log.containerName()).append(" log=").append(truncate(log.log(), 1200)).append('\n');
                    references.add(new PendingReference(AiChatReferenceType.LIVE_LOG, null,
                            "LIVE log " + log.podName() + "/" + log.containerName() + " at " + diagnostics.collectedAt()));
                });
            }
        } catch (RuntimeException exception) {
            context.append("- source=LIVE_NAMESPACE status=UNAVAILABLE reason=")
                    .append(truncate(exception.getMessage(), 300)).append('\n');
            snapshots.stream().limit(15).forEach(resource -> appendResourceSnapshot(context, references, resource));
        }
    }

    private void appendLiveLogs(StringBuilder context, List<PendingReference> references, UUID clusterId,
                                String namespace, ResourceTarget target, int requestedLines) {
        int lines = Math.max(10, Math.min(requestedLines <= 0 ? 80 : requestedLines, 200));
        try {
            KubernetesPodLogs logs = kubernetesNamespaceDiagnosticsPort.collectResourceLogs(connectionCredential(clusterId),
                    namespace, target.resourceType(), target.resourceName(), null, lines, false);
            logs.containers().stream().limit(4).forEach(log -> {
                context.append("- source=LIVE_LOG pod=").append(logs.podName()).append(" container=")
                        .append(log.containerName()).append(" log=").append(truncate(log.log(), 3000)).append('\n');
                references.add(new PendingReference(AiChatReferenceType.LIVE_LOG, null,
                        "LIVE log " + logs.podName() + "/" + log.containerName() + " at " + logs.collectedAt()));
            });
        } catch (RuntimeException exception) {
            context.append("- source=LIVE_LOG status=UNAVAILABLE resource=").append(target.resourceType()).append('/')
                    .append(target.resourceName()).append(" reason=").append(truncate(exception.getMessage(), 300)).append('\n');
        }
    }

    private int diagnosticResourcePriority(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        String status = nullToEmpty(resource.status()).toLowerCase();
        if (status.contains("fail") || status.contains("error") || status.contains("crash")
                || status.contains("unavailable")) return 4;
        if (status.contains("pending") || status.contains("unknown") || status.contains("terminating")) return 3;
        if (status.contains("false") || status.contains("degraded") || status.contains("warning")) return 2;
        return 1;
    }

    private int diagnosticEventPriority(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        int typeWeight = "warning".equalsIgnoreCase(event.type()) ? 100_000 : 0;
        return typeWeight + Math.max(0, event.count() == null ? 0 : event.count());
    }

    private ResourceTarget resolveTarget(AiChatContextSelection selection, String userContent,
                                         List<KubernetesResourceSnapshot> resources) {
        if (selection != null && selection.resourceName() != null && !selection.resourceName().isBlank()) {
            String type = selection.resourceType();
            if (type == null || type.isBlank()) {
                type = resources.stream().filter(resource -> resource.resourceName().equals(selection.resourceName()))
                        .map(KubernetesResourceSnapshot::resourceType).findFirst().orElse("Pod");
            }
            return new ResourceTarget(type, selection.resourceName().trim());
        }
        return resources.stream()
                .filter(resource -> userContent.contains(resource.resourceName()))
                .sorted(Comparator.comparingInt((KubernetesResourceSnapshot resource) -> resource.resourceName().length()).reversed())
                .map(resource -> new ResourceTarget(resource.resourceType(), resource.resourceName()))
                .findFirst().orElse(null);
    }

    private boolean sameTarget(KubernetesResourceSnapshot resource, ResourceTarget target) {
        return resource.resourceName().equals(target.resourceName())
                && resource.resourceType().equalsIgnoreCase(target.resourceType());
    }

    private void validateResourceScope(String namespace, ResourceTarget target) {
        if (target == null || namespace == null || namespace.isBlank()) {
            return;
        }
        String type = target.resourceType().replace("-", "").replace("_", "").toLowerCase();
        if (type.equals("node") || type.equals("nodes")
                || type.equals("persistentvolume") || type.equals("persistentvolumes") || type.equals("pv")
                || type.equals("namespace") || type.equals("namespaces") || type.equals("ns")) {
            throw new IllegalArgumentException(target.resourceType() + " is cluster-scoped and cannot be requested inside a namespace context");
        }
    }

    private void rejectMismatchedApplicationScope(UUID clusterId, String namespace, ManagedApplication application) {
        if (clusterId != null && !clusterId.equals(application.clusterId())) {
            throw new IllegalArgumentException("applicationId does not belong to the selected cluster");
        }
        if (namespace != null && !namespace.equals(application.namespace())) {
            throw new IllegalArgumentException("applicationId does not belong to the selected namespace");
        }
    }

    private String normalizeNamespace(String namespace) {
        return namespace == null || namespace.isBlank() ? null : namespace.trim();
    }

    private void appendResourceSnapshot(StringBuilder context, List<PendingReference> references,
                                        KubernetesResourceSnapshot resource) {
        context.append("- source=SNAPSHOT resource=").append(resource.resourceType()).append('/')
                .append(resource.resourceName()).append(" status=").append(resource.status())
                .append(" collectedAt=").append(resource.collectedAt())
                .append(" summary=").append(truncate(resource.summaryJson(), 1200)).append('\n');
        references.add(new PendingReference(AiChatReferenceType.RESOURCE_SNAPSHOT, resource.id(),
                resource.resourceType() + "/" + resource.resourceName() + " at " + resource.collectedAt()));
    }

    private KubernetesConnectionCredential connectionCredential(UUID clusterId) {
        EncryptedClusterCredential credential = clusterCredentialRepositoryPort.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));
        String payload = secretCryptoPort.decrypt(new EncryptedSecret(credential.encryptedPayload(), credential.keyId(),
                credential.algorithm(), credential.nonce()));
        return new KubernetesConnectionCredential(credential.credentialType(), payload);
    }

    private AiChatConversation requireConversation(UUID conversationId, String actor) {
        return conversationRepositoryPort.findByIdAndCreatedBy(conversationId, actor)
                .orElseThrow(() -> new NoSuchElementException("AI chat conversation not found: " + conversationId));
    }

    private void validateMode(AiChatMode mode, UUID clusterId, UUID applicationId) {
        if (mode == AiChatMode.CLUSTER && clusterId == null && applicationId == null) {
            throw new IllegalArgumentException("cluster mode requires clusterId or applicationId");
        }
        if (mode == AiChatMode.GENERAL && (clusterId != null || applicationId != null)) {
            throw new IllegalArgumentException("general mode cannot include cluster or application context");
        }
    }

    private Cluster requireCluster(UUID clusterId) {
        return clusterRepositoryPort.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    private ManagedApplication requireApplication(UUID applicationId) {
        return applicationRepositoryPort.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + applicationId));
    }

    private String sanitizeUserMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (message.length() > 4000) {
            throw new IllegalArgumentException("message must be 4000 characters or fewer");
        }
        return message;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void audit(String action, String targetType, UUID targetId, String actor, String requestId) {
        auditLogRepositoryPort.save(AuditLog.create(action, targetType, targetId.toString(), actor, requestId));
    }

    private record AiChatContext(String promptContext, List<PendingReference> references) {
    }

    private record PendingReference(AiChatReferenceType referenceType, UUID referenceId, String label) {
    }

    private record ResourceTarget(String resourceType, String resourceName) {
    }
}
