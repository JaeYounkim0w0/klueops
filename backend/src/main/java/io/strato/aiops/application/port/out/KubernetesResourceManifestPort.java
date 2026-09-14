package io.strato.aiops.application.port.out;

public interface KubernetesResourceManifestPort {

    KubernetesResourceManifest getResourceManifest(
            KubernetesConnectionCredential credential,
            String namespace,
            String resourceType,
            String resourceName
    );

    KubernetesResourceManifest getAiSafeResourceManifest(
            KubernetesConnectionCredential credential,
            String namespace,
            String resourceType,
            String resourceName
    );
}
