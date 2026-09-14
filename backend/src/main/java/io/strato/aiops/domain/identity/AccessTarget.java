package io.strato.aiops.domain.identity;

import java.util.UUID;

public record AccessTarget(UUID tenantId, UUID workspaceId, UUID clusterId, String namespace) {
}
