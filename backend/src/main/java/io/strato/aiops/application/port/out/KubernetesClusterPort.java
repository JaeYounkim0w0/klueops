package io.strato.aiops.application.port.out;

public interface KubernetesClusterPort {

    /** KubernetesClusterPort의 testConnection 처리 계약을 정의한다. */
    KubernetesConnectionTestResult testConnection(KubernetesConnectionCredential credential);

    /** KubernetesClusterPort의 listNamespaces 처리 결과를 조회해 반환한다. */
    java.util.List<KubernetesNamespace> listNamespaces(KubernetesConnectionCredential credential);

    /** KubernetesClusterPort의 listNodes 처리 결과를 조회해 반환한다. */
    java.util.List<KubernetesNode> listNodes(KubernetesConnectionCredential credential);

    /** KubernetesClusterPort의 metricsApiAvailable 처리에 필요한 업무 로직을 수행한다. */
    default boolean metricsApiAvailable(KubernetesConnectionCredential credential) {
        return false;
    }
}
