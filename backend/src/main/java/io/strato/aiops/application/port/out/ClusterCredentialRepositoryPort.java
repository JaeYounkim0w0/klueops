package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.cluster.EncryptedClusterCredential;

import java.util.Optional;
import java.util.UUID;

public interface ClusterCredentialRepositoryPort {

    EncryptedClusterCredential save(EncryptedClusterCredential credential);

    Optional<EncryptedClusterCredential> findByClusterId(UUID clusterId);
}
