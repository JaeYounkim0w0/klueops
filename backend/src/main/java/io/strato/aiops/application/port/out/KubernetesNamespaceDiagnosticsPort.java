package io.strato.aiops.application.port.out;

public interface KubernetesNamespaceDiagnosticsPort {

    KubernetesNamespaceDiagnostics collectNamespaceDiagnostics(KubernetesConnectionCredential credential, String namespace);

    default KubernetesNamespaceDiagnostics collectNamespaceDiagnostics(
            KubernetesConnectionCredential credential, String namespace, boolean includeLogs) {
        return collectNamespaceDiagnostics(credential, namespace);
    }

    KubernetesPodLogs collectPodLogs(KubernetesConnectionCredential credential, String namespace, String podName, String containerName, int tailLines, boolean previous);

    KubernetesPodLogs collectResourceLogs(KubernetesConnectionCredential credential, String namespace, String resourceType, String resourceName, String containerName, int tailLines, boolean previous);
}
