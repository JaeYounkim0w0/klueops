package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.audit.AuditLog;

public interface AuditLogRepositoryPort {

    /** AuditLogRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    AuditLog save(AuditLog auditLog);
}

