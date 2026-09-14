package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditLogRepositoryAdapter implements AuditLogRepositoryPort {

    private final AuditLogJpaRepository auditLogJpaRepository;

    public JpaAuditLogRepositoryAdapter(AuditLogJpaRepository auditLogJpaRepository) {
        this.auditLogJpaRepository = auditLogJpaRepository;
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        return auditLogJpaRepository.save(AuditLogEntity.fromDomain(auditLog)).toDomain();
    }
}

