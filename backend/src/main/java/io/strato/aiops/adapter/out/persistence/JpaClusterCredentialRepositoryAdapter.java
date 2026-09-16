package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaClusterCredentialRepositoryAdapter implements ClusterCredentialRepositoryPort {

    private final ClusterCredentialJpaRepository clusterCredentialJpaRepository;

    /** JpaClusterCredentialRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaClusterCredentialRepositoryAdapter(ClusterCredentialJpaRepository clusterCredentialJpaRepository) {
        this.clusterCredentialJpaRepository = clusterCredentialJpaRepository;
    }

    /** JpaClusterCredentialRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public EncryptedClusterCredential save(EncryptedClusterCredential credential) {
        return clusterCredentialJpaRepository.save(ClusterCredentialEntity.fromDomain(credential)).toDomain();
    }

    /** JpaClusterCredentialRepositoryAdapter의 findByClusterId 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<EncryptedClusterCredential> findByClusterId(UUID clusterId) {
        return clusterCredentialJpaRepository.findFirstByClusterIdOrderByCreatedAtDesc(clusterId)
                .map(ClusterCredentialEntity::toDomain);
    }
}
