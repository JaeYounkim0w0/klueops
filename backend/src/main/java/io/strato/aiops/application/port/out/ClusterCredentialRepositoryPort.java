package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.cluster.EncryptedClusterCredential;

import java.util.Optional;
import java.util.UUID;

public interface ClusterCredentialRepositoryPort {

    /** ClusterCredentialRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    EncryptedClusterCredential save(EncryptedClusterCredential credential);

    /** ClusterCredentialRepositoryPort의 findByClusterId 처리 결과를 조회해 반환한다. */
    Optional<EncryptedClusterCredential> findByClusterId(UUID clusterId);
}
