package io.strato.aiops.domain.ai;

import java.time.Instant;
import java.util.UUID;

public record TenantAiRoutingPolicy(UUID tenantId, String purpose, UUID primaryProfileId, String model,
                                    UUID fallbackProfileId, String fallbackModel, boolean externalTransferAllowed,
                                    int maximumContextChars, int maximumOutputTokens, String updatedBy,
                                    Instant updatedAt) {
}
