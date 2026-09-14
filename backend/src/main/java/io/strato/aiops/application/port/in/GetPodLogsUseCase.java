package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface GetPodLogsUseCase {

    PodLogsResult getPodLogs(UUID clusterId, String namespace, String podName, String containerName, int tailLines);

    PodLogsResult getResourceLogs(UUID clusterId, String namespace, String resourceType, String resourceName, String containerName, int tailLines);
}
