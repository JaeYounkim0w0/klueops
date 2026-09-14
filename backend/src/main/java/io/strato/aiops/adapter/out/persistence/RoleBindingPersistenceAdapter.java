package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.RoleBindingRepositoryPort;
import io.strato.aiops.domain.identity.RoleBinding;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
class RoleBindingPersistenceAdapter implements RoleBindingRepositoryPort {
    private final RoleBindingJpaRepository repository;

    RoleBindingPersistenceAdapter(RoleBindingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<RoleBinding> findByPrincipals(Collection<String> principals) {
        return repository.findByPrincipalKeyIn(principals).stream().map(RoleBindingEntity::toDomain).toList();
    }

    @Override
    public List<RoleBinding> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(RoleBindingEntity::toDomain).toList();
    }

    @Override
    public RoleBinding save(RoleBinding binding) {
        return repository.save(RoleBindingEntity.fromDomain(binding)).toDomain();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
