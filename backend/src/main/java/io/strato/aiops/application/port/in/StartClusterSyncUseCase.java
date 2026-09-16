package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface StartClusterSyncUseCase {

    /** StartClusterSyncUseCase의 startClusterSync 처리 계약을 정의한다. */
    UUID startClusterSync(UUID clusterId, String actor, String requestId);
}
