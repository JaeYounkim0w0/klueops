package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.RoleBinding;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface RoleBindingRepositoryPort {
    /** RoleBindingRepositoryPort의 findByPrincipals 처리 결과를 조회해 반환한다. */
    List<RoleBinding> findByPrincipals(Collection<String> principals);
    /** RoleBindingRepositoryPort의 findByPrincipal 처리 결과를 조회해 반환한다. */
    default List<RoleBinding> findByPrincipal(String principal) { return findByPrincipals(List.of(principal)); }
    /** RoleBindingRepositoryPort의 findAll 처리 결과를 조회해 반환한다. */
    List<RoleBinding> findAll();
    /** RoleBindingRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<RoleBinding> findById(UUID id);
    /** RoleBindingRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    RoleBinding save(RoleBinding binding);
    /** RoleBindingRepositoryPort의 deleteById 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteById(UUID id);
}
