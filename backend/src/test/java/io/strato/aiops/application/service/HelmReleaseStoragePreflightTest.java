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

    /** HelmReleaseStoragePreflightTest의 reportsDeniedHelmSecretVerbsBeforeDeployment 처리에 필요한 업무 로직을 수행한다. */
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

    /** HelmReleaseStoragePreflightTest의 deniedListPort 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesMutationPort deniedListPort() {
        return new KubernetesMutationPort() {
            /** 익명 구현체의 canI 처리 조건의 충족 여부를 판단한다. */
            @Override
            public KubernetesAccessReviewResult canI(KubernetesConnectionCredential credential, String namespace,
                                                       String verb, String group, String resource,
                                                       String subresource, String resourceName) {
                return new KubernetesAccessReviewResult(!"list".equals(verb), verb, resource, subresource,
                        namespace, "test");
            }

            /** 익명 구현체의 dryRunRolloutRestartDeployment 처리에 필요한 업무 로직을 수행한다. */
            @Override public KubernetesMutationResult dryRunRolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName) { return null; }
            /** 익명 구현체의 rolloutRestartDeployment 처리에 필요한 업무 로직을 수행한다. */
            @Override public KubernetesMutationResult rolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName) { return null; }
            /** 익명 구현체의 dryRunScaleDeployment 처리에 필요한 업무 로직을 수행한다. */
            @Override public KubernetesMutationResult dryRunScaleDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, int replicas) { return null; }
            /** 익명 구현체의 scaleDeployment 처리에 필요한 업무 로직을 수행한다. */
            @Override public KubernetesMutationResult scaleDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, int replicas) { return null; }
            /** 익명 구현체의 listDeploymentRevisions 처리 결과를 조회해 반환한다. */
            @Override public List<KubernetesDeploymentRevision> listDeploymentRevisions(KubernetesConnectionCredential credential, String namespace, String deploymentName) { return List.of(); }
            /** 익명 구현체의 previewRollbackDeployment 처리에 필요한 업무 로직을 수행한다. */
            @Override public KubernetesRollbackPlan previewRollbackDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, Integer targetRevision) { return null; }
            /** 익명 구현체의 rollbackDeployment 처리에 필요한 업무 로직을 수행한다. */
            @Override public KubernetesMutationResult rollbackDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, Integer targetRevision) { return null; }
        };
    }
}
