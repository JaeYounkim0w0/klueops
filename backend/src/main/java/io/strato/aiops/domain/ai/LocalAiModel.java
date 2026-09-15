package io.strato.aiops.domain.ai;

import java.time.Instant;
import java.util.UUID;

public record LocalAiModel(UUID id, UUID providerProfileId, String modelTag, Double parameterBillions,
                           String status, Long sizeBytes, String digest, Instant updatedAt) { }
