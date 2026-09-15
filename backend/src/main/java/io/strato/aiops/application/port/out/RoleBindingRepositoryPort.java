package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.RoleBinding;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface RoleBindingRepositoryPort {
    List<RoleBinding> findByPrincipals(Collection<String> principals);
    List<RoleBinding> findAll();
    Optional<RoleBinding> findById(UUID id);
    RoleBinding save(RoleBinding binding);
    void deleteById(UUID id);
}
