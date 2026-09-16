package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface DeleteClusterUseCase {

    /** DeleteClusterUseCase의 deleteCluster 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteCluster(UUID clusterId, String actor, String requestId);
}
