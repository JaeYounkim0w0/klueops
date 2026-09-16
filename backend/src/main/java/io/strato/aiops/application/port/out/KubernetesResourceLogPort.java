package io.strato.aiops.application.port.out;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface KubernetesResourceLogPort {

    /** KubernetesResourceLogPort의 findTargets 처리 결과를 조회해 반환한다. */
    KubernetesResourceLogTargets findTargets(KubernetesConnectionCredential credential, String namespace,
                                              String resourceType, String resourceName);

    /** KubernetesResourceLogPort의 getRecentLogs 처리 결과를 조회해 반환한다. */
    KubernetesResourceLogSnapshot getRecentLogs(KubernetesConnectionCredential credential, String namespace,
                                                String resourceType, String resourceName, String podName,
                                                String containerName, int tailLines, boolean previous);

    /** KubernetesResourceLogPort의 streamLogs 처리 계약을 정의한다. */
    KubernetesResourceLogStreamResult streamLogs(KubernetesConnectionCredential credential, String namespace,
                                                 String resourceType, String resourceName, String podName,
                                                 String containerName, int tailLines, Consumer<String> onLine,
                                                 BooleanSupplier cancelled);
}
