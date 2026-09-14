package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface GetNamespaceDiagnosticsUseCase {

    NamespaceDiagnosticsResult getNamespaceDiagnostics(UUID clusterId, String namespace);
}
