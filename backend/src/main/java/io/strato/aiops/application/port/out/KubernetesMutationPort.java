package io.strato.aiops.application.port.out;

import java.util.List;

public interface KubernetesMutationPort {

    /** KubernetesMutationPort의 canI 처리 조건의 충족 여부를 판단한다. */
    KubernetesAccessReviewResult canI(KubernetesConnectionCredential credential, String namespace, String verb,
                                      String group, String resource, String subresource, String resourceName);

    /** KubernetesMutationPort의 dryRunRolloutRestartDeployment 처리 계약을 정의한다. */
    KubernetesMutationResult dryRunRolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace,
                                                            String deploymentName);

    /** KubernetesMutationPort의 rolloutRestartDeployment 처리 계약을 정의한다. */
    KubernetesMutationResult rolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName);

    /** KubernetesMutationPort의 dryRunScaleDeployment 처리 계약을 정의한다. */
    KubernetesMutationResult dryRunScaleDeployment(KubernetesConnectionCredential credential, String namespace,
                                                   String deploymentName, int replicas);

    /** KubernetesMutationPort의 scaleDeployment 처리 계약을 정의한다. */
    KubernetesMutationResult scaleDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, int replicas);

    /** KubernetesMutationPort의 listDeploymentRevisions 처리 결과를 조회해 반환한다. */
    List<KubernetesDeploymentRevision> listDeploymentRevisions(KubernetesConnectionCredential credential, String namespace,
                                                               String deploymentName);

    /** KubernetesMutationPort의 previewRollbackDeployment 처리 계약을 정의한다. */
    KubernetesRollbackPlan previewRollbackDeployment(KubernetesConnectionCredential credential, String namespace,
                                                     String deploymentName, Integer targetRevision);

    /** KubernetesMutationPort의 rollbackDeployment 처리 계약을 정의한다. */
    KubernetesMutationResult rollbackDeployment(KubernetesConnectionCredential credential, String namespace,
                                                String deploymentName, Integer targetRevision);
}
