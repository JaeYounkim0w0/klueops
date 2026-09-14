package io.strato.aiops.application.port.in;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface GetClusterResourceLogsUseCase {

    ClusterResourceLogTargetsResult getTargets(UUID clusterId, String namespace, String resourceType, String resourceName);

    ClusterResourceLogResult getRecentLogs(UUID clusterId, String namespace, String resourceType, String resourceName,
                                           String podName, String containerName, int tailLines, boolean previous);

    ClusterResourceLogStreamResult streamLogs(UUID clusterId, String namespace, String resourceType, String resourceName,
                                              String podName, String containerName, int tailLines,
                                              Consumer<ClusterResourceLogLine> onLine,
                                              BooleanSupplier cancelled);
}
