package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface GetPodLogsUseCase {

    /** GetPodLogsUseCase의 getPodLogs 처리 결과를 조회해 반환한다. */
    PodLogsResult getPodLogs(UUID clusterId, String namespace, String podName, String containerName, int tailLines);

    /** GetPodLogsUseCase의 getResourceLogs 처리 결과를 조회해 반환한다. */
    PodLogsResult getResourceLogs(UUID clusterId, String namespace, String resourceType, String resourceName, String containerName, int tailLines);
}
