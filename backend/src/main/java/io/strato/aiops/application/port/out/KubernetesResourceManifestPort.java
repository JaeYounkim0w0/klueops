package io.strato.aiops.application.port.out;

public interface KubernetesResourceManifestPort {

    /** KubernetesResourceManifestPort의 getResourceManifest 처리 결과를 조회해 반환한다. */
    KubernetesResourceManifest getResourceManifest(
            KubernetesConnectionCredential credential,
            String namespace,
            String resourceType,
            String resourceName
    );

    /** KubernetesResourceManifestPort의 getAiSafeResourceManifest 처리 결과를 조회해 반환한다. */
    KubernetesResourceManifest getAiSafeResourceManifest(
            KubernetesConnectionCredential credential,
            String namespace,
            String resourceType,
            String resourceName
    );
}
