package io.strato.aiops.domain.chat;

public enum AiChatReferenceType {
    CLUSTER,
    NAMESPACE,
    APPLICATION,
    RESOURCE_SNAPSHOT,
    EVENT_SNAPSHOT,
    LIVE_RESOURCE,
    LIVE_EVENT,
    LIVE_LOG,
    JOB
}
