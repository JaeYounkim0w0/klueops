package io.strato.aiops.application.port.out;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface KubernetesResourceLogPort {

    KubernetesResourceLogTargets findTargets(KubernetesConnectionCredential credential, String namespace,
                                              String resourceType, String resourceName);

    KubernetesResourceLogSnapshot getRecentLogs(KubernetesConnectionCredential credential, String namespace,
                                                String resourceType, String resourceName, String podName,
                                                String containerName, int tailLines, boolean previous);

    KubernetesResourceLogStreamResult streamLogs(KubernetesConnectionCredential credential, String namespace,
                                                 String resourceType, String resourceName, String podName,
                                                 String containerName, int tailLines, Consumer<String> onLine,
                                                 BooleanSupplier cancelled);
}
