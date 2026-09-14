package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface StartClusterSyncUseCase {

    UUID startClusterSync(UUID clusterId, String actor, String requestId);
}
