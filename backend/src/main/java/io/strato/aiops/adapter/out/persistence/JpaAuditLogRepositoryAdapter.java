package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditLogRepositoryAdapter implements AuditLogRepositoryPort {

    private final AuditLogJpaRepository auditLogJpaRepository;

    /** JpaAuditLogRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaAuditLogRepositoryAdapter(AuditLogJpaRepository auditLogJpaRepository) {
        this.auditLogJpaRepository = auditLogJpaRepository;
    }

    /** JpaAuditLogRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public AuditLog save(AuditLog auditLog) {
        return auditLogJpaRepository.save(AuditLogEntity.fromDomain(auditLog)).toDomain();
    }
}

