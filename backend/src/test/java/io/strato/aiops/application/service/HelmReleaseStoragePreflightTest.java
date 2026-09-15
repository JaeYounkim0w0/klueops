package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.KubernetesAccessReviewResult;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesDeploymentRevision;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import io.strato.aiops.application.port.out.KubernetesMutationResult;
import io.strato.aiops.application.port.out.KubernetesRollbackPlan;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelmReleaseStoragePreflightTest {

    @Test
    void reportsDeniedHelmSecretVerbsBeforeDeployment() {
        KubernetesMutationPort port = deniedListPort();
        KubernetesConnectionCredential credential = new KubernetesConnectionCredential(
                ClusterCredentialType.SERVICE_ACCOUNT_TOKEN, "{}");

        HelmReleaseStoragePreflight preflight = new HelmReleaseStoragePreflight(port);

        assertThatThrownBy(() -> preflight.require(credential, "restricted"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("get/list/create")
                .hasMessageContaining("Denied verbs: list");
    }

    private KubernetesMutationPort deniedListPort() {
        return new KubernetesMutationPort() {
            @Override
            public KubernetesAccessReviewResult canI(KubernetesConnectionCredential credential, String namespace,
                                                       String verb, String group, String resource,
                                                       String subresource, String resourceName) {
                return new KubernetesAccessReviewResult(!"list".equals(verb), verb, resource, subresource,
                        namespace, "test");
            }

            @Override public KubernetesMutationResult dryRunRolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName) { return null; }
            @Override public KubernetesMutationResult rolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName) { return null; }
            @Override public KubernetesMutationResult dryRunScaleDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, int replicas) { return null; }
            @Override public KubernetesMutationResult scaleDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, int replicas) { return null; }
            @Override public List<KubernetesDeploymentRevision> listDeploymentRevisions(KubernetesConnectionCredential credential, String namespace, String deploymentName) { return List.of(); }
            @Override public KubernetesRollbackPlan previewRollbackDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, Integer targetRevision) { return null; }
            @Override public KubernetesMutationResult rollbackDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, Integer targetRevision) { return null; }
        };
    }
}
