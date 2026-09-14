package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ManagedApplicationRepositoryPort;
import io.strato.aiops.domain.application.ManagedApplication;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaManagedApplicationRepositoryAdapter implements ManagedApplicationRepositoryPort {

    private final ManagedApplicationJpaRepository repository;

    public JpaManagedApplicationRepositoryAdapter(ManagedApplicationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ManagedApplication save(ManagedApplication application) {
        return repository.save(ManagedApplicationEntity.fromDomain(application)).toDomain();
    }

    @Override
    public Optional<ManagedApplication> findById(UUID applicationId) {
        return repository.findById(applicationId).map(ManagedApplicationEntity::toDomain);
    }

    @Override
    public List<ManagedApplication> findRecent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(ManagedApplicationEntity::toDomain)
                .toList();
    }
}
