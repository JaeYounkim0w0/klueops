package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface ClusterDataDeletionPort {

    /** ClusterDataDeletionPort의 deleteClusterData 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteClusterData(UUID clusterId);
}
