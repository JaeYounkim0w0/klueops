package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.RoleBindingRepositoryPort;
import io.strato.aiops.domain.identity.RoleBinding;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Component
class RoleBindingPersistenceAdapter implements RoleBindingRepositoryPort {
    private final RoleBindingJpaRepository repository;

    /** RoleBindingPersistenceAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    RoleBindingPersistenceAdapter(RoleBindingJpaRepository repository) {
        this.repository = repository;
    }

    /** RoleBindingPersistenceAdapter의 findByPrincipals 처리 결과를 조회해 반환한다. */
    @Override
    public List<RoleBinding> findByPrincipals(Collection<String> principals) {
        return repository.findByPrincipalKeyIn(principals).stream().map(RoleBindingEntity::toDomain).toList();
    }

    /** RoleBindingPersistenceAdapter의 findAll 처리 결과를 조회해 반환한다. */
    @Override
    public List<RoleBinding> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(RoleBindingEntity::toDomain).toList();
    }

    /** RoleBindingPersistenceAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<RoleBinding> findById(UUID id) {
        return repository.findById(id).map(RoleBindingEntity::toDomain);
    }

    /** RoleBindingPersistenceAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public RoleBinding save(RoleBinding binding) {
        return repository.save(RoleBindingEntity.fromDomain(binding)).toDomain();
    }

    /** RoleBindingPersistenceAdapter의 deleteById 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
