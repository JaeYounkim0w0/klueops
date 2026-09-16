package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.tenancy.Tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepositoryPort {
    /** TenantRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    Tenant save(Tenant tenant);
    /** TenantRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<Tenant> findById(UUID id);
    /** TenantRepositoryPort의 findByCode 처리 결과를 조회해 반환한다. */
    Optional<Tenant> findByCode(String code);
    /** TenantRepositoryPort의 findAll 처리 결과를 조회해 반환한다. */
    List<Tenant> findAll();
}
