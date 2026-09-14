package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.dto.AiChatContextReferenceResponse;
import io.strato.aiops.adapter.in.web.dto.AiChatConversationResponse;
import io.strato.aiops.adapter.in.web.dto.AiChatMessageResponse;
import io.strato.aiops.adapter.in.web.dto.AiChatSendMessageResponse;
import io.strato.aiops.adapter.in.web.dto.CreateAiChatConversationRequest;
import io.strato.aiops.adapter.in.web.dto.SendAiChatMessageRequest;
import io.strato.aiops.adapter.in.web.dto.UpdateAiChatConversationRequest;
import io.strato.aiops.application.port.in.AiChatUseCase;
import io.strato.aiops.application.port.in.ApplicationUseCase;
import io.strato.aiops.application.port.in.SendAiChatMessageCommand;
import io.strato.aiops.domain.application.ManagedApplication;
import io.strato.aiops.domain.analysis.SupportedLocale;
import io.strato.aiops.domain.chat.AiChatConversation;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.adapter.in.web.security.ApiAuthorizationInterceptor;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import org.springframework.security.access.AccessDeniedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;
import java.util.UUID;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Tag(name = "AI Chat", description = "Ollama-backed AI chat assistant APIs")
@RestController
@RequestMapping("/api/ai-chat")
public class AiChatController {

    private final AiChatUseCase aiChatUseCase;
    private final ApplicationUseCase applicationUseCase;
    private final IdentityAccessService identityAccessService;
    private final TaskScheduler heartbeatScheduler;
    private final long heartbeatMs;

    public AiChatController(AiChatUseCase aiChatUseCase, ApplicationUseCase applicationUseCase,
                            IdentityAccessService identityAccessService,
                            @Qualifier("aiChatHeartbeatScheduler") TaskScheduler heartbeatScheduler,
                            @Value("${aiops.ai.chat-stream.heartbeat-ms:15000}") long heartbeatMs) {
        this.aiChatUseCase = aiChatUseCase;
        this.applicationUseCase = applicationUseCase;
        this.identityAccessService = identityAccessService;
        this.heartbeatScheduler = heartbeatScheduler;
        this.heartbeatMs = Math.max(1, heartbeatMs);
    }

    @Operation(summary = "Create AI chat conversation")
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public AiChatConversationResponse createConversation(@Valid @RequestBody CreateAiChatConversationRequest request,
                                                         HttpServletRequest servletRequest) {
        TargetScope scope = resolveTargetScope(request.clusterId(), request.namespace(), request.applicationId());
        authorizeTarget(servletRequest, scope.clusterId(), scope.namespace(), Capability.ANALYSIS_RUN);
        return AiChatConversationResponse.from(aiChatUseCase.createConversation(
                request.toCommand(), actor(servletRequest), requestId(servletRequest)));
    }

    @Operation(summary = "List recent AI chat conversations")
    @GetMapping("/conversations")
    public List<AiChatConversationResponse> listConversations(
            @RequestParam(defaultValue = "false") boolean archived, HttpServletRequest request) {
        return aiChatUseCase.listConversations(actor(request), archived).stream()
                .filter(conversation -> canAccessConversation(request, conversation))
                .map(AiChatConversationResponse::from).toList();
    }

    @Operation(summary = "Get AI chat conversation")
    @GetMapping("/conversations/{conversationId}")
    public AiChatConversationResponse getConversation(@PathVariable UUID conversationId, HttpServletRequest request) {
        AiChatConversation conversation = ownedConversation(conversationId, request);
        authorizeConversation(request, conversation, Capability.ANALYSIS_READ);
        return AiChatConversationResponse.from(conversation);
    }

    @Operation(summary = "List AI chat messages")
    @GetMapping("/conversations/{conversationId}/messages")
    public List<AiChatMessageResponse> listMessages(@PathVariable UUID conversationId, HttpServletRequest request) {
        AiChatConversation conversation = ownedConversation(conversationId, request);
        authorizeConversation(request, conversation, Capability.ANALYSIS_READ);
        return aiChatUseCase.listMessages(conversationId, actor(request)).stream().map(AiChatMessageResponse::from).toList();
    }

    @Operation(summary = "Send AI chat message")
    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public AiChatSendMessageResponse sendMessage(@PathVariable UUID conversationId,
                                                 @Valid @RequestBody SendAiChatMessageRequest request,
                                                 HttpServletRequest servletRequest) {
        AiChatConversation conversation = ownedConversation(conversationId, servletRequest);
        authorizeConversation(servletRequest, conversation, Capability.ANALYSIS_RUN);
        UUID requestedCluster = request.contextSelection() != null && request.contextSelection().clusterId() != null
                ? request.contextSelection().clusterId() : conversation.clusterId();
        String requestedNamespace = request.contextSelection() != null && request.contextSelection().namespace() != null
                ? request.contextSelection().namespace() : conversation.namespace();
        UUID requestedApplication = request.contextSelection() != null && request.contextSelection().applicationId() != null
                ? request.contextSelection().applicationId() : conversation.applicationId();
        TargetScope requestedScope = resolveTargetScope(requestedCluster, requestedNamespace, requestedApplication);
        authorizeTarget(servletRequest, requestedScope.clusterId(), requestedScope.namespace(), Capability.ANALYSIS_RUN);
        SendAiChatMessageCommand command = request.toCommand(conversationId);
        return AiChatSendMessageResponse.from(aiChatUseCase.sendMessage(
                new SendAiChatMessageCommand(command.conversationId(), command.message(), command.contextSelection(),
                        SupportedLocale.fromAcceptLanguage(servletRequest.getHeader("Accept-Language"))),
                actor(servletRequest), requestId(servletRequest)));
    }

    @Operation(summary = "Stream an AI chat response using server-sent events")
    @PostMapping(value = "/conversations/{conversationId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamMessage(@PathVariable UUID conversationId,
                                               @Valid @RequestBody SendAiChatMessageRequest request,
                                               HttpServletRequest servletRequest) {
        AiChatConversation conversation = ownedConversation(conversationId, servletRequest);
        authorizeConversation(servletRequest, conversation, Capability.ANALYSIS_RUN);
        UUID requestedCluster = request.contextSelection() != null && request.contextSelection().clusterId() != null
                ? request.contextSelection().clusterId() : conversation.clusterId();
        String requestedNamespace = request.contextSelection() != null && request.contextSelection().namespace() != null
                ? request.contextSelection().namespace() : conversation.namespace();
        UUID requestedApplication = request.contextSelection() != null && request.contextSelection().applicationId() != null
                ? request.contextSelection().applicationId() : conversation.applicationId();
        TargetScope requestedScope = resolveTargetScope(requestedCluster, requestedNamespace, requestedApplication);
        authorizeTarget(servletRequest, requestedScope.clusterId(), requestedScope.namespace(), Capability.ANALYSIS_RUN);
        SendAiChatMessageCommand command = request.toCommand(conversationId);
        SendAiChatMessageCommand localized = new SendAiChatMessageCommand(command.conversationId(), command.message(),
                command.contextSelection(), SupportedLocale.fromAcceptLanguage(servletRequest.getHeader("Accept-Language")));
        String actor = actor(servletRequest);
        String requestId = requestId(servletRequest);
        StreamingResponseBody body = outputStream -> {
            AiChatSseWriter writer = new AiChatSseWriter(outputStream);
            ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(writer::writeHeartbeat,
                    Instant.now().plusMillis(heartbeatMs), Duration.ofMillis(heartbeatMs));
            try {
                aiChatUseCase.streamMessage(localized, actor, requestId,
                        delta -> writer.writeEvent("delta", delta));
                writer.writeEvent("done", "[DONE]");
            } catch (UncheckedIOException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                writer.writeEvent("error", "AI response stream was interrupted");
            } finally {
                if (heartbeat != null) {
                    heartbeat.cancel(false);
                }
            }
        };
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(body);
    }

    @Operation(summary = "List AI chat context references for a message")
    @GetMapping("/messages/{messageId}/context-references")
    public List<AiChatContextReferenceResponse> listContextReferences(@PathVariable UUID messageId, HttpServletRequest request) {
        AiChatConversation conversation = aiChatUseCase.getConversationByMessage(messageId, actor(request));
        authorizeConversation(request, conversation, Capability.ANALYSIS_READ);
        return aiChatUseCase.listContextReferences(messageId, actor(request)).stream().map(AiChatContextReferenceResponse::from).toList();
    }

    @Operation(summary = "List all AI chat context references for a conversation")
    @GetMapping("/conversations/{conversationId}/context-references")
    public List<AiChatContextReferenceResponse> listConversationContextReferences(
            @PathVariable UUID conversationId, HttpServletRequest request) {
        AiChatConversation conversation = ownedConversation(conversationId, request);
        authorizeConversation(request, conversation, Capability.ANALYSIS_READ);
        return aiChatUseCase.listConversationContextReferences(conversationId, actor(request)).stream()
                .map(AiChatContextReferenceResponse::from).toList();
    }

    @Operation(summary = "Update AI chat conversation title, favorite, or archive state")
    @PatchMapping("/conversations/{conversationId}")
    public AiChatConversationResponse updateConversation(@PathVariable UUID conversationId,
                                                         @Valid @RequestBody UpdateAiChatConversationRequest request,
                                                         HttpServletRequest servletRequest) {
        AiChatConversation conversation = ownedConversation(conversationId, servletRequest);
        authorizeConversation(servletRequest, conversation, Capability.ANALYSIS_RUN);
        return AiChatConversationResponse.from(aiChatUseCase.updateConversation(conversationId, request.title(),
                request.favorite(), request.archived(), actor(servletRequest), requestId(servletRequest)));
    }

    @Operation(summary = "Permanently delete an AI chat conversation and its messages")
    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable UUID conversationId, HttpServletRequest servletRequest) {
        AiChatConversation conversation = ownedConversation(conversationId, servletRequest);
        authorizeConversation(servletRequest, conversation, Capability.ANALYSIS_RUN);
        aiChatUseCase.deleteConversation(conversationId, actor(servletRequest), requestId(servletRequest));
    }

    private AiChatConversation ownedConversation(UUID conversationId, HttpServletRequest request) {
        return aiChatUseCase.getConversation(conversationId, actor(request));
    }

    private void authorizeConversation(HttpServletRequest request, AiChatConversation conversation, Capability capability) {
        TargetScope scope = resolveTargetScope(conversation.clusterId(), conversation.namespace(), conversation.applicationId());
        authorizeTarget(request, scope.clusterId(), scope.namespace(), capability);
    }

    private TargetScope resolveTargetScope(UUID clusterId, String namespace, UUID applicationId) {
        if (applicationId == null) {
            return new TargetScope(clusterId, normalizeNamespace(namespace));
        }
        ManagedApplication application = applicationUseCase.getApplication(applicationId);
        String normalizedNamespace = normalizeNamespace(namespace);
        if (clusterId != null && !clusterId.equals(application.clusterId())) {
            throw new IllegalArgumentException("applicationId does not belong to the selected cluster");
        }
        if (normalizedNamespace != null && !normalizedNamespace.equals(application.namespace())) {
            throw new IllegalArgumentException("applicationId does not belong to the selected namespace");
        }
        return new TargetScope(application.clusterId(), application.namespace());
    }

    private String normalizeNamespace(String namespace) {
        return namespace == null || namespace.isBlank() ? null : namespace.trim();
    }

    private void authorizeTarget(HttpServletRequest request, UUID clusterId, String namespace) {
        authorizeTarget(request, clusterId, namespace, Capability.ANALYSIS_READ);
    }

    private void authorizeTarget(HttpServletRequest request, UUID clusterId, String namespace, Capability capability) {
        ResolvedAccess access = (ResolvedAccess) request.getAttribute(ApiAuthorizationInterceptor.RESOLVED_ACCESS_ATTRIBUTE);
        boolean allowed = access == null || (clusterId == null
                ? identityAccessService.hasAccessAtAnyScope(access, capability)
                : identityAccessService.allows(access, capability, clusterId, namespace));
        if (!allowed) {
            throw new AccessDeniedException("AI chat access is not granted for this cluster scope");
        }
    }

    private boolean canAccess(HttpServletRequest request, UUID clusterId, String namespace) {
        ResolvedAccess access = (ResolvedAccess) request.getAttribute(ApiAuthorizationInterceptor.RESOLVED_ACCESS_ATTRIBUTE);
        return access == null || (clusterId == null
                ? identityAccessService.hasAccessAtAnyScope(access, Capability.ANALYSIS_READ)
                : identityAccessService.allows(access, Capability.ANALYSIS_READ, clusterId, namespace));
    }

    private boolean canAccessConversation(HttpServletRequest request, AiChatConversation conversation) {
        try {
            TargetScope scope = resolveTargetScope(conversation.clusterId(), conversation.namespace(), conversation.applicationId());
            return canAccess(request, scope.clusterId(), scope.namespace());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
    }

    private record TargetScope(UUID clusterId, String namespace) {
    }
}
