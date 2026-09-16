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

    /** EncryptedClusterCredential 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** EncryptedClusterCredential의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
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

    /** EncryptedClusterCredential의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() {
        return id;
    }

    /** EncryptedClusterCredential의 clusterId 처리에 필요한 업무 로직을 수행한다. */
    public UUID clusterId() {
        return clusterId;
    }

    /** EncryptedClusterCredential의 credentialType 처리에 필요한 업무 로직을 수행한다. */
    public ClusterCredentialType credentialType() {
        return credentialType;
    }

    /** EncryptedClusterCredential의 encryptedPayload 처리에 필요한 업무 로직을 수행한다. */
    public String encryptedPayload() {
        return encryptedPayload;
    }

    /** EncryptedClusterCredential의 keyId 처리에 필요한 업무 로직을 수행한다. */
    public String keyId() {
        return keyId;
    }

    /** EncryptedClusterCredential의 algorithm 처리에 필요한 업무 로직을 수행한다. */
    public String algorithm() {
        return algorithm;
    }

    /** EncryptedClusterCredential의 nonce 처리에 필요한 업무 로직을 수행한다. */
    public String nonce() {
        return nonce;
    }

    /** EncryptedClusterCredential의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() {
        return createdAt;
    }
}

