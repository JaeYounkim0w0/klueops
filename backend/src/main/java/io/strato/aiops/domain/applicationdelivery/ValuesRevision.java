package io.strato.aiops.domain.applicationdelivery;

import io.strato.aiops.domain.cluster.EncryptedSecret;

import java.time.Instant;
import java.util.UUID;

public record ValuesRevision(UUID id, UUID profileId, int revision, EncryptedSecret encryptedValues,
                             String valuesSha256, Integer parentRevision, String createdBy, Instant createdAt) {
}
