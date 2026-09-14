package io.strato.aiops.domain.cluster;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class EncryptedClusterCredential {

    private final UUID id;
    private final UUID clusterId;
    private final ClusterCredentialType credentialType;
    private final String encryptedPayload;
    private final String keyId;
    private final String algorithm;
    private final String nonce;
    private final Instant createdAt;

    public EncryptedClusterCredential(UUID id, UUID clusterId, ClusterCredentialType credentialType, String encryptedPayload,
                                      String keyId, String algorithm, String nonce, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
        this.credentialType = Objects.requireNonNull(credentialType, "credentialType must not be null");
        this.encryptedPayload = Objects.requireNonNull(encryptedPayload, "encryptedPayload must not be null");
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        this.nonce = Objects.requireNonNull(nonce, "nonce must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static EncryptedClusterCredential create(UUID clusterId, ClusterCredentialType credentialType, EncryptedSecret encryptedSecret) {
        return new EncryptedClusterCredential(
                UUID.randomUUID(),
                clusterId,
                credentialType,
                encryptedSecret.ciphertext(),
                encryptedSecret.keyId(),
                encryptedSecret.algorithm(),
                encryptedSecret.nonce(),
                Instant.now()
        );
    }

    public UUID id() {
        return id;
    }

    public UUID clusterId() {
        return clusterId;
    }

    public ClusterCredentialType credentialType() {
        return credentialType;
    }

    public String encryptedPayload() {
        return encryptedPayload;
    }

    public String keyId() {
        return keyId;
    }

    public String algorithm() {
        return algorithm;
    }

    public String nonce() {
        return nonce;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

