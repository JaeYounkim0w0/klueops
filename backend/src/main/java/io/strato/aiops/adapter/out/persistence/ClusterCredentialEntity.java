package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cluster_credentials")
class ClusterCredentialEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID clusterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClusterCredentialType credentialType;

    @Column(nullable = false)
    private String encryptedPayload;

    @Column(nullable = false)
    private String keyId;

    @Column(nullable = false)
    private String algorithm;

    @Column(nullable = false)
    private String nonce;

    @Column(nullable = false)
    private Instant createdAt;

    /** ClusterCredentialEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected ClusterCredentialEntity() {
    }

    /** ClusterCredentialEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private ClusterCredentialEntity(UUID id, UUID clusterId, ClusterCredentialType credentialType, String encryptedPayload,
                                    String keyId, String algorithm, String nonce, Instant createdAt) {
        this.id = id;
        this.clusterId = clusterId;
        this.credentialType = credentialType;
        this.encryptedPayload = encryptedPayload;
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.nonce = nonce;
        this.createdAt = createdAt;
    }

    /** ClusterCredentialEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static ClusterCredentialEntity fromDomain(EncryptedClusterCredential credential) {
        return new ClusterCredentialEntity(
                credential.id(),
                credential.clusterId(),
                credential.credentialType(),
                credential.encryptedPayload(),
                credential.keyId(),
                credential.algorithm(),
                credential.nonce(),
                credential.createdAt()
        );
    }

    /** ClusterCredentialEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    EncryptedClusterCredential toDomain() {
        return new EncryptedClusterCredential(id, clusterId, credentialType, encryptedPayload, keyId, algorithm, nonce, createdAt);
    }
}

