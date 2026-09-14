package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesWatchPort;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class Fabric8KubernetesWatchAdapter implements KubernetesWatchPort {

    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;

    public Fabric8KubernetesWatchAdapter(ObjectMapper objectMapper,
                                         @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
                                         @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs) {
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    @Override
    public WatchRegistration watch(java.util.UUID clusterId, KubernetesConnectionCredential credential,
                                   WatchListener listener) {
        return watch(clusterId, credential, WatchCursor.empty(), listener);
    }

    @Override
    public WatchRegistration watch(java.util.UUID clusterId, KubernetesConnectionCredential credential,
                                   WatchCursor cursor, WatchListener listener) {
        KubernetesClient client = createClient(credential);
        AtomicBoolean closed = new AtomicBoolean(false);
        try {
            PollResult initial = poll(client);
            initial.signals().forEach(listener::onSignal);
            String podVersion = initial.podResourceVersion();
            String eventVersion = initial.eventResourceVersion();
            listener.onCheckpoint(podVersion, eventVersion, initial.collectedAt(), initial.signals().size());
            Watch podWatch = client.pods().inAnyNamespace().withResourceVersion(podVersion)
                    .watch(new Watcher<>() {
                        @Override
                        public void eventReceived(Action action, Pod pod) {
                            listener.onHeartbeat(Instant.now());
                            if (pod == null || pod.getMetadata() == null) return;
                            if (podNeedsAttention(pod)) {
                                listener.onSignal(podSignal(action, pod, action.name()));
                            }
                            listener.onCheckpoint(pod.getMetadata().getResourceVersion(), null, null, 0);
                        }

                        @Override
                        public void onClose(WatcherException cause) {
                            notifyClosed(closed, listener, cause);
                        }
                    });
            Watch eventWatch = client.v1().events().inAnyNamespace().withResourceVersion(eventVersion)
                    .watch(new Watcher<>() {
                        @Override
                        public void eventReceived(Action action, Event event) {
                            listener.onHeartbeat(Instant.now());
                            if (event == null || event.getMetadata() == null || event.getInvolvedObject() == null) return;
                            if ("Warning".equalsIgnoreCase(event.getType())) {
                                listener.onSignal(eventSignal(action, event, action.name()));
                            }
                            listener.onCheckpoint(null, event.getMetadata().getResourceVersion(), null, 0);
                        }

                        @Override
                        public void onClose(WatcherException cause) {
                            notifyClosed(closed, listener, cause);
                        }
                    });
            return () -> {
                closed.set(true);
                try {
                    podWatch.close();
                } finally {
                    try {
                        eventWatch.close();
                    } finally {
                        client.close();
                    }
                }
            };
        } catch (RuntimeException exception) {
            client.close();
            throw exception;
        }
    }

    @Override
    public PollResult poll(java.util.UUID clusterId, KubernetesConnectionCredential credential, WatchCursor cursor) {
        try (KubernetesClient client = createClient(credential)) {
            return poll(client);
        }
    }

    private PollResult poll(KubernetesClient client) {
        var boundedList = new ListOptionsBuilder().withLimit(500L).build();
        var podList = client.pods().inAnyNamespace().list(boundedList);
        var eventList = client.v1().events().inAnyNamespace().list(boundedList);
        List<CollectedWatchSignal> signals = new ArrayList<>();
        for (Pod pod : podList.getItems()) {
            if (podNeedsAttention(pod)) {
                signals.add(podSignal(Watcher.Action.MODIFIED, pod, "POLL_RECONCILED"));
            }
        }
        for (Event event : eventList.getItems()) {
            if (event != null && "Warning".equalsIgnoreCase(event.getType())) {
                signals.add(eventSignal(Watcher.Action.MODIFIED, event, "POLL_RECONCILED"));
            }
        }
        return new PollResult(signals,
                podList.getMetadata() == null ? null : podList.getMetadata().getResourceVersion(),
                eventList.getMetadata() == null ? null : eventList.getMetadata().getResourceVersion(),
                Instant.now());
    }

    private CollectedWatchSignal podSignal(Watcher.Action action, Pod pod, String sourceAction) {
        String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
        int restarts = pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null ? 0
                : pod.getStatus().getContainerStatuses().stream()
                .mapToInt(item -> item.getRestartCount() == null ? 0 : item.getRestartCount()).sum();
        return new CollectedWatchSignal(pod.getMetadata().getNamespace(), "Pod",
                pod.getMetadata().getName(), sourceAction == null ? action.name() : sourceAction, podReason(pod), phase,
                "phase=" + text(phase) + ", restarts=" + restarts, Instant.now());
    }

    private CollectedWatchSignal eventSignal(Watcher.Action action, Event event, String sourceAction) {
        return new CollectedWatchSignal(event.getMetadata().getNamespace(),
                text(event.getInvolvedObject().getKind()), text(event.getInvolvedObject().getName()),
                sourceAction == null ? action.name() : sourceAction, text(event.getReason()), text(event.getType()),
                sanitize(event.getMessage()), Instant.now());
    }

    private boolean podNeedsAttention(Pod pod) {
        if (pod == null || pod.getMetadata() == null) return false;
        String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
        return phase == null || !java.util.Set.of("Running", "Succeeded").contains(phase) || podReason(pod) != null;
    }

    private void notifyClosed(AtomicBoolean closed, WatchListener listener, WatcherException cause) {
        if (closed.compareAndSet(false, true)) {
            listener.onClosed(cause == null ? "Kubernetes watch closed" : sanitize(cause.getMessage()));
        }
    }

    private String podReason(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) return null;
        return pod.getStatus().getContainerStatuses().stream()
                .flatMap(status -> java.util.stream.Stream.of(
                        status.getState() == null ? null : status.getState().getWaiting(),
                        status.getState() == null ? null : status.getState().getTerminated()))
                .filter(java.util.Objects::nonNull)
                .map(state -> {
                    try {
                        Object value = state.getClass().getMethod("getReason").invoke(state);
                        return value == null ? null : String.valueOf(value);
                    } catch (ReflectiveOperationException ignored) {
                        return null;
                    }
                })
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse(null);
    }

    private KubernetesClient createClient(KubernetesConnectionCredential credential) {
        if (credential.credentialType() == ClusterCredentialType.KUBECONFIG) {
            Config config = Config.fromKubeconfig(credential.payload());
            config.setConnectionTimeout(connectTimeoutMs);
            config.setRequestTimeout(requestTimeoutMs);
            return new KubernetesClientBuilder().withConfig(config).build();
        }
        try {
            ServiceAccountPayload payload = objectMapper.readValue(credential.payload(), ServiceAccountPayload.class);
            String ca = payload.caCertificate();
            if (ca != null && ca.contains("BEGIN CERTIFICATE")) {
                ca = Base64.getEncoder().encodeToString(ca.getBytes(StandardCharsets.UTF_8));
            }
            Config config = new ConfigBuilder().withMasterUrl(payload.apiServerUrl()).withOauthToken(payload.token())
                    .withCaCertData(ca).withConnectionTimeout(connectTimeoutMs).withRequestTimeout(requestTimeoutMs).build();
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    private String sanitize(String value) {
        if (value == null) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("password") || lower.contains("token") || lower.contains("credential")) return "***";
        return value.length() > 1800 ? value.substring(0, 1800) : value;
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private record ServiceAccountPayload(String apiServerUrl, String caCertificate, String token) {
    }
}
