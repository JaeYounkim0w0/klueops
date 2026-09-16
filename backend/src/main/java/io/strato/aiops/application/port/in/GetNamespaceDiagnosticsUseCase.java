package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface GetNamespaceDiagnosticsUseCase {

    /** GetNamespaceDiagnosticsUseCase의 getNamespaceDiagnostics 처리 결과를 조회해 반환한다. */
    NamespaceDiagnosticsResult getNamespaceDiagnostics(UUID clusterId, String namespace);
}
