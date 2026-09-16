package io.strato.aiops.domain.ai;

import java.time.Instant;
import java.util.UUID;

public record LocalAiModel(UUID id, UUID providerProfileId, String modelTag, Double parameterBillions,
                           String status, Long sizeBytes, String digest, Integer evaluationScore,
                           Integer evaluationSamples, Long averageLatencyMs, Instant evaluatedAt,
                           boolean promoted, Instant updatedAt) {
    /** 평가 전 inventory 행을 간결하게 생성한다. */
    public static LocalAiModel inventory(UUID id, UUID profileId, String tag, Double parameters, String status,
                                         Long sizeBytes, String digest, Instant updatedAt) {
        return new LocalAiModel(id, profileId, tag, parameters, status, sizeBytes, digest,
                null, null, null, null, false, updatedAt);
    }
}
