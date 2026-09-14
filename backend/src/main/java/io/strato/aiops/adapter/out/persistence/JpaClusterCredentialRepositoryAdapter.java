package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaClusterCredentialRepositoryAdapter implements ClusterCredentialRepositoryPort {

    private final ClusterCredentialJpaRepository clusterCredentialJpaRepository;

    public JpaClusterCredentialRepositoryAdapter(ClusterCredentialJpaRepository clusterCredentialJpaRepository) {
        this.clusterCredentialJpaRepository = clusterCredentialJpaRepository;
    }

    @Override
    public EncryptedClusterCredential save(EncryptedClusterCredential credential) {
        return clusterCredentialJpaRepository.save(ClusterCredentialEntity.fromDomain(credential)).toDomain();
    }

    @Override
    public Optional<EncryptedClusterCredential> findByClusterId(UUID clusterId) {
        return clusterCredentialJpaRepository.findFirstByClusterIdOrderByCreatedAtDesc(clusterId)
                .map(ClusterCredentialEntity::toDomain);
    }
}
