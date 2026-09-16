package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.api.model.authorization.v1.SubjectAccessReviewStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.strato.aiops.adapter.out.crypto.SecretMasker;
import io.strato.aiops.application.port.out.KubernetesAccessReviewResult;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesDeploymentRevision;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import io.strato.aiops.application.port.out.KubernetesMutationResult;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics.DiagnosticEvent;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics.DiagnosticPodLog;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics.DiagnosticResource;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnosticsPort;
import io.strato.aiops.application.port.out.KubernetesPodLogs;
import io.strato.aiops.application.port.out.KubernetesRollbackPlan;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Component
public class Fabric8KubernetesNamespaceDiagnosticsAdapter implements KubernetesNamespaceDiagnosticsPort, KubernetesMutationPort {

    private final ObjectMapper objectMapper;
    private final SecretMasker secretMasker;
    private final KubernetesDiagnosticSummaryMapper summaryMapper;
    private final Fabric8PodLogCollector podLogCollector;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final int logTailLines;
    private final int maxLogPods;
    private final int maxCollectionFailures;

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Fabric8KubernetesNamespaceDiagnosticsAdapter(
            ObjectMapper objectMapper,
            SecretMasker secretMasker,
            KubernetesDiagnosticSummaryMapper summaryMapper,
            Fabric8PodLogCollector podLogCollector,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs,
            @Value("${aiops.analysis.log-tail-lines:80}") int logTailLines,
            @Value("${aiops.analysis.max-log-pods:20}") int maxLogPods,
            @Value("${aiops.analysis.max-collection-failures:6}") int maxCollectionFailures
    ) {
        this.objectMapper = objectMapper;
        this.secretMasker = secretMasker;
        this.summaryMapper = summaryMapper;
        this.podLogCollector = podLogCollector;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.logTailLines = logTailLines;
        this.maxLogPods = maxLogPods;
        this.maxCollectionFailures = Math.max(1, maxCollectionFailures);
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 collectNamespaceDiagnostics 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    public KubernetesNamespaceDiagnostics collectNamespaceDiagnostics(KubernetesConnectionCredential credential, String namespace) {
        return collectNamespaceDiagnostics(credential, namespace, true);
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 collectNamespaceDiagnostics 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    public KubernetesNamespaceDiagnostics collectNamespaceDiagnostics(
            KubernetesConnectionCredential credential, String namespace, boolean includeLogs) {
        try (KubernetesClient client = createClient(credential)) {
            Instant collectedAt = Instant.now();
            List<DiagnosticResource> resources = new ArrayList<>();
            List<DiagnosticEvent> events = new ArrayList<>();
            List<DiagnosticPodLog> podLogs = new ArrayList<>();
            KubernetesDiagnosticCollectionGuard collection = new KubernetesDiagnosticCollectionGuard(
                    maxCollectionFailures, this::failureDetail);

            collection.run("deployments", resources::size, () ->
            client.apps().deployments().inNamespace(namespace).list().getItems()
                    .forEach(deployment -> resources.add(resource("Deployment", deployment, summaryMapper.replicaSummary(
                            deployment.getStatus() == null ? null : deployment.getStatus().getAvailableReplicas(),
                            deployment.getSpec() == null ? null : deployment.getSpec().getReplicas(),
                            workloadSummary(deployment)
                    )))));

            collection.run("statefulsets", resources::size, () ->
            client.apps().statefulSets().inNamespace(namespace).list().getItems()
                    .forEach(statefulSet -> resources.add(resource("StatefulSet", statefulSet, summaryMapper.replicaSummary(
                            statefulSet.getStatus() == null ? null : statefulSet.getStatus().getReadyReplicas(),
                            statefulSet.getSpec() == null ? null : statefulSet.getSpec().getReplicas(),
                            workloadSummary(statefulSet)
                    )))));

            collection.run("daemonsets", resources::size, () ->
            client.apps().daemonSets().inNamespace(namespace).list().getItems()
                    .forEach(daemonSet -> resources.add(resource("DaemonSet", daemonSet, summaryMapper.summary(
                            "desired", daemonSet.getStatus() == null ? 0 : valueOrZero(daemonSet.getStatus().getDesiredNumberScheduled()),
                            "ready", daemonSet.getStatus() == null ? 0 : valueOrZero(daemonSet.getStatus().getNumberReady()),
                            "updated", daemonSet.getStatus() == null ? 0 : valueOrZero(daemonSet.getStatus().getUpdatedNumberScheduled()),
                            "selector", daemonSet.getSpec() == null || daemonSet.getSpec().getSelector() == null
                                    ? Map.of() : summaryMapper.nullSafeMap(daemonSet.getSpec().getSelector().getMatchLabels()),
                            "templateLabels", daemonSet.getSpec() == null || daemonSet.getSpec().getTemplate() == null
                                    || daemonSet.getSpec().getTemplate().getMetadata() == null ? Map.of()
                                    : summaryMapper.nullSafeMap(daemonSet.getSpec().getTemplate().getMetadata().getLabels()),
                            "containers", daemonSet.getSpec() == null || daemonSet.getSpec().getTemplate() == null
                                    || daemonSet.getSpec().getTemplate().getSpec() == null
                                    || daemonSet.getSpec().getTemplate().getSpec().getContainers() == null ? List.of()
                                    : daemonSet.getSpec().getTemplate().getSpec().getContainers().stream()
                                    .map(this::containerSpecSummary)
                                    .toList()
                    )))));

            collection.run("replicasets", resources::size, () ->
            client.apps().replicaSets().inNamespace(namespace).list().getItems()
                    .forEach(replicaSet -> resources.add(resource("ReplicaSet", replicaSet, summaryMapper.replicaSummary(
                            replicaSet.getStatus() == null ? null : replicaSet.getStatus().getAvailableReplicas(),
                            replicaSet.getSpec() == null ? null : replicaSet.getSpec().getReplicas(),
                            workloadSummary(replicaSet)
                    )))));

            List<Pod> pods = new ArrayList<>();
            collection.run("pods", resources::size, () -> {
                pods.addAll(client.pods().inNamespace(namespace).list().getItems());
                pods.forEach(pod -> resources.add(resource("Pod", pod, podSummary(pod))));
            });

            collection.run("services", resources::size, () ->
            client.services().inNamespace(namespace).list().getItems()
                    .forEach(service -> resources.add(resource("Service", service, summaryMapper.summary(
                            "type", service.getSpec() == null ? "" : valueOrBlank(service.getSpec().getType()),
                            "clusterIp", service.getSpec() == null ? "" : valueOrBlank(service.getSpec().getClusterIP()),
                            "selector", service.getSpec() == null ? Map.of() : summaryMapper.nullSafeMap(service.getSpec().getSelector()),
                            "ports", service.getSpec() == null || service.getSpec().getPorts() == null ? List.of() : service.getSpec().getPorts().stream()
                                    .map(port -> summaryMapper.summary(
                                            "name", valueOrBlank(port.getName()),
                                            "port", port.getPort(),
                                            "targetPort", targetPortValue(port.getTargetPort()),
                                            "protocol", valueOrBlank(port.getProtocol())
                                    ))
                                    .toList()
                    )))));

            collection.run("endpoints", resources::size, () ->
            client.endpoints().inNamespace(namespace).list().getItems()
                    .forEach(endpoint -> resources.add(resource("Endpoint", endpoint, summaryMapper.summary(
                            "readyAddresses", endpoint.getSubsets() == null ? 0 : endpoint.getSubsets().stream()
                                    .mapToInt(subset -> subset.getAddresses() == null ? 0 : subset.getAddresses().size())
                                    .sum(),
                            "notReadyAddresses", endpoint.getSubsets() == null ? 0 : endpoint.getSubsets().stream()
                                    .mapToInt(subset -> subset.getNotReadyAddresses() == null ? 0 : subset.getNotReadyAddresses().size())
                                    .sum()
                    )))));

            collection.run("ingresses", resources::size, () ->
            client.network().v1().ingresses().inNamespace(namespace).list().getItems()
                    .forEach(ingress -> resources.add(resource("Ingress", ingress, summaryMapper.summary(
                            "className", ingress.getSpec() == null ? "" : valueOrBlank(ingress.getSpec().getIngressClassName()),
                            "rules", ingress.getSpec() == null || ingress.getSpec().getRules() == null ? 0 : ingress.getSpec().getRules().size()
                    )))));

            collection.run("configmaps", resources::size, () ->
            client.configMaps().inNamespace(namespace).list().getItems()
                    .forEach(configMap -> resources.add(resource("ConfigMap", configMap, summaryMapper.summary(
                            "keyCount", configMap.getData() == null ? 0 : configMap.getData().size(),
                            "keys", configMap.getData() == null ? List.of() : configMap.getData().keySet().stream().sorted().toList(),
                            "sensitiveKeys", configMap.getData() == null ? List.of() : configMap.getData().keySet().stream()
                                    .filter(secretMasker::isSensitiveKey)
                                    .sorted()
                                    .toList()
                    )))));

            collection.run("secrets", resources::size, () ->
            client.secrets().inNamespace(namespace).list().getItems()
                    .forEach(secret -> resources.add(resource("Secret", secret, summaryMapper.summary(
                            "type", valueOrBlank(secret.getType()),
                            "keyCount", secret.getData() == null ? 0 : secret.getData().size(),
                            "values", "***"
                    )))));

            collection.run("persistentvolumeclaims", resources::size, () ->
            client.persistentVolumeClaims().inNamespace(namespace).list().getItems()
                    .forEach(pvc -> resources.add(resource("PersistentVolumeClaim", pvc, summaryMapper.summary(
                            "phase", pvc.getStatus() == null ? "" : valueOrBlank(pvc.getStatus().getPhase()),
                            "storageClassName", pvc.getSpec() == null ? "" : valueOrBlank(pvc.getSpec().getStorageClassName()),
                            "volumeName", pvc.getSpec() == null ? "" : valueOrBlank(pvc.getSpec().getVolumeName())
                    )))));

            collection.run("serviceaccounts", resources::size, () ->
            client.serviceAccounts().inNamespace(namespace).list().getItems()
                    .forEach(serviceAccount -> resources.add(resource("ServiceAccount", serviceAccount, summaryMapper.summary(
                            "secretRefs", serviceAccount.getSecrets() == null ? 0 : serviceAccount.getSecrets().size(),
                            "imagePullSecrets", serviceAccount.getImagePullSecrets() == null ? 0 : serviceAccount.getImagePullSecrets().size()
                    )))));

            collection.run("jobs", resources::size, () ->
            client.batch().v1().jobs().inNamespace(namespace).list().getItems()
                    .forEach(job -> resources.add(resource("Job", job, summaryMapper.summary(
                            "active", job.getStatus() == null ? 0 : valueOrZero(job.getStatus().getActive()),
                            "succeeded", job.getStatus() == null ? 0 : valueOrZero(job.getStatus().getSucceeded()),
                            "failed", job.getStatus() == null ? 0 : valueOrZero(job.getStatus().getFailed())
                    )))));

            collection.run("cronjobs", resources::size, () ->
            client.batch().v1().cronjobs().inNamespace(namespace).list().getItems()
                    .forEach(cronJob -> resources.add(resource("CronJob", cronJob, summaryMapper.summary(
                            "schedule", cronJob.getSpec() == null ? "" : valueOrBlank(cronJob.getSpec().getSchedule()),
                            "suspend", cronJob.getSpec() != null && Boolean.TRUE.equals(cronJob.getSpec().getSuspend()),
                            "active", cronJob.getStatus() == null || cronJob.getStatus().getActive() == null ? 0 : cronJob.getStatus().getActive().size()
                    )))));

            collection.run("networkpolicies", resources::size, () ->
            client.network().v1().networkPolicies().inNamespace(namespace).list().getItems()
                    .forEach(networkPolicy -> resources.add(resource("NetworkPolicy", networkPolicy, summaryMapper.summary(
                            "policyTypes", networkPolicy.getSpec() == null || networkPolicy.getSpec().getPolicyTypes() == null
                                    ? List.of() : networkPolicy.getSpec().getPolicyTypes(),
                            "podSelector", networkPolicy.getSpec() == null || networkPolicy.getSpec().getPodSelector() == null
                                    ? Map.of() : summaryMapper.nullSafeMap(networkPolicy.getSpec().getPodSelector().getMatchLabels())
                    )))));

            collection.run("horizontalpodautoscalers", resources::size, () ->
            client.autoscaling().v2().horizontalPodAutoscalers().inNamespace(namespace).list().getItems()
                    .forEach(hpa -> resources.add(resource("HorizontalPodAutoscaler", hpa, summaryMapper.summary(
                            "scaleTargetRef", hpa.getSpec() == null || hpa.getSpec().getScaleTargetRef() == null ? "" : summaryMapper.summary(
                                    "kind", valueOrBlank(hpa.getSpec().getScaleTargetRef().getKind()),
                                    "name", valueOrBlank(hpa.getSpec().getScaleTargetRef().getName())
                            ),
                            "minReplicas", hpa.getSpec() == null ? 0 : valueOrZero(hpa.getSpec().getMinReplicas()),
                            "maxReplicas", hpa.getSpec() == null ? 0 : valueOrZero(hpa.getSpec().getMaxReplicas()),
                            "metricTypes", hpa.getSpec() == null || hpa.getSpec().getMetrics() == null ? List.of() : hpa.getSpec().getMetrics().stream()
                                    .map(metric -> valueOrBlank(metric.getType()))
                                    .toList(),
                            "currentReplicas", hpa.getStatus() == null ? 0 : valueOrZero(hpa.getStatus().getCurrentReplicas()),
                            "desiredReplicas", hpa.getStatus() == null ? 0 : valueOrZero(hpa.getStatus().getDesiredReplicas()),
                            "conditions", hpa.getStatus() == null || hpa.getStatus().getConditions() == null ? List.of() : hpa.getStatus().getConditions().stream()
                                    .map(condition -> summaryMapper.summary(
                                            "type", valueOrBlank(condition.getType()),
                                            "status", valueOrBlank(condition.getStatus()),
                                            "reason", valueOrBlank(condition.getReason()),
                                            "message", sanitizeText(condition.getMessage(), 300)
                                    ))
                                    .toList()
                    )))));

            collection.run("poddisruptionbudgets", resources::size, () ->
            client.policy().v1().podDisruptionBudget().inNamespace(namespace).list().getItems()
                    .forEach(pdb -> resources.add(resource("PodDisruptionBudget", pdb, summaryMapper.summary(
                            "minAvailable", pdb.getSpec() == null || pdb.getSpec().getMinAvailable() == null ? "" : pdb.getSpec().getMinAvailable().toString(),
                            "maxUnavailable", pdb.getSpec() == null || pdb.getSpec().getMaxUnavailable() == null ? "" : pdb.getSpec().getMaxUnavailable().toString(),
                            "selector", pdb.getSpec() == null || pdb.getSpec().getSelector() == null ? Map.of() : summaryMapper.nullSafeMap(pdb.getSpec().getSelector().getMatchLabels()),
                            "currentHealthy", pdb.getStatus() == null ? 0 : valueOrZero(pdb.getStatus().getCurrentHealthy()),
                            "desiredHealthy", pdb.getStatus() == null ? 0 : valueOrZero(pdb.getStatus().getDesiredHealthy()),
                            "expectedPods", pdb.getStatus() == null ? 0 : valueOrZero(pdb.getStatus().getExpectedPods()),
                            "disruptionsAllowed", pdb.getStatus() == null ? 0 : valueOrZero(pdb.getStatus().getDisruptionsAllowed())
                    )))));

            collection.run("resourcequotas", resources::size, () ->
            client.resourceQuotas().inNamespace(namespace).list().getItems()
                    .forEach(resourceQuota -> resources.add(resource("ResourceQuota", resourceQuota, summaryMapper.summary(
                            "hard", resourceQuota.getStatus() == null ? Map.of() : summaryMapper.quantityMap(resourceQuota.getStatus().getHard()),
                            "used", resourceQuota.getStatus() == null ? Map.of() : summaryMapper.quantityMap(resourceQuota.getStatus().getUsed()),
                            "scopes", resourceQuota.getSpec() == null || resourceQuota.getSpec().getScopes() == null ? List.of() : resourceQuota.getSpec().getScopes()
                    )))));

            collection.run("limitranges", resources::size, () ->
            client.limitRanges().inNamespace(namespace).list().getItems()
                    .forEach(limitRange -> resources.add(resource("LimitRange", limitRange, summaryMapper.summary(
                            "limits", limitRange.getSpec() == null || limitRange.getSpec().getLimits() == null ? List.of() : limitRange.getSpec().getLimits().stream()
                                    .map(limit -> summaryMapper.summary(
                                            "type", valueOrBlank(limit.getType()),
                                            "default", summaryMapper.quantityMap(limit.getDefault()),
                                            "defaultRequest", summaryMapper.quantityMap(limit.getDefaultRequest()),
                                            "min", summaryMapper.quantityMap(limit.getMin()),
                                            "max", summaryMapper.quantityMap(limit.getMax()),
                                            "maxLimitRequestRatio", summaryMapper.quantityMap(limit.getMaxLimitRequestRatio())
                                    ))
                                    .toList()
                    )))));

            collection.run("events", events::size, () ->
            client.v1().events().inNamespace(namespace).list().getItems()
                    .forEach(event -> events.add(new DiagnosticEvent(
                            namespace,
                            event.getInvolvedObject() == null ? null : event.getInvolvedObject().getKind(),
                            event.getInvolvedObject() == null ? null : event.getInvolvedObject().getName(),
                            event.getReason(),
                            event.getType(),
                            sanitizeText(event.getMessage(), 1000),
                            parseInstant(event.getLastTimestamp()),
                            event.getCount()
                    ))));

            if (includeLogs) {
                collection.run("pod-logs", podLogs::size, () ->
                        pods.stream()
                                .sorted(Comparator.comparing(this::podIsLikelyProblematic).reversed())
                                .limit(maxLogPods)
                                .forEach(pod -> podLogCollector.collect(client, namespace, pod, null, logTailLines, false, podLogs)));
            } else {
                collection.skip("pod-logs", "Log collection was disabled for this request.");
            }

            collection.requireSuccessfulSource();
            return new KubernetesNamespaceDiagnostics(resources, events, podLogs, collectedAt, collection.stages());
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Kubernetes namespace diagnostics failed: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 collectPodLogs 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    public KubernetesPodLogs collectPodLogs(KubernetesConnectionCredential credential, String namespace, String podName,
                                            String containerName, int tailLines, boolean previous) {
        try (KubernetesClient client = createClient(credential)) {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                throw new KubernetesApiException("Pod not found: " + namespace + "/" + podName,
                        new IllegalArgumentException("Pod not found"));
            }
            List<DiagnosticPodLog> logs = new ArrayList<>();
            podLogCollector.collect(client, namespace, pod, containerName, tailLines, previous, logs);
            return new KubernetesPodLogs(namespace, podName, tailLines, logs.stream()
                    .map(log -> new KubernetesPodLogs.ContainerLog(log.containerName(), log.log(), log.truncated()))
                    .toList(), Instant.now());
        } catch (KubernetesApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Kubernetes pod log request failed: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 collectResourceLogs 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    public KubernetesPodLogs collectResourceLogs(KubernetesConnectionCredential credential, String namespace, String resourceType,
                                                 String resourceName, String containerName, int tailLines, boolean previous) {
        try (KubernetesClient client = createClient(credential)) {
            List<Pod> pods = podsForResource(client, namespace, resourceType, resourceName);
            List<DiagnosticPodLog> logs = new ArrayList<>();
            pods.stream()
                    .sorted(Comparator.comparing(pod -> pod.getMetadata() == null ? "" : valueOrBlank(pod.getMetadata().getName())))
                    .limit(maxLogPods)
                    .forEach(pod -> podLogCollector.collect(client, namespace, pod, containerName, tailLines, previous, logs));
            if (logs.isEmpty()) {
                logs.add(new DiagnosticPodLog(namespace, resourceName, valueOrBlank(containerName),
                        "No pod logs are directly available for " + resourceType + "/" + resourceName
                                + ". Check related events and owner resources for this object.", false));
            }
            return new KubernetesPodLogs(namespace, resourceType + "/" + resourceName, tailLines, logs.stream()
                    .map(log -> new KubernetesPodLogs.ContainerLog(log.containerName(), log.log(), log.truncated()))
                    .toList(), Instant.now());
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Kubernetes resource log request failed: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 canI 처리 조건의 충족 여부를 판단한다. */
    @Override
    public KubernetesAccessReviewResult canI(KubernetesConnectionCredential credential, String namespace, String verb,
                                             String group, String resource, String subresource, String resourceName) {
        try (KubernetesClient client = createClient(credential)) {
            SelfSubjectAccessReview review = new SelfSubjectAccessReviewBuilder()
                    .withNewSpec()
                    .withNewResourceAttributes()
                    .withNamespace(namespace)
                    .withVerb(verb)
                    .withGroup(group)
                    .withResource(resource)
                    .withSubresource(valueOrBlank(subresource).isBlank() ? null : subresource)
                    .withName(valueOrBlank(resourceName).isBlank() ? null : resourceName)
                    .endResourceAttributes()
                    .endSpec()
                    .build();
            SelfSubjectAccessReview result = client.authorization().v1().selfSubjectAccessReview().create(review);
            SubjectAccessReviewStatus status = result == null ? null : result.getStatus();
            boolean allowed = status != null && Boolean.TRUE.equals(status.getAllowed());
            String reason = status == null ? "Kubernetes did not return access review status"
                    : valueOrBlank(status.getReason()).isBlank()
                    ? allowed ? "Kubernetes RBAC allowed this action" : "Kubernetes RBAC denied this action"
                    : status.getReason();
            return new KubernetesAccessReviewResult(allowed, verb, resource, subresource, namespace, reason);
        } catch (RuntimeException exception) {
            return new KubernetesAccessReviewResult(false, verb, resource, subresource, namespace,
                    "Kubernetes RBAC review failed: " + failureDetail(exception));
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 dryRunRolloutRestartDeployment 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public KubernetesMutationResult dryRunRolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace,
                                                                   String deploymentName) {
        try (KubernetesClient client = createClient(credential)) {
            Deployment current = requireDeployment(client, namespace, deploymentName);
            KubernetesAccessReviewResult access = canI(credential, namespace, "patch", "apps", "deployments", null,
                    deploymentName);
            if (!access.allowed()) {
                throw guarded("RBAC denied rollout restart dry-run: " + access.reason());
            }
            Instant plannedAt = Instant.now();
            return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "rollout-restart-dry-run",
                    deploymentState(current), "annotation=aiops.strato.io/restartedAt:" + plannedAt + " dryRun=true",
                    plannedAt);
        } catch (KubernetesApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Failed to dry-run rollout restart deployment: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 rolloutRestartDeployment 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public KubernetesMutationResult rolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace,
                                                             String deploymentName) {
        try (KubernetesClient client = createClient(credential)) {
            KubernetesAccessReviewResult access = canI(credential, namespace, "patch", "apps", "deployments", null,
                    deploymentName);
            if (!access.allowed()) {
                throw guarded("RBAC denied rollout restart: " + access.reason());
            }
            Deployment current = requireDeployment(client, namespace, deploymentName);
            Instant changedAt = Instant.now();
            String previousState = deploymentState(current);
            Deployment updated = client.apps().deployments().inNamespace(namespace).withName(deploymentName).edit(deployment -> {
                if (deployment.getSpec() == null || deployment.getSpec().getTemplate() == null) {
                    return deployment;
                }
                if (deployment.getSpec().getTemplate().getMetadata() == null) {
                    deployment.getSpec().getTemplate().setMetadata(new ObjectMeta());
                }
                Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();
                if (annotations == null) {
                    annotations = new LinkedHashMap<>();
                    deployment.getSpec().getTemplate().getMetadata().setAnnotations(annotations);
                }
                annotations.put("aiops.strato.io/restartedAt", changedAt.toString());
                return deployment;
            });
            String nextState = "generation=" + valueOrBlank(updated == null || updated.getMetadata() == null
                    || updated.getMetadata().getGeneration() == null ? null : String.valueOf(updated.getMetadata().getGeneration()))
                    + " annotation=aiops.strato.io/restartedAt:" + changedAt;
            return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "rollout-restart",
                    previousState, nextState, changedAt);
        } catch (KubernetesApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Failed to rollout restart deployment: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 dryRunScaleDeployment 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public KubernetesMutationResult dryRunScaleDeployment(KubernetesConnectionCredential credential, String namespace,
                                                          String deploymentName, int replicas) {
        try (KubernetesClient client = createClient(credential)) {
            Deployment current = requireDeployment(client, namespace, deploymentName);
            KubernetesAccessReviewResult access = canI(credential, namespace, "patch", "apps", "deployments", "scale",
                    deploymentName);
            if (!access.allowed()) {
                throw guarded("RBAC denied scale dry-run: " + access.reason());
            }
            int previousReplicas = valueOrZero(current.getSpec().getReplicas());
            return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "scale-dry-run",
                    "replicas=" + previousReplicas, "replicas=" + replicas + " dryRun=true", Instant.now());
        } catch (KubernetesApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Failed to dry-run scale deployment: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 scaleDeployment 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public KubernetesMutationResult scaleDeployment(KubernetesConnectionCredential credential, String namespace,
                                                    String deploymentName, int replicas) {
        try (KubernetesClient client = createClient(credential)) {
            KubernetesAccessReviewResult access = canI(credential, namespace, "patch", "apps", "deployments", "scale",
                    deploymentName);
            if (!access.allowed()) {
                throw guarded("RBAC denied scale: " + access.reason());
            }
            Deployment current = requireDeployment(client, namespace, deploymentName);
            int previousReplicas = valueOrZero(current.getSpec().getReplicas());
            Instant changedAt = Instant.now();
            Deployment updated = client.apps().deployments().inNamespace(namespace).withName(deploymentName).edit(deployment -> {
                deployment.getSpec().setReplicas(replicas);
                return deployment;
            });
            int nextReplicas = updated == null || updated.getSpec() == null ? replicas : valueOrZero(updated.getSpec().getReplicas());
            return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "scale",
                    "replicas=" + previousReplicas, "replicas=" + nextReplicas, changedAt);
        } catch (KubernetesApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Failed to scale deployment: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 listDeploymentRevisions 처리 결과를 조회해 반환한다. */
    @Override
    public List<KubernetesDeploymentRevision> listDeploymentRevisions(KubernetesConnectionCredential credential,
                                                                      String namespace,
                                                                      String deploymentName) {
        try (KubernetesClient client = createClient(credential)) {
            Deployment deployment = requireDeployment(client, namespace, deploymentName);
            String current = currentRevision(deployment);
            return deploymentReplicaSets(client, namespace, deployment).stream()
                    .sorted(Comparator.comparingInt((ReplicaSet replicaSet) -> parseRevision(revision(replicaSet))).reversed())
                    .map(replicaSet -> new KubernetesDeploymentRevision(
                            revision(replicaSet),
                            Objects.equals(revision(replicaSet), current),
                            replicaSet.getMetadata() == null ? "" : valueOrBlank(replicaSet.getMetadata().getName()),
                            replicaSet.getSpec() == null ? 0 : valueOrZero(replicaSet.getSpec().getReplicas()),
                            replicaSetImage(replicaSet),
                            replicaSetState(replicaSet),
                            replicaSet.getMetadata() == null || replicaSet.getMetadata().getCreationTimestamp() == null
                                    ? null : Instant.parse(replicaSet.getMetadata().getCreationTimestamp())
                    ))
                    .toList();
        } catch (KubernetesApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Failed to list deployment revisions: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 previewRollbackDeployment 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public KubernetesRollbackPlan previewRollbackDeployment(KubernetesConnectionCredential credential, String namespace,
                                                            String deploymentName, Integer targetRevision) {
        try (KubernetesClient client = createClient(credential)) {
            Deployment current = requireDeployment(client, namespace, deploymentName);
            KubernetesAccessReviewResult access = canI(credential, namespace, "patch", "apps", "deployments", null,
                    deploymentName);
            if (!access.allowed()) {
                return rollbackPlan(namespace, deploymentName, currentRevision(current), stringRevision(targetRevision),
                        false, "RBAC denied rollback: " + access.reason(), deploymentState(current), "");
            }
            ReplicaSet target = targetReplicaSet(client, namespace, current, targetRevision);
            String currentRevision = currentRevision(current);
            String selectedRevision = revision(target);
            if (selectedRevision.isBlank()) {
                return rollbackPlan(namespace, deploymentName, currentRevision, stringRevision(targetRevision), false,
                        "Rollback target revision could not be identified.", deploymentState(current), replicaSetState(target));
            }
            if (selectedRevision.equals(currentRevision)) {
                return rollbackPlan(namespace, deploymentName, currentRevision, selectedRevision, false,
                        "Rollback target revision is the current revision.", deploymentState(current), replicaSetState(target));
            }
            return rollbackPlan(namespace, deploymentName, currentRevision, selectedRevision, true,
                    "Rollback guard passed. Review the revision diff and confirmation text before execution.",
                    deploymentState(current), replicaSetState(target));
        } catch (KubernetesApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Failed to preview deployment rollback: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 rollbackDeployment 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public KubernetesMutationResult rollbackDeployment(KubernetesConnectionCredential credential, String namespace,
                                                       String deploymentName, Integer targetRevision) {
        try (KubernetesClient client = createClient(credential)) {
            KubernetesRollbackPlan plan = previewRollbackDeployment(credential, namespace, deploymentName, targetRevision);
            if (!plan.executable()) {
                throw guarded("Rollback guard blocked execution: " + plan.reason());
            }
            Deployment current = requireDeployment(client, namespace, deploymentName);
            ReplicaSet target = targetReplicaSet(client, namespace, current, parseRevision(plan.targetRevision()));
            Instant changedAt = Instant.now();
            Deployment updated = client.apps().deployments().inNamespace(namespace).withName(deploymentName).edit(deployment -> {
                deployment.getSpec().setTemplate(target.getSpec().getTemplate());
                if (deployment.getSpec().getTemplate().getMetadata() != null
                        && deployment.getSpec().getTemplate().getMetadata().getLabels() != null) {
                    deployment.getSpec().getTemplate().getMetadata().getLabels().remove("pod-template-hash");
                }
                if (deployment.getMetadata().getAnnotations() == null) {
                    deployment.getMetadata().setAnnotations(new LinkedHashMap<>());
                }
                deployment.getMetadata().getAnnotations().put("aiops.strato.io/rollbackAt", changedAt.toString());
                deployment.getMetadata().getAnnotations().put("aiops.strato.io/rollbackToRevision", plan.targetRevision());
                return deployment;
            });
            return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "rollback",
                    plan.currentState(), deploymentState(updated) + " rollbackToRevision=" + plan.targetRevision(), changedAt);
        } catch (KubernetesApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Failed to rollback deployment: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 requireDeployment 처리 입력과 현재 상태의 유효성을 검증한다. */
    private Deployment requireDeployment(KubernetesClient client, String namespace, String deploymentName) {
        Deployment current = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
        if (current == null || current.getSpec() == null) {
            throw new NoSuchElementException("Deployment not found: " + namespace + "/" + deploymentName);
        }
        return current;
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 guarded 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesApiException guarded(String message) {
        return new KubernetesApiException(message, new IllegalStateException(message));
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 rollbackPlan 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesRollbackPlan rollbackPlan(String namespace, String deploymentName, String currentRevision,
                                                String targetRevision, boolean executable, String reason,
                                                String currentState, String targetState) {
        return new KubernetesRollbackPlan(namespace, deploymentName, currentRevision, targetRevision, executable, reason,
                executable ? "ROLLBACK " + namespace + "/" + deploymentName + " TO REVISION " + targetRevision : "",
                currentState, targetState, Instant.now());
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 targetReplicaSet 처리에 필요한 업무 로직을 수행한다. */
    private ReplicaSet targetReplicaSet(KubernetesClient client, String namespace, Deployment deployment, Integer targetRevision) {
        List<ReplicaSet> replicaSets = deploymentReplicaSets(client, namespace, deployment);
        if (replicaSets.isEmpty()) {
            throw new NoSuchElementException("No ReplicaSet revision history found for Deployment "
                    + deployment.getMetadata().getName());
        }
        if (targetRevision != null && targetRevision > 0) {
            return replicaSets.stream()
                    .filter(replicaSet -> targetRevision == parseRevision(revision(replicaSet)))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("ReplicaSet revision not found: " + targetRevision));
        }
        int current = parseRevision(currentRevision(deployment));
        return replicaSets.stream()
                .filter(replicaSet -> parseRevision(revision(replicaSet)) < current)
                .max(Comparator.comparingInt(replicaSet -> parseRevision(revision(replicaSet))))
                .orElseThrow(() -> new NoSuchElementException("Previous ReplicaSet revision not found for Deployment "
                        + deployment.getMetadata().getName()));
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 deploymentReplicaSets 처리에 필요한 업무 로직을 수행한다. */
    private List<ReplicaSet> deploymentReplicaSets(KubernetesClient client, String namespace, Deployment deployment) {
        Map<String, String> labels = deployment.getSpec().getSelector() == null
                || deployment.getSpec().getSelector().getMatchLabels() == null ? Map.of()
                : deployment.getSpec().getSelector().getMatchLabels();
        return client.apps().replicaSets().inNamespace(namespace)
                .withLabels(labels)
                .list()
                .getItems()
                .stream()
                .filter(replicaSet -> revision(replicaSet).matches("\\d+"))
                .sorted(Comparator.comparingInt(replicaSet -> parseRevision(revision(replicaSet))))
                .toList();
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 deploymentState 처리에 필요한 업무 로직을 수행한다. */
    private String deploymentState(Deployment deployment) {
        if (deployment == null) {
            return "";
        }
        String generation = deployment.getMetadata() == null || deployment.getMetadata().getGeneration() == null
                ? "" : String.valueOf(deployment.getMetadata().getGeneration());
        int replicas = deployment.getSpec() == null ? 0 : valueOrZero(deployment.getSpec().getReplicas());
        String image = deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getSpec() == null
                || deployment.getSpec().getTemplate().getSpec().getContainers().isEmpty()
                ? "" : deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        return "generation=" + generation + " revision=" + currentRevision(deployment)
                + " replicas=" + replicas + " image=" + valueOrBlank(image);
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 replicaSetState 처리에 필요한 업무 로직을 수행한다. */
    private String replicaSetState(ReplicaSet replicaSet) {
        if (replicaSet == null) {
            return "";
        }
        int replicas = replicaSet.getSpec() == null ? 0 : valueOrZero(replicaSet.getSpec().getReplicas());
        return "revision=" + revision(replicaSet) + " replicas=" + replicas + " image=" + valueOrBlank(replicaSetImage(replicaSet));
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 replicaSetImage 처리에 필요한 업무 로직을 수행한다. */
    private String replicaSetImage(ReplicaSet replicaSet) {
        return replicaSet.getSpec() == null || replicaSet.getSpec().getTemplate() == null
                || replicaSet.getSpec().getTemplate().getSpec() == null
                || replicaSet.getSpec().getTemplate().getSpec().getContainers().isEmpty()
                ? "" : valueOrBlank(replicaSet.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 currentRevision 처리에 필요한 업무 로직을 수행한다. */
    private String currentRevision(Deployment deployment) {
        return deployment == null || deployment.getMetadata() == null || deployment.getMetadata().getAnnotations() == null
                ? "" : valueOrBlank(deployment.getMetadata().getAnnotations().get("deployment.kubernetes.io/revision"));
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 revision 처리에 필요한 업무 로직을 수행한다. */
    private String revision(ReplicaSet replicaSet) {
        return replicaSet == null || replicaSet.getMetadata() == null || replicaSet.getMetadata().getAnnotations() == null
                ? "" : valueOrBlank(replicaSet.getMetadata().getAnnotations().get("deployment.kubernetes.io/revision"));
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 stringRevision 처리에 필요한 업무 로직을 수행한다. */
    private String stringRevision(Integer revision) {
        return revision == null || revision <= 0 ? "" : String.valueOf(revision);
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 parseRevision 처리 데이터를 필요한 표현으로 변환한다. */
    private Integer parseRevision(String revision) {
        try {
            return Integer.parseInt(valueOrBlank(revision));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 resource 처리에 필요한 업무 로직을 수행한다. */
    private DiagnosticResource resource(String resourceType, HasMetadata resource, Map<String, Object> summary) {
        return new DiagnosticResource(
                resource.getMetadata() == null ? null : resource.getMetadata().getNamespace(),
                resourceType,
                resource.getMetadata() == null ? "" : resource.getMetadata().getName(),
                summaryMapper.statusFromSummary(summary),
                summaryMapper.writeJson(summary)
        );
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 podSummary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> podSummary(Pod pod) {
        int restartCount = pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                ? 0
                : pod.getStatus().getContainerStatuses().stream()
                .mapToInt(status -> status.getRestartCount() == null ? 0 : status.getRestartCount())
                .sum();
        long readyContainers = pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                ? 0
                : pod.getStatus().getContainerStatuses().stream()
                .filter(status -> Boolean.TRUE.equals(status.getReady()))
                .count();
        List<Map<String, Object>> containerStates = pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                ? List.of()
                : pod.getStatus().getContainerStatuses().stream()
                .map(this::containerStateSummary)
                .toList();
        return summaryMapper.summary(
                "phase", pod.getStatus() == null ? "" : valueOrBlank(pod.getStatus().getPhase()),
                "restartCount", restartCount,
                "readyContainers", readyContainers,
                "totalContainers", pod.getSpec() == null || pod.getSpec().getContainers() == null ? 0 : pod.getSpec().getContainers().size(),
                "nodeName", pod.getSpec() == null ? "" : valueOrBlank(pod.getSpec().getNodeName()),
                "podIp", pod.getStatus() == null ? "" : valueOrBlank(pod.getStatus().getPodIP()),
                "labels", pod.getMetadata() == null ? Map.of() : summaryMapper.nullSafeMap(pod.getMetadata().getLabels()),
                "qosClass", pod.getStatus() == null ? "" : valueOrBlank(pod.getStatus().getQosClass()),
                "restartPolicy", pod.getSpec() == null ? "" : valueOrBlank(pod.getSpec().getRestartPolicy()),
                "volumes", pod.getSpec() == null || pod.getSpec().getVolumes() == null ? List.of() : pod.getSpec().getVolumes().stream()
                        .map(this::volumeSummary)
                        .toList(),
                "containers", pod.getSpec() == null || pod.getSpec().getContainers() == null ? List.of() : pod.getSpec().getContainers().stream()
                        .map(this::containerSpecSummary)
                        .toList(),
                "containerStates", containerStates
        );
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 containerSpecSummary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> containerSpecSummary(Container container) {
        return summaryMapper.summary(
                "name", valueOrBlank(container.getName()),
                "image", sanitizeImage(container.getImage()),
                "imagePullPolicy", valueOrBlank(container.getImagePullPolicy()),
                "requests", container.getResources() == null ? Map.of() : summaryMapper.quantityMap(container.getResources().getRequests()),
                "limits", container.getResources() == null ? Map.of() : summaryMapper.quantityMap(container.getResources().getLimits()),
                "hasLivenessProbe", container.getLivenessProbe() != null,
                "hasReadinessProbe", container.getReadinessProbe() != null,
                "hasStartupProbe", container.getStartupProbe() != null,
                "volumeMounts", container.getVolumeMounts() == null ? List.of() : container.getVolumeMounts().stream()
                        .map(this::volumeMountSummary)
                        .toList(),
                "ports", container.getPorts() == null ? List.of() : container.getPorts().stream()
                        .map(port -> summaryMapper.summary(
                                "name", valueOrBlank(port.getName()),
                                "containerPort", port.getContainerPort(),
                                "protocol", valueOrBlank(port.getProtocol())
                        ))
                        .toList()
        );
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 volumeSummary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> volumeSummary(Volume volume) {
        String type = "other";
        String sourceName = "";
        if (volume.getConfigMap() != null) {
            type = "ConfigMap";
            sourceName = valueOrBlank(volume.getConfigMap().getName());
        } else if (volume.getSecret() != null) {
            type = "Secret";
            sourceName = valueOrBlank(volume.getSecret().getSecretName());
        } else if (volume.getPersistentVolumeClaim() != null) {
            type = "PersistentVolumeClaim";
            sourceName = valueOrBlank(volume.getPersistentVolumeClaim().getClaimName());
        } else if (volume.getEmptyDir() != null) {
            type = "EmptyDir";
        } else if (volume.getHostPath() != null) {
            type = "HostPath";
            sourceName = valueOrBlank(volume.getHostPath().getPath());
        }
        return summaryMapper.summary(
                "name", valueOrBlank(volume.getName()),
                "type", type,
                "sourceName", sourceName
        );
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 volumeMountSummary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> volumeMountSummary(VolumeMount mount) {
        return summaryMapper.summary(
                "name", valueOrBlank(mount.getName()),
                "mountPath", valueOrBlank(mount.getMountPath()),
                "readOnly", Boolean.TRUE.equals(mount.getReadOnly())
        );
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 targetPortValue 처리에 필요한 업무 로직을 수행한다. */
    private String targetPortValue(IntOrString targetPort) {
        if (targetPort == null) {
            return "";
        }
        String stringValue = targetPort.getStrVal();
        if (stringValue != null && !stringValue.isBlank()) {
            return stringValue;
        }
        Integer intValue = targetPort.getIntVal();
        return intValue == null ? "" : String.valueOf(intValue);
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 workloadSummary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> workloadSummary(Deployment deployment) {
        return summaryMapper.summary(
                "strategy", valueOrBlank(deployment.getSpec() == null || deployment.getSpec().getStrategy() == null
                        ? null : deployment.getSpec().getStrategy().getType()),
                "selector", deployment.getSpec() == null || deployment.getSpec().getSelector() == null
                        ? Map.of() : summaryMapper.nullSafeMap(deployment.getSpec().getSelector().getMatchLabels()),
                "templateLabels", deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                        || deployment.getSpec().getTemplate().getMetadata() == null ? Map.of()
                        : summaryMapper.nullSafeMap(deployment.getSpec().getTemplate().getMetadata().getLabels()),
                "containers", deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                        || deployment.getSpec().getTemplate().getSpec() == null
                        || deployment.getSpec().getTemplate().getSpec().getContainers() == null ? List.of()
                        : deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                        .map(this::containerSpecSummary)
                        .toList()
        );
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 workloadSummary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> workloadSummary(StatefulSet statefulSet) {
        return summaryMapper.summary(
                "serviceName", valueOrBlank(statefulSet.getSpec() == null ? null : statefulSet.getSpec().getServiceName()),
                "selector", statefulSet.getSpec() == null || statefulSet.getSpec().getSelector() == null
                        ? Map.of() : summaryMapper.nullSafeMap(statefulSet.getSpec().getSelector().getMatchLabels()),
                "templateLabels", statefulSet.getSpec() == null || statefulSet.getSpec().getTemplate() == null
                        || statefulSet.getSpec().getTemplate().getMetadata() == null ? Map.of()
                        : summaryMapper.nullSafeMap(statefulSet.getSpec().getTemplate().getMetadata().getLabels()),
                "containers", statefulSet.getSpec() == null || statefulSet.getSpec().getTemplate() == null
                        || statefulSet.getSpec().getTemplate().getSpec() == null
                        || statefulSet.getSpec().getTemplate().getSpec().getContainers() == null ? List.of()
                        : statefulSet.getSpec().getTemplate().getSpec().getContainers().stream()
                        .map(this::containerSpecSummary)
                        .toList()
        );
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 workloadSummary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> workloadSummary(ReplicaSet replicaSet) {
        return summaryMapper.summary(
                "selector", replicaSet.getSpec() == null || replicaSet.getSpec().getSelector() == null
                        ? Map.of() : summaryMapper.nullSafeMap(replicaSet.getSpec().getSelector().getMatchLabels()),
                "templateLabels", replicaSet.getSpec() == null || replicaSet.getSpec().getTemplate() == null
                        || replicaSet.getSpec().getTemplate().getMetadata() == null ? Map.of()
                        : summaryMapper.nullSafeMap(replicaSet.getSpec().getTemplate().getMetadata().getLabels()),
                "containers", replicaSet.getSpec() == null || replicaSet.getSpec().getTemplate() == null
                        || replicaSet.getSpec().getTemplate().getSpec() == null
                        || replicaSet.getSpec().getTemplate().getSpec().getContainers() == null ? List.of()
                        : replicaSet.getSpec().getTemplate().getSpec().getContainers().stream()
                        .map(this::containerSpecSummary)
                        .toList()
        );
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 containerStateSummary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> containerStateSummary(ContainerStatus status) {
        if (status.getState() == null) {
            return summaryMapper.summary("name", status.getName(), "state", "UNKNOWN", "restartCount", valueOrZero(status.getRestartCount()));
        }
        if (status.getState().getWaiting() != null) {
            return summaryMapper.summary(
                    "name", status.getName(),
                    "state", "WAITING",
                    "reason", valueOrBlank(status.getState().getWaiting().getReason()),
                    "message", sanitizeText(status.getState().getWaiting().getMessage(), 300),
                    "restartCount", valueOrZero(status.getRestartCount())
            );
        }
        if (status.getState().getTerminated() != null) {
            return summaryMapper.summary(
                    "name", status.getName(),
                    "state", "TERMINATED",
                    "reason", valueOrBlank(status.getState().getTerminated().getReason()),
                    "exitCode", status.getState().getTerminated().getExitCode(),
                    "message", sanitizeText(status.getState().getTerminated().getMessage(), 300),
                    "restartCount", valueOrZero(status.getRestartCount())
            );
        }
        return summaryMapper.summary("name", status.getName(), "state", "RUNNING", "restartCount", valueOrZero(status.getRestartCount()));
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 podsForResource 처리에 필요한 업무 로직을 수행한다. */
    private List<Pod> podsForResource(KubernetesClient client, String namespace, String resourceType, String resourceName) {
        String normalizedType = valueOrBlank(resourceType).toLowerCase();
        return switch (normalizedType) {
            case "pod" -> {
                Pod pod = client.pods().inNamespace(namespace).withName(resourceName).get();
                yield pod == null ? List.of() : List.of(pod);
            }
            case "deployment" -> {
                var deployment = client.apps().deployments().inNamespace(namespace).withName(resourceName).get();
                yield deployment == null || deployment.getSpec() == null
                        ? List.of()
                        : podsBySelector(client, namespace, deployment.getSpec().getSelector());
            }
            case "statefulset" -> {
                var statefulSet = client.apps().statefulSets().inNamespace(namespace).withName(resourceName).get();
                yield statefulSet == null || statefulSet.getSpec() == null
                        ? List.of()
                        : podsBySelector(client, namespace, statefulSet.getSpec().getSelector());
            }
            case "daemonset" -> {
                var daemonSet = client.apps().daemonSets().inNamespace(namespace).withName(resourceName).get();
                yield daemonSet == null || daemonSet.getSpec() == null
                        ? List.of()
                        : podsBySelector(client, namespace, daemonSet.getSpec().getSelector());
            }
            case "replicaset" -> {
                var replicaSet = client.apps().replicaSets().inNamespace(namespace).withName(resourceName).get();
                yield replicaSet == null || replicaSet.getSpec() == null
                        ? List.of()
                        : podsBySelector(client, namespace, replicaSet.getSpec().getSelector());
            }
            case "job" -> {
                var job = client.batch().v1().jobs().inNamespace(namespace).withName(resourceName).get();
                yield job == null || job.getSpec() == null
                        ? List.of()
                        : podsBySelector(client, namespace, job.getSpec().getSelector());
            }
            case "service" -> {
                var service = client.services().inNamespace(namespace).withName(resourceName).get();
                yield service == null || service.getSpec() == null
                        ? List.of()
                        : podsByLabels(client, namespace, summaryMapper.nullSafeMap(service.getSpec().getSelector()));
            }
            default -> List.of();
        };
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 podsBySelector 처리에 필요한 업무 로직을 수행한다. */
    private List<Pod> podsBySelector(KubernetesClient client, String namespace, LabelSelector selector) {
        if (selector == null || selector.getMatchLabels() == null || selector.getMatchLabels().isEmpty()) {
            return List.of();
        }
        return podsByLabels(client, namespace, selector.getMatchLabels());
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 podsByLabels 처리에 필요한 업무 로직을 수행한다. */
    private List<Pod> podsByLabels(KubernetesClient client, String namespace, Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        return client.pods().inNamespace(namespace).withLabels(labels).list().getItems();
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 sanitizeImage 처리에 필요한 업무 로직을 수행한다. */
    private String sanitizeImage(String image) {
        if (image == null || image.isBlank()) {
            return "";
        }
        return sanitizeText(image, 300);
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 podIsLikelyProblematic 처리에 필요한 업무 로직을 수행한다. */
    private boolean podIsLikelyProblematic(Pod pod) {
        if (pod.getStatus() == null) {
            return true;
        }
        if (!"Running".equals(pod.getStatus().getPhase()) && !"Succeeded".equals(pod.getStatus().getPhase())) {
            return true;
        }
        return pod.getStatus().getContainerStatuses() != null && pod.getStatus().getContainerStatuses().stream()
                .anyMatch(status -> valueOrZero(status.getRestartCount()) > 0
                        || !Boolean.TRUE.equals(status.getReady())
                        || (status.getState() != null && (status.getState().getWaiting() != null || status.getState().getTerminated() != null)));
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 createClient 처리에 필요한 데이터를 생성하거나 저장한다. */
    private KubernetesClient createClient(KubernetesConnectionCredential credential) {
        if (credential.credentialType() == ClusterCredentialType.KUBECONFIG) {
            Config config = Config.fromKubeconfig(credential.payload());
            applyTimeouts(config);
            return new KubernetesClientBuilder().withConfig(config).build();
        }

        ServiceAccountPayload payload = parseServiceAccountPayload(credential.payload());
        Config config = new ConfigBuilder()
                .withMasterUrl(payload.apiServerUrl())
                .withOauthToken(payload.token())
                .withCaCertData(normalizeCertificateAuthority(payload.caCertificate()))
                .withConnectionTimeout(connectTimeoutMs)
                .withRequestTimeout(requestTimeoutMs)
                .build();
        return new KubernetesClientBuilder().withConfig(config).build();
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 applyTimeouts 처리에 필요한 업무 로직을 수행한다. */
    private void applyTimeouts(Config config) {
        config.setConnectionTimeout(connectTimeoutMs);
        config.setRequestTimeout(requestTimeoutMs);
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 failureDetail 처리에 필요한 업무 로직을 수행한다. */
    private String failureDetail(RuntimeException exception) {
        Throwable rootCause = rootCause(exception);
        String detail = rootCause.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = rootCause.getClass().getSimpleName();
        }
        detail = detail.replaceAll("\\s+", " ");
        if (detail.length() > 300) {
            detail = detail.substring(0, 300) + "...";
        }
        return detail;
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 rootCause 처리에 필요한 업무 로직을 수행한다. */
    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 parseServiceAccountPayload 처리 데이터를 필요한 표현으로 변환한다. */
    private ServiceAccountPayload parseServiceAccountPayload(String payload) {
        try {
            return objectMapper.readValue(payload, ServiceAccountPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 normalizeCertificateAuthority 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeCertificateAuthority(String caCertificate) {
        if (caCertificate == null || caCertificate.isBlank()) {
            return null;
        }
        if (caCertificate.contains("BEGIN CERTIFICATE")) {
            return Base64.getEncoder().encodeToString(caCertificate.getBytes(StandardCharsets.UTF_8));
        }
        return caCertificate;
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 parseInstant 처리 데이터를 필요한 표현으로 변환한다. */
    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 sanitizeText 처리에 필요한 업무 로직을 수행한다. */
    private String sanitizeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = value.lines()
                .map(line -> containsSensitiveToken(line) ? "***" : line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return sanitized.length() > maxLength ? sanitized.substring(0, maxLength) : sanitized;
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 containsSensitiveToken 처리에 필요한 업무 로직을 수행한다. */
    private boolean containsSensitiveToken(String value) {
        return List.of("password", "passwd", "secret", "token", "apikey", "api_key", "accesskey", "privatekey", "credential")
                .stream()
                .anyMatch(token -> value.toLowerCase().contains(token));
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 valueOrZero 처리에 필요한 업무 로직을 수행한다. */
    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    /** Fabric8KubernetesNamespaceDiagnosticsAdapter의 valueOrBlank 처리에 필요한 업무 로직을 수행한다. */
    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }

    private record ServiceAccountPayload(
            String apiServerUrl,
            String caCertificate,
            String token
    ) {
    }
}
