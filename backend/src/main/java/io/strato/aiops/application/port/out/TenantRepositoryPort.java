package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.tenancy.Tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepositoryPort {
    Tenant save(Tenant tenant);
    Optional<Tenant> findById(UUID id);
    Optional<Tenant> findByCode(String code);
    List<Tenant> findAll();
}
