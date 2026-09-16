package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesStateInventory;
import io.strato.aiops.application.port.out.KubernetesStateSyncPort;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class Fabric8KubernetesStateSyncAdapter implements KubernetesStateSyncPort {

    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;

    /** Fabric8KubernetesStateSyncAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Fabric8KubernetesStateSyncAdapter(
            ObjectMapper objectMapper,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    /** Fabric8KubernetesStateSyncAdapter의 collectClusterInventory 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    public KubernetesStateInventory collectClusterInventory(KubernetesConnectionCredential credential) {
        try (KubernetesClient client = createClient(credential)) {
            List<KubernetesResourceSnapshot.CollectedResource> resources = new ArrayList<>();
            List<KubernetesEventSnapshot.CollectedEvent> events = new ArrayList<>();
            Instant collectedAt = Instant.now();

            client.namespaces().list().getItems()
                    .forEach(namespace -> resources.add(resource("Namespace", namespace, phase(namespace.getStatus()), collectedAt,
                            Map.of("phase", valueOrBlank(phase(namespace.getStatus()))))));

            client.apps().deployments().inAnyNamespace().list().getItems()
                    .forEach(deployment -> resources.add(resource("Deployment", deployment, replicaStatus(
                                    deployment.getStatus() == null ? null : deployment.getStatus().getAvailableReplicas(),
                                    deployment.getSpec() == null ? null : deployment.getSpec().getReplicas()),
                            collectedAt,
                            summary(
                                    "availableReplicas", valueOrZero(deployment.getStatus() == null ? null : deployment.getStatus().getAvailableReplicas()),
                                    "desiredReplicas", valueOrZero(deployment.getSpec() == null ? null : deployment.getSpec().getReplicas()),
                                    "selector", deployment.getSpec() == null || deployment.getSpec().getSelector() == null
                                            ? Map.of() : nullSafeMap(deployment.getSpec().getSelector().getMatchLabels()),
                                    "templateLabels", deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                                            || deployment.getSpec().getTemplate().getMetadata() == null ? Map.of()
                                            : nullSafeMap(deployment.getSpec().getTemplate().getMetadata().getLabels()),
                                    "containers", deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                                            ? List.of() : containerSummaries(deployment.getSpec().getTemplate().getSpec()),
                                    "volumes", deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                                            ? List.of() : volumeSummaries(deployment.getSpec().getTemplate().getSpec())
                            ))));

            client.apps().replicaSets().inAnyNamespace().list().getItems()
                    .forEach(replicaSet -> resources.add(resource("ReplicaSet", replicaSet, replicaStatus(
                                    replicaSet.getStatus() == null ? null : replicaSet.getStatus().getAvailableReplicas(),
                                    replicaSet.getSpec() == null ? null : replicaSet.getSpec().getReplicas()),
                            collectedAt,
                            summary(
                                    "availableReplicas", valueOrZero(replicaSet.getStatus() == null ? null : replicaSet.getStatus().getAvailableReplicas()),
                                    "desiredReplicas", valueOrZero(replicaSet.getSpec() == null ? null : replicaSet.getSpec().getReplicas()),
                                    "containers", replicaSet.getSpec() == null || replicaSet.getSpec().getTemplate() == null
                                            ? List.of() : containerSummaries(replicaSet.getSpec().getTemplate().getSpec()),
                                    "volumes", replicaSet.getSpec() == null || replicaSet.getSpec().getTemplate() == null
                                            ? List.of() : volumeSummaries(replicaSet.getSpec().getTemplate().getSpec())
                            ))));

            client.apps().statefulSets().inAnyNamespace().list().getItems()
                    .forEach(statefulSet -> resources.add(resource("StatefulSet", statefulSet, replicaStatus(
                                    statefulSet.getStatus() == null ? null : statefulSet.getStatus().getReadyReplicas(),
                                    statefulSet.getSpec() == null ? null : statefulSet.getSpec().getReplicas()),
                            collectedAt,
                            summary(
                                    "readyReplicas", valueOrZero(statefulSet.getStatus() == null ? null : statefulSet.getStatus().getReadyReplicas()),
                                    "desiredReplicas", valueOrZero(statefulSet.getSpec() == null ? null : statefulSet.getSpec().getReplicas()),
                                    "templateLabels", statefulSet.getSpec() == null || statefulSet.getSpec().getTemplate() == null
                                            || statefulSet.getSpec().getTemplate().getMetadata() == null ? Map.of()
                                            : nullSafeMap(statefulSet.getSpec().getTemplate().getMetadata().getLabels()),
                                    "containers", statefulSet.getSpec() == null || statefulSet.getSpec().getTemplate() == null
                                            ? List.of() : containerSummaries(statefulSet.getSpec().getTemplate().getSpec()),
                                    "volumes", statefulSet.getSpec() == null || statefulSet.getSpec().getTemplate() == null
                                            ? List.of() : volumeSummaries(statefulSet.getSpec().getTemplate().getSpec())
                            ))));

            client.apps().daemonSets().inAnyNamespace().list().getItems()
                    .forEach(daemonSet -> resources.add(resource("DaemonSet", daemonSet, replicaStatus(
                                    daemonSet.getStatus() == null ? null : daemonSet.getStatus().getNumberReady(),
                                    daemonSet.getStatus() == null ? null : daemonSet.getStatus().getDesiredNumberScheduled()),
                            collectedAt,
                            summary(
                                    "ready", valueOrZero(daemonSet.getStatus() == null ? null : daemonSet.getStatus().getNumberReady()),
                                    "desired", valueOrZero(daemonSet.getStatus() == null ? null : daemonSet.getStatus().getDesiredNumberScheduled()),
                                    "containers", daemonSet.getSpec() == null || daemonSet.getSpec().getTemplate() == null
                                            ? List.of() : containerSummaries(daemonSet.getSpec().getTemplate().getSpec()),
                                    "volumes", daemonSet.getSpec() == null || daemonSet.getSpec().getTemplate() == null
                                            ? List.of() : volumeSummaries(daemonSet.getSpec().getTemplate().getSpec())
                            ))));

            client.batch().v1().jobs().inAnyNamespace().list().getItems()
                    .forEach(job -> resources.add(resource("Job", job, jobStatus(
                                    job.getStatus() == null ? null : job.getStatus().getSucceeded(),
                                    job.getSpec() == null ? null : job.getSpec().getCompletions()),
                            collectedAt,
                            Map.of(
                                    "succeeded", valueOrZero(job.getStatus() == null ? null : job.getStatus().getSucceeded()),
                                    "failed", valueOrZero(job.getStatus() == null ? null : job.getStatus().getFailed()),
                                    "completions", valueOrZero(job.getSpec() == null ? null : job.getSpec().getCompletions())
                            ))));

            client.batch().v1().cronjobs().inAnyNamespace().list().getItems()
                    .forEach(cronJob -> resources.add(resource("CronJob", cronJob,
                            cronJob.getSpec() != null && Boolean.TRUE.equals(cronJob.getSpec().getSuspend()) ? "SUSPENDED" : "ACTIVE",
                            collectedAt,
                            Map.of(
                                    "schedule", valueOrBlank(cronJob.getSpec() == null ? null : cronJob.getSpec().getSchedule()),
                                    "suspend", cronJob.getSpec() != null && Boolean.TRUE.equals(cronJob.getSpec().getSuspend())
                            ))));

            client.pods().inAnyNamespace().list().getItems()
                    .forEach(pod -> resources.add(resource("Pod", pod, phase(pod.getStatus()), collectedAt,
                            summary(
                                    "phase", valueOrBlank(phase(pod.getStatus())),
                                    "restartCount", pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                                            ? 0
                                            : pod.getStatus().getContainerStatuses().stream().mapToInt(status -> status.getRestartCount() == null ? 0 : status.getRestartCount()).sum(),
                                    "qosClass", valueOrBlank(pod.getStatus() == null ? null : pod.getStatus().getQosClass()),
                                    "nodeName", valueOrBlank(pod.getSpec() == null ? null : pod.getSpec().getNodeName()),
                                    "labels", pod.getMetadata() == null ? Map.of() : nullSafeMap(pod.getMetadata().getLabels()),
                                    "containers", containerSummaries(pod.getSpec()),
                                    "volumes", volumeSummaries(pod.getSpec())
                            ))));

            client.services().inAnyNamespace().list().getItems()
                    .forEach(service -> resources.add(resource("Service", service, service.getSpec() == null ? null : service.getSpec().getType(), collectedAt,
                            summary(
                                    "type", valueOrBlank(service.getSpec() == null ? null : service.getSpec().getType()),
                                    "selector", service.getSpec() == null ? Map.of() : nullSafeMap(service.getSpec().getSelector()),
                                    "ports", service.getSpec() == null || service.getSpec().getPorts() == null ? List.of()
                                            : service.getSpec().getPorts().stream().map(port -> summary(
                                            "name", valueOrBlank(port.getName()),
                                            "port", port.getPort(),
                                            "targetPort", targetPortValue(port.getTargetPort()),
                                            "protocol", valueOrBlank(port.getProtocol())
                                    )).toList()
                            ))));

            client.network().v1().ingresses().inAnyNamespace().list().getItems()
                    .forEach(ingress -> resources.add(resource("Ingress", ingress, "ACTIVE", collectedAt,
                            summary(
                                    "className", valueOrBlank(ingress.getSpec() == null ? null : ingress.getSpec().getIngressClassName()),
                                    "backends", ingressBackends(ingress)
                            ))));

            client.configMaps().inAnyNamespace().list().getItems()
                    .forEach(configMap -> resources.add(resource("ConfigMap", configMap, "ACTIVE", collectedAt,
                            Map.of(
                                    "dataKeys", keys(configMap.getData()),
                                    "binaryDataKeys", keys(configMap.getBinaryData())
                            ))));

            client.secrets().inAnyNamespace().list().getItems()
                    .forEach(secret -> resources.add(resource("Secret", secret, valueOrBlank(secret.getType()), collectedAt,
                            Map.of(
                                    "type", valueOrBlank(secret.getType()),
                                    "dataKeys", keys(secret.getData())
                            ))));

            client.serviceAccounts().inAnyNamespace().list().getItems()
                    .forEach(serviceAccount -> resources.add(resource("ServiceAccount", serviceAccount, "ACTIVE", collectedAt,
                            Map.of("secretRefs", serviceAccount.getSecrets() == null ? 0 : serviceAccount.getSecrets().size()))));

            client.network().v1().networkPolicies().inAnyNamespace().list().getItems()
                    .forEach(networkPolicy -> resources.add(resource("NetworkPolicy", networkPolicy, "ACTIVE", collectedAt,
                            Map.of(
                                    "policyTypes", networkPolicy.getSpec() == null || networkPolicy.getSpec().getPolicyTypes() == null
                                            ? List.of()
                                            : networkPolicy.getSpec().getPolicyTypes(),
                                    "podSelector", networkPolicy.getSpec() == null ? "" : writeJson(Map.of("matchLabels",
                                            networkPolicy.getSpec().getPodSelector() == null || networkPolicy.getSpec().getPodSelector().getMatchLabels() == null
                                                    ? Map.of()
                                                    : networkPolicy.getSpec().getPodSelector().getMatchLabels()))
                            ))));

            client.endpoints().inAnyNamespace().list().getItems()
                    .forEach(endpoint -> resources.add(resource("Endpoint", endpoint, "ACTIVE", collectedAt, summary(
                            "readyAddresses", endpoint.getSubsets() == null ? 0 : endpoint.getSubsets().stream()
                                    .mapToInt(subset -> subset.getAddresses() == null ? 0 : subset.getAddresses().size()).sum(),
                            "notReadyAddresses", endpoint.getSubsets() == null ? 0 : endpoint.getSubsets().stream()
                                    .mapToInt(subset -> subset.getNotReadyAddresses() == null ? 0 : subset.getNotReadyAddresses().size()).sum()
                    ))));

            client.persistentVolumeClaims().inAnyNamespace().list().getItems()
                    .forEach(pvc -> resources.add(resource("PersistentVolumeClaim", pvc, phase(pvc.getStatus()), collectedAt,
                            Map.of("phase", valueOrBlank(phase(pvc.getStatus()))))));

            client.persistentVolumes().list().getItems()
                    .forEach(pv -> resources.add(resource("PersistentVolume", pv, phase(pv.getStatus()), collectedAt,
                            Map.of("phase", valueOrBlank(phase(pv.getStatus()))))));

            client.autoscaling().v2().horizontalPodAutoscalers().inAnyNamespace().list().getItems()
                    .forEach(hpa -> resources.add(resource("HorizontalPodAutoscaler", hpa, "ACTIVE", collectedAt, summary(
                            "targetKind", hpa.getSpec() == null || hpa.getSpec().getScaleTargetRef() == null ? ""
                                    : valueOrBlank(hpa.getSpec().getScaleTargetRef().getKind()),
                            "targetName", hpa.getSpec() == null || hpa.getSpec().getScaleTargetRef() == null ? ""
                                    : valueOrBlank(hpa.getSpec().getScaleTargetRef().getName()),
                            "minReplicas", valueOrZero(hpa.getSpec() == null ? null : hpa.getSpec().getMinReplicas()),
                            "maxReplicas", valueOrZero(hpa.getSpec() == null ? null : hpa.getSpec().getMaxReplicas())
                    ))));

            client.policy().v1().podDisruptionBudget().inAnyNamespace().list().getItems()
                    .forEach(pdb -> resources.add(resource("PodDisruptionBudget", pdb, "ACTIVE", collectedAt, summary(
                            "currentHealthy", valueOrZero(pdb.getStatus() == null ? null : pdb.getStatus().getCurrentHealthy()),
                            "desiredHealthy", valueOrZero(pdb.getStatus() == null ? null : pdb.getStatus().getDesiredHealthy()),
                            "disruptionsAllowed", valueOrZero(pdb.getStatus() == null ? null : pdb.getStatus().getDisruptionsAllowed())
                            , "selector", pdb.getSpec() == null || pdb.getSpec().getSelector() == null
                                    ? Map.of() : nullSafeMap(pdb.getSpec().getSelector().getMatchLabels())
                    ))));

            client.resourceQuotas().inAnyNamespace().list().getItems()
                    .forEach(quota -> resources.add(resource("ResourceQuota", quota, "ACTIVE", collectedAt, summary(
                            "hardKeys", quota.getStatus() == null ? List.of() : keys(quota.getStatus().getHard()),
                            "usedKeys", quota.getStatus() == null ? List.of() : keys(quota.getStatus().getUsed())
                    ))));

            client.limitRanges().inAnyNamespace().list().getItems()
                    .forEach(limitRange -> resources.add(resource("LimitRange", limitRange, "ACTIVE", collectedAt, summary(
                            "itemCount", limitRange.getSpec() == null || limitRange.getSpec().getLimits() == null
                                    ? 0 : limitRange.getSpec().getLimits().size()
                    ))));

            client.v1().events().inAnyNamespace().list().getItems()
                    .forEach(event -> events.add(new KubernetesEventSnapshot.CollectedEvent(
                            event.getMetadata() == null ? null : event.getMetadata().getNamespace(),
                            event.getInvolvedObject() == null ? null : event.getInvolvedObject().getKind(),
                            event.getInvolvedObject() == null ? null : event.getInvolvedObject().getName(),
                            event.getReason(),
                            event.getType(),
                            sanitizeEventMessage(event.getMessage()),
                            parseInstant(event.getLastTimestamp()),
                            event.getCount(),
                            collectedAt
                    )));

            return new KubernetesStateInventory(resources, events);
        }
    }

    /** Fabric8KubernetesStateSyncAdapter의 resource 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesResourceSnapshot.CollectedResource resource(String resourceType, HasMetadata resource, String status,
                                                                  Instant collectedAt, Map<String, Object> summary) {
        Map<String, Object> enrichedSummary = new LinkedHashMap<>(summary);
        enrichedSummary.put("ownerReferences", ownerReferences(resource));
        return new KubernetesResourceSnapshot.CollectedResource(
                resource.getMetadata() == null ? null : resource.getMetadata().getNamespace(),
                resourceType,
                resource.getMetadata() == null ? "" : resource.getMetadata().getName(),
                resource.getMetadata() == null ? null : resource.getMetadata().getUid(),
                status,
                writeJson(enrichedSummary),
                null,
                false,
                collectedAt
        );
    }

    /** Fabric8KubernetesStateSyncAdapter의 ownerReferences 처리에 필요한 업무 로직을 수행한다. */
    private List<Map<String, Object>> ownerReferences(HasMetadata resource) {
        if (resource.getMetadata() == null || resource.getMetadata().getOwnerReferences() == null) {
            return List.of();
        }
        return resource.getMetadata().getOwnerReferences().stream()
                .map(owner -> summary(
                        "kind", valueOrBlank(owner.getKind()),
                        "name", valueOrBlank(owner.getName()),
                        "uid", valueOrBlank(owner.getUid()),
                        "controller", Boolean.TRUE.equals(owner.getController())
                )).toList();
    }

    /** Fabric8KubernetesStateSyncAdapter의 ingressBackends 처리에 필요한 업무 로직을 수행한다. */
    private List<Map<String, Object>> ingressBackends(io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress) {
        List<Map<String, Object>> backends = new ArrayList<>();
        if (ingress.getSpec() == null) {
            return backends;
        }
        if (ingress.getSpec().getDefaultBackend() != null && ingress.getSpec().getDefaultBackend().getService() != null) {
            backends.add(summary("serviceName", valueOrBlank(
                    ingress.getSpec().getDefaultBackend().getService().getName()), "source", "defaultBackend"));
        }
        if (ingress.getSpec().getRules() != null) {
            ingress.getSpec().getRules().forEach(rule -> {
                if (rule.getHttp() == null || rule.getHttp().getPaths() == null) {
                    return;
                }
                rule.getHttp().getPaths().forEach(path -> {
                    if (path.getBackend() != null && path.getBackend().getService() != null) {
                        backends.add(summary(
                                "serviceName", valueOrBlank(path.getBackend().getService().getName()),
                                "host", valueOrBlank(rule.getHost()),
                                "path", valueOrBlank(path.getPath())
                        ));
                    }
                });
            });
        }
        return List.copyOf(backends);
    }

    /** Fabric8KubernetesStateSyncAdapter의 summary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> summary(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    /** Fabric8KubernetesStateSyncAdapter의 containerSummaries 처리에 필요한 업무 로직을 수행한다. */
    private List<Map<String, Object>> containerSummaries(PodSpec podSpec) {
        if (podSpec == null || podSpec.getContainers() == null) {
            return List.of();
        }
        return podSpec.getContainers().stream().map(this::containerSummary).toList();
    }

    /** Fabric8KubernetesStateSyncAdapter의 volumeSummaries 처리에 필요한 업무 로직을 수행한다. */
    private List<Map<String, Object>> volumeSummaries(PodSpec podSpec) {
        if (podSpec == null || podSpec.getVolumes() == null) {
            return List.of();
        }
        return podSpec.getVolumes().stream().map(volume -> {
            if (volume.getConfigMap() != null) {
                return summary("name", valueOrBlank(volume.getName()), "sourceKind", "ConfigMap",
                        "sourceName", valueOrBlank(volume.getConfigMap().getName()));
            }
            if (volume.getSecret() != null) {
                return summary("name", valueOrBlank(volume.getName()), "sourceKind", "Secret",
                        "sourceName", valueOrBlank(volume.getSecret().getSecretName()));
            }
            if (volume.getPersistentVolumeClaim() != null) {
                return summary("name", valueOrBlank(volume.getName()), "sourceKind", "PersistentVolumeClaim",
                        "sourceName", valueOrBlank(volume.getPersistentVolumeClaim().getClaimName()));
            }
            return summary("name", valueOrBlank(volume.getName()), "sourceKind", "Other", "sourceName", "");
        }).toList();
    }

    /** Fabric8KubernetesStateSyncAdapter의 containerSummary 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, Object> containerSummary(Container container) {
        return summary(
                "name", valueOrBlank(container.getName()),
                "image", sanitizeImage(container.getImage()),
                "imagePullPolicy", valueOrBlank(container.getImagePullPolicy()),
                "requests", container.getResources() == null ? Map.of() : quantityMap(container.getResources().getRequests()),
                "limits", container.getResources() == null ? Map.of() : quantityMap(container.getResources().getLimits()),
                "hasLivenessProbe", container.getLivenessProbe() != null,
                "hasReadinessProbe", container.getReadinessProbe() != null,
                "hasStartupProbe", container.getStartupProbe() != null,
                "ports", container.getPorts() == null ? List.of() : container.getPorts().stream().map(port -> summary(
                        "name", valueOrBlank(port.getName()),
                        "containerPort", port.getContainerPort(),
                        "protocol", valueOrBlank(port.getProtocol())
                )).toList()
        );
    }

    /** Fabric8KubernetesStateSyncAdapter의 quantityMap 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, String> quantityMap(Map<String, io.fabric8.kubernetes.api.model.Quantity> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, quantity) -> result.put(key,
                quantity == null ? "" : valueOrBlank(quantity.getAmount()) + valueOrBlank(quantity.getFormat())));
        return result;
    }

    /** Fabric8KubernetesStateSyncAdapter의 nullSafeMap 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, String> nullSafeMap(Map<String, String> value) {
        return value == null ? Map.of() : value;
    }

    /** Fabric8KubernetesStateSyncAdapter의 targetPortValue 처리에 필요한 업무 로직을 수행한다. */
    private String targetPortValue(IntOrString targetPort) {
        if (targetPort == null) {
            return "";
        }
        if (targetPort.getStrVal() != null && !targetPort.getStrVal().isBlank()) {
            return targetPort.getStrVal();
        }
        return targetPort.getIntVal() == null ? "" : String.valueOf(targetPort.getIntVal());
    }

    /** Fabric8KubernetesStateSyncAdapter의 sanitizeImage 처리에 필요한 업무 로직을 수행한다. */
    private String sanitizeImage(String image) {
        if (image == null) {
            return "";
        }
        return image.replaceAll("(https?://)[^/@:]+:[^/@]+@", "$1***:***@");
    }

    /** Fabric8KubernetesStateSyncAdapter의 writeJson 처리에 필요한 업무 로직을 수행한다. */
    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Kubernetes resource summary", exception);
        }
    }

    /** Fabric8KubernetesStateSyncAdapter의 createClient 처리에 필요한 데이터를 생성하거나 저장한다. */
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

    /** Fabric8KubernetesStateSyncAdapter의 applyTimeouts 처리에 필요한 업무 로직을 수행한다. */
    private void applyTimeouts(Config config) {
        config.setConnectionTimeout(connectTimeoutMs);
        config.setRequestTimeout(requestTimeoutMs);
    }

    /** Fabric8KubernetesStateSyncAdapter의 parseServiceAccountPayload 처리 데이터를 필요한 표현으로 변환한다. */
    private ServiceAccountPayload parseServiceAccountPayload(String payload) {
        try {
            return objectMapper.readValue(payload, ServiceAccountPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    /** Fabric8KubernetesStateSyncAdapter의 normalizeCertificateAuthority 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeCertificateAuthority(String caCertificate) {
        if (caCertificate == null || caCertificate.isBlank()) {
            return null;
        }
        if (caCertificate.contains("BEGIN CERTIFICATE")) {
            return Base64.getEncoder().encodeToString(caCertificate.getBytes(StandardCharsets.UTF_8));
        }
        return caCertificate;
    }

    /** Fabric8KubernetesStateSyncAdapter의 phase 처리에 필요한 업무 로직을 수행한다. */
    private String phase(Object status) {
        if (status == null) {
            return null;
        }
        try {
            Object phase = status.getClass().getMethod("getPhase").invoke(status);
            return phase == null ? null : phase.toString();
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    /** Fabric8KubernetesStateSyncAdapter의 replicaStatus 처리에 필요한 업무 로직을 수행한다. */
    private String replicaStatus(Integer availableReplicas, Integer desiredReplicas) {
        return valueOrZero(availableReplicas) + "/" + valueOrZero(desiredReplicas);
    }

    /** Fabric8KubernetesStateSyncAdapter의 jobStatus 처리에 필요한 업무 로직을 수행한다. */
    private String jobStatus(Integer succeeded, Integer completions) {
        return valueOrZero(succeeded) + "/" + valueOrZero(completions);
    }

    /** Fabric8KubernetesStateSyncAdapter의 valueOrZero 처리에 필요한 업무 로직을 수행한다. */
    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    /** Fabric8KubernetesStateSyncAdapter의 valueOrBlank 처리에 필요한 업무 로직을 수행한다. */
    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }

    /** Fabric8KubernetesStateSyncAdapter의 keys 처리에 필요한 업무 로직을 수행한다. */
    private List<String> keys(Map<String, ?> value) {
        return value == null || value.isEmpty() ? List.of() : List.copyOf(value.keySet());
    }

    /** Fabric8KubernetesStateSyncAdapter의 parseInstant 처리 데이터를 필요한 표현으로 변환한다. */
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

    /** Fabric8KubernetesStateSyncAdapter의 sanitizeEventMessage 처리에 필요한 업무 로직을 수행한다. */
    private String sanitizeEventMessage(String message) {
        if (message == null) {
            return null;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("password") || normalized.contains("secret") || normalized.contains("token") || normalized.contains("credential")) {
            return "***";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private record ServiceAccountPayload(
            String apiServerUrl,
            String caCertificate,
            String token
    ) {
    }
}
