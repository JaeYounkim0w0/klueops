package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.RoleBinding;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RoleBindingRepositoryPort {
    List<RoleBinding> findByPrincipals(Collection<String> principals);
    List<RoleBinding> findAll();
    RoleBinding save(RoleBinding binding);
    void deleteById(UUID id);
}
