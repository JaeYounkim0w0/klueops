package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface ClusterReadinessUseCase {

    /** ClusterReadinessUseCase의 getReadiness 처리 결과를 조회해 반환한다. */
    ClusterReadinessReport getReadiness(UUID clusterId, String namespace, String targetVersion, boolean refresh);
}
