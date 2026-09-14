package io.strato.aiops.application.port.out;

import java.util.List;

public interface KubernetesMutationPort {

    KubernetesAccessReviewResult canI(KubernetesConnectionCredential credential, String namespace, String verb,
                                      String group, String resource, String subresource, String resourceName);

    KubernetesMutationResult dryRunRolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace,
                                                            String deploymentName);

    KubernetesMutationResult rolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName);

    KubernetesMutationResult dryRunScaleDeployment(KubernetesConnectionCredential credential, String namespace,
                                                   String deploymentName, int replicas);

    KubernetesMutationResult scaleDeployment(KubernetesConnectionCredential credential, String namespace, String deploymentName, int replicas);

    List<KubernetesDeploymentRevision> listDeploymentRevisions(KubernetesConnectionCredential credential, String namespace,
                                                               String deploymentName);

    KubernetesRollbackPlan previewRollbackDeployment(KubernetesConnectionCredential credential, String namespace,
                                                     String deploymentName, Integer targetRevision);

    KubernetesMutationResult rollbackDeployment(KubernetesConnectionCredential credential, String namespace,
                                                String deploymentName, Integer targetRevision);
}
