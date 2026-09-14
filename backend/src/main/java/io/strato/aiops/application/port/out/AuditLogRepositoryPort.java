package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.audit.AuditLog;

public interface AuditLogRepositoryPort {

    AuditLog save(AuditLog auditLog);
}

