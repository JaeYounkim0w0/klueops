package io.strato.aiops.domain.ai;

import io.strato.aiops.domain.cluster.EncryptedSecret;

import java.time.Instant;
import java.util.UUID;

public record AiProviderProfile(UUID id, UUID tenantId, String name, String providerType, String baseUrl,
                                EncryptedSecret credential, String defaultModel, String allowedModelsJson,
                                boolean enabled, boolean externalDataTransfer, String validationStatus,
                                Instant lastValidatedAt, String createdBy, Instant createdAt, Instant updatedAt) {
}
