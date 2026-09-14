package io.strato.aiops.application.port.out;

public interface KubernetesStateSyncPort {

    KubernetesStateInventory collectClusterInventory(KubernetesConnectionCredential credential);
}
