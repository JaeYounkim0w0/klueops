package io.strato.aiops.application.port.in;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface GetClusterResourceLogsUseCase {

    /** GetClusterResourceLogsUseCase의 getTargets 처리 결과를 조회해 반환한다. */
    ClusterResourceLogTargetsResult getTargets(UUID clusterId, String namespace, String resourceType, String resourceName);

    /** GetClusterResourceLogsUseCase의 getRecentLogs 처리 결과를 조회해 반환한다. */
    ClusterResourceLogResult getRecentLogs(UUID clusterId, String namespace, String resourceType, String resourceName,
                                           String podName, String containerName, int tailLines, boolean previous);

    /** GetClusterResourceLogsUseCase의 streamLogs 처리 계약을 정의한다. */
    ClusterResourceLogStreamResult streamLogs(UUID clusterId, String namespace, String resourceType, String resourceName,
                                              String podName, String containerName, int tailLines,
                                              Consumer<ClusterResourceLogLine> onLine,
                                              BooleanSupplier cancelled);
}
