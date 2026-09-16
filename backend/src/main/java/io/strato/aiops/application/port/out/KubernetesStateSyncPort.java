package io.strato.aiops.application.port.out;

public interface KubernetesStateSyncPort {

    /** KubernetesStateSyncPort의 collectClusterInventory 처리의 핵심 작업 흐름을 실행한다. */
    KubernetesStateInventory collectClusterInventory(KubernetesConnectionCredential credential);
}
