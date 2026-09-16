package io.strato.aiops.application.port.out;

public interface KubernetesNamespaceDiagnosticsPort {

    /** KubernetesNamespaceDiagnosticsPort의 collectNamespaceDiagnostics 처리의 핵심 작업 흐름을 실행한다. */
    KubernetesNamespaceDiagnostics collectNamespaceDiagnostics(KubernetesConnectionCredential credential, String namespace);

    /** KubernetesNamespaceDiagnosticsPort의 collectNamespaceDiagnostics 처리의 핵심 작업 흐름을 실행한다. */
    default KubernetesNamespaceDiagnostics collectNamespaceDiagnostics(
            KubernetesConnectionCredential credential, String namespace, boolean includeLogs) {
        return collectNamespaceDiagnostics(credential, namespace);
    }

    /** KubernetesNamespaceDiagnosticsPort의 collectPodLogs 처리의 핵심 작업 흐름을 실행한다. */
    KubernetesPodLogs collectPodLogs(KubernetesConnectionCredential credential, String namespace, String podName, String containerName, int tailLines, boolean previous);

    /** KubernetesNamespaceDiagnosticsPort의 collectResourceLogs 처리의 핵심 작업 흐름을 실행한다. */
    KubernetesPodLogs collectResourceLogs(KubernetesConnectionCredential credential, String namespace, String resourceType, String resourceName, String containerName, int tailLines, boolean previous);
}
