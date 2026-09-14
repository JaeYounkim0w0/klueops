package io.strato.aiops.application.port.out;

public interface KubernetesClusterPort {

    KubernetesConnectionTestResult testConnection(KubernetesConnectionCredential credential);

    java.util.List<KubernetesNamespace> listNamespaces(KubernetesConnectionCredential credential);

    java.util.List<KubernetesNode> listNodes(KubernetesConnectionCredential credential);

    default boolean metricsApiAvailable(KubernetesConnectionCredential credential) {
        return false;
    }
}
