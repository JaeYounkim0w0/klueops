package io.strato.aiops.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import io.strato.aiops.application.port.out.AiChatCompletion;
import io.strato.aiops.application.port.out.AiChatPort;
import io.strato.aiops.application.port.out.AiChatPrompt;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnosticsPort;
import io.strato.aiops.application.port.out.KubernetesPodLogs;
import io.strato.aiops.application.port.out.KubernetesResourceManifest;
import io.strato.aiops.application.port.out.KubernetesResourceManifestPort;
import io.strato.aiops.application.port.in.AiChatUseCase;
import io.strato.aiops.application.port.in.CreateAiChatConversationCommand;
import io.strato.aiops.domain.chat.AiChatMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.List;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(properties = "aiops.ai.chat-stream.heartbeat-ms=5")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AiChatApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AtomicReference<AiChatPrompt> capturedPrompt;

    @Autowired
    private AiChatUseCase aiChatUseCase;

    @Autowired
    private Environment environment;

    /** AiChatApiTest의 createsConversationAndSendsOllamaBackedMessage 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Test
    void createsConversationAndSendsOllamaBackedMessage() throws Exception {
        String clusterId = registerCluster("ai-chat-cluster-" + UUID.randomUUID());
        String conversationId = createConversation(clusterId);

        String response = mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "en-US")
                        .content("""
                                {
                                  "message": "default namespace 상태를 요약해줘",
                                  "contextSelection": {
                                    "clusterId": "%s",
                                    "namespace": "default",
                                    "includeRecentEvents": true
                                  }
                                }
                                """.formatted(clusterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").value(conversationId))
                .andExpect(jsonPath("$.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.assistantMessage.model").value("test-ollama"))
                .andExpect(jsonPath("$.assistantMessage.content", containsString("Ollama test response")))
                .andExpect(jsonPath("$.assistantMessage.firstTokenLatencyMs").value(1))
                .andExpect(jsonPath("$.assistantMessage.totalLatencyMs").isNumber())
                .andExpect(jsonPath("$.assistantMessage.contextChars").isNumber())
                .andExpect(jsonPath("$.contextReferences[0].referenceType").value("CLUSTER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(capturedPrompt.get().sanitizedContext())
                .contains("Respond in English")
                .contains("Preserve Kubernetes resource names");
        String assistantMessageId = JsonPath.read(response, "$.assistantMessage.id");

        mockMvc.perform(get("/api/ai-chat/conversations/{conversationId}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));

        mockMvc.perform(get("/api/ai-chat/messages/{messageId}/context-references", assistantMessageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].referenceType").value("CLUSTER"));

        mockMvc.perform(get("/api/ai-chat/conversations/{conversationId}/context-references", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageId").value(assistantMessageId))
                .andExpect(jsonPath("$[0].referenceType").value("CLUSTER"));

        mockMvc.perform(delete("/api/ai-chat/conversations/{conversationId}", conversationId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/ai-chat/conversations/{conversationId}", conversationId))
                .andExpect(status().isNotFound());
    }

    /** AiChatApiTest의 rejectsBlankMessage 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsBlankMessage() throws Exception {
        String conversationId = createConversation(null);

        mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** AiChatApiTest의 managesOnlyTheOwnersConversationLifecycle 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void managesOnlyTheOwnersConversationLifecycle() throws Exception {
        String conversationId = createConversation(null, "GENERAL", "anonymous");

        mockMvc.perform(get("/api/ai-chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(conversationId)).exists());

        mockMvc.perform(patch("/api/ai-chat/conversations/{conversationId}", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Renamed consultation","favorite":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed consultation"))
                .andExpect(jsonPath("$.favorite").value(true))
                .andExpect(jsonPath("$.mode").value("GENERAL"));

        mockMvc.perform(patch("/api/ai-chat/conversations/{conversationId}", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archived":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt", notNullValue()));
        mockMvc.perform(get("/api/ai-chat/conversations"))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(conversationId)).doesNotExist());
        mockMvc.perform(get("/api/ai-chat/conversations").param("archived", "true"))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(conversationId)).exists());

        mockMvc.perform(delete("/api/ai-chat/conversations/{conversationId}", conversationId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/ai-chat/conversations/{conversationId}", conversationId))
                .andExpect(status().isNotFound());
    }

    /** AiChatApiTest의 isolatesConversationOwnershipAtTheApplicationBoundary 처리 조건의 충족 여부를 판단한다. */
    @Test
    void isolatesConversationOwnershipAtTheApplicationBoundary() {
        var conversation = aiChatUseCase.createConversation(
                new CreateAiChatConversationCommand("Owner A", AiChatMode.GENERAL, null, null, null),
                "owner-a", "owner-test");

        org.assertj.core.api.Assertions.assertThat(aiChatUseCase.listConversations("owner-b", false))
                .noneMatch(item -> item.id().equals(conversation.id()));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> aiChatUseCase.getConversation(conversation.id(), "owner-b"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    /** AiChatApiTest의 separatesGeneralChatFromLiveClusterEvidence 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void separatesGeneralChatFromLiveClusterEvidence() throws Exception {
        String generalId = createConversation(null, "GENERAL", "anonymous");
        mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages", generalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Explain eventual consistency"}
                                """))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(capturedPrompt.get().sanitizedContext())
                .contains("mode=GENERAL")
                .doesNotContain("kubernetesEvidence:");

        String clusterId = registerCluster("live-chat-cluster-" + UUID.randomUUID());
        String clusterConversationId = createConversation(clusterId, "CLUSTER", "anonymous");
        mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages", clusterConversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"api-pod가 왜 불안정한지 확인해줘",
                                  "contextSelection": {
                                    "clusterId":"%s",
                                    "namespace":"default",
                                    "resourceType":"Pod",
                                    "resourceName":"api-pod",
                                    "includeRecentEvents":true,
                                    "includeLogs":true,
                                    "logLineLimit":80
                                  }
                                }
                                """.formatted(clusterId)))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(capturedPrompt.get().sanitizedContext())
                .contains("mode=CLUSTER")
                .contains("kubernetesEvidence:")
                .contains("source=LIVE resource=Pod/api-pod")
                .contains("container=api log=connection refused");
    }

    /** AiChatApiTest의 boundsClusterWideSnapshotContextAndDeclaresItsFreshness 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void boundsClusterWideSnapshotContextAndDeclaresItsFreshness() throws Exception {
        String clusterId = registerCluster("bounded-cluster-chat-" + UUID.randomUUID());
        String createResponse = mockMvc.perform(post("/api/ai-chat/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Cluster-wide consultation",
                                  "mode":"CLUSTER",
                                  "clusterId":"%s"
                                }
                                """.formatted(clusterId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String conversationId = JsonPath.read(createResponse, "$.id");

        mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"클러스터 상태를 요약해줘",
                                  "contextSelection":{"clusterId":"%s","includeRecentEvents":true}
                                }
                                """.formatted(clusterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assistantMessage.content", containsString("Evidence scope:")));

        org.assertj.core.api.Assertions.assertThat(capturedPrompt.get().sanitizedContext())
                .contains("Evidence freshness rule")
                .contains("source=SNAPSHOT_SCOPE")
                .contains("coverage=BOUNDED")
                .contains("MANDATORY FINAL ANSWER RULE")
                .endsWith("a namespace for live evidence.\n")
                .hasSizeLessThanOrEqualTo(12_000);
    }

    /** AiChatApiTest의 rejectsClusterScopedResourceInsideNamespaceContext 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsClusterScopedResourceInsideNamespaceContext() throws Exception {
        String clusterId = registerCluster("scoped-chat-cluster-" + UUID.randomUUID());
        String conversationId = createConversation(clusterId, "CLUSTER", "anonymous");

        mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"worker node를 확인해줘",
                                  "contextSelection": {
                                    "clusterId":"%s",
                                    "namespace":"default",
                                    "resourceType":"Node",
                                    "resourceName":"worker-1"
                                  }
                                }
                                """.formatted(clusterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"nodes 리소스를 확인해줘",
                                  "contextSelection": {
                                    "clusterId":"%s",
                                    "namespace":"default",
                                    "resourceType":"Nodes",
                                    "resourceName":"worker-1"
                                  }
                                }
                                """.formatted(clusterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** AiChatApiTest의 streamsAndPersistsTheCompletedAssistantMessage 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void streamsAndPersistsTheCompletedAssistantMessage() throws Exception {
        String conversationId = createConversation(null, "GENERAL", "anonymous");
        var pending = mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages/stream", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"message":"stream this response"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event: status")))
                .andExpect(content().string(containsString("data: accepted")))
                .andExpect(content().string(containsString("data: Ollama test response")))
                .andExpect(content().string(containsString("data: [DONE]")));

        mockMvc.perform(get("/api/ai-chat/conversations/{conversationId}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
    }

    /** AiChatApiTest의 emitsHeartbeatWhileWaitingForTheFirstAiToken 처리 결과를 지정된 대상에 전달한다. */
    @Test
    void emitsHeartbeatWhileWaitingForTheFirstAiToken() throws Exception {
        String conversationId = createConversation(null, "GENERAL", "anonymous");
        var pending = mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages/stream", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"message":"SLOW_STREAM"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event: heartbeat")))
                .andExpect(content().string(containsString("data: keep-alive")))
                .andExpect(content().string(containsString("data: [DONE]")));
    }

    /** AiChatApiTest의 persistsTheQuestionAndFailureStateWhenStreamingIsInterrupted 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Test
    void persistsTheQuestionAndFailureStateWhenStreamingIsInterrupted() throws Exception {
        String conversationId = createConversation(null, "GENERAL", "anonymous");
        var pending = mockMvc.perform(post("/api/ai-chat/conversations/{conversationId}/messages/stream", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"message":"FAIL_STREAM"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event: error")));

        mockMvc.perform(get("/api/ai-chat/conversations/{conversationId}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("FAIL_STREAM"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].errorCode").value("STREAM_INTERRUPTED"));
    }

    /** AiChatApiTest의 keepsTheMvcStreamTimeoutLongerThanTheAiReadTimeout 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void keepsTheMvcStreamTimeoutLongerThanTheAiReadTimeout() {
        long aiTimeout = Long.parseLong(environment.getProperty("aiops.ai.timeout-ms", "300000"));
        long streamTimeout = Long.parseLong(environment.getProperty("spring.mvc.async.request-timeout", "0"));

        org.assertj.core.api.Assertions.assertThat(streamTimeout).isGreaterThan(aiTimeout);
    }

    /** AiChatApiTest의 createConversation 처리에 필요한 데이터를 생성하거나 저장한다. */
    private String createConversation(String clusterId) throws Exception {
        return createConversation(clusterId, clusterId == null ? "GENERAL" : "CLUSTER", "anonymous");
    }

    /** AiChatApiTest의 createConversation 처리에 필요한 데이터를 생성하거나 저장한다. */
    private String createConversation(String clusterId, String mode, String actor) throws Exception {
        String clusterFragment = clusterId == null ? "" : """
                                  "clusterId": "%s",
                                  "namespace": "default",
                """.formatted(clusterId);
        String response = mockMvc.perform(post("/api/ai-chat/conversations")
                        .principal(() -> actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Operations chat",
                %s                  "applicationId": null,
                                  "mode": "%s"
                                }
                                """.formatted(clusterFragment, mode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.mode").value(mode))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    /** AiChatApiTest의 registerCluster 처리에 필요한 데이터를 생성하거나 저장한다. */
    private String registerCluster(String name) throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "cluster for ai chat api test",
                                  "environment": "DEV",
                                  "provider": "KIND",
                                  "region": "local",
                                  "credentialType": "KUBECONFIG",
                                  "kubeconfig": "apiVersion: v1\\nkind: Config\\nclusters: []",
                                  "syncSettings": {
                                    "autoSyncEnabled": true,
                                    "syncIntervalSeconds": 300
                                  }
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @TestConfiguration
    static class FakeAiChatConfig {

        /** FakeAiChatConfig의 aiChatPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        AiChatPort aiChatPort(AtomicReference<AiChatPrompt> capturedPrompt) {
            return new AiChatPort() {
                /** 익명 구현체의 complete 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public AiChatCompletion complete(AiChatPrompt prompt) {
                    capturedPrompt.set(prompt);
                    if ("FAIL_STREAM".equals(prompt.userMessage())) {
                        throw new IllegalStateException("simulated stream interruption");
                    }
                    if (prompt.conversationId() == null) {
                        throw new IllegalArgumentException("conversationId must be passed to AI chat port");
                    }
                    return new AiChatCompletion("Ollama test response", "test-ollama", "STOP", 1, 1);
                }

                /** 익명 구현체의 stream 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public AiChatCompletion stream(AiChatPrompt prompt, Consumer<String> onDelta) {
                    if ("SLOW_STREAM".equals(prompt.userMessage())) {
                        try {
                            Thread.sleep(30);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("test stream interrupted", exception);
                        }
                    }
                    return AiChatPort.super.stream(prompt, onDelta);
                }
            };
        }

        /** FakeAiChatConfig의 capturedPrompt 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        AtomicReference<AiChatPrompt> capturedPrompt() {
            return new AtomicReference<>();
        }

        /** FakeAiChatConfig의 kubernetesResourceManifestPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        KubernetesResourceManifestPort kubernetesResourceManifestPort() {
            return new KubernetesResourceManifestPort() {
                /** 익명 구현체의 getResourceManifest 처리 결과를 조회해 반환한다. */
                @Override
                public KubernetesResourceManifest getResourceManifest(KubernetesConnectionCredential credential,
                                                                        String namespace, String resourceType, String resourceName) {
                    return manifest(namespace, resourceType, resourceName);
                }

                /** 익명 구현체의 getAiSafeResourceManifest 처리 결과를 조회해 반환한다. */
                @Override
                public KubernetesResourceManifest getAiSafeResourceManifest(KubernetesConnectionCredential credential,
                                                                              String namespace, String resourceType, String resourceName) {
                    return manifest(namespace, resourceType, resourceName);
                }

                /** 익명 구현체의 manifest 처리에 필요한 업무 로직을 수행한다. */
                private KubernetesResourceManifest manifest(String namespace, String resourceType, String resourceName) {
                    return new KubernetesResourceManifest(namespace, resourceType, resourceName,
                            "apiVersion: v1\nkind: Pod\nmetadata:\n  name: " + resourceName + "\nstatus:\n  phase: Running",
                            true, Instant.parse("2026-09-03T00:00:00Z"));
                }
            };
        }

        /** FakeAiChatConfig의 kubernetesNamespaceDiagnosticsPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        KubernetesNamespaceDiagnosticsPort kubernetesNamespaceDiagnosticsPort() {
            return new KubernetesNamespaceDiagnosticsPort() {
                /** 익명 구현체의 collectNamespaceDiagnostics 처리의 핵심 작업 흐름을 실행한다. */
                @Override
                public KubernetesNamespaceDiagnostics collectNamespaceDiagnostics(KubernetesConnectionCredential credential, String namespace) {
                    return new KubernetesNamespaceDiagnostics(List.of(), List.of(), List.of(), Instant.now());
                }

                /** 익명 구현체의 collectPodLogs 처리의 핵심 작업 흐름을 실행한다. */
                @Override
                public KubernetesPodLogs collectPodLogs(KubernetesConnectionCredential credential, String namespace,
                                                        String podName, String containerName, int tailLines, boolean previous) {
                    return collectResourceLogs(credential, namespace, "Pod", podName, containerName, tailLines, previous);
                }

                /** 익명 구현체의 collectResourceLogs 처리의 핵심 작업 흐름을 실행한다. */
                @Override
                public KubernetesPodLogs collectResourceLogs(KubernetesConnectionCredential credential, String namespace,
                                                             String resourceType, String resourceName, String containerName,
                                                             int tailLines, boolean previous) {
                    return new KubernetesPodLogs(namespace, resourceName, tailLines,
                            List.of(new KubernetesPodLogs.ContainerLog("api", "connection refused", false)), Instant.now());
                }
            };
        }
    }
}
