package io.strato.aiops.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface KubernetesWatchPort {

    WatchRegistration watch(UUID clusterId, KubernetesConnectionCredential credential, WatchListener listener);

    default WatchRegistration watch(UUID clusterId, KubernetesConnectionCredential credential,
                                    WatchCursor cursor, WatchListener listener) {
        return watch(clusterId, credential, listener);
    }

    default PollResult poll(UUID clusterId, KubernetesConnectionCredential credential, WatchCursor cursor) {
        throw new UnsupportedOperationException("Kubernetes polling is not supported");
    }

    interface WatchRegistration extends AutoCloseable {
        @Override
        void close();
    }

    interface WatchListener {
        void onSignal(CollectedWatchSignal signal);

        void onClosed(String message);

        default void onHeartbeat(Instant observedAt) {
        }

        default void onCheckpoint(String podResourceVersion, String eventResourceVersion,
                                  Instant reconciledAt, int gapSignalCount) {
        }
    }

    record WatchCursor(String podResourceVersion, String eventResourceVersion) {
        public static WatchCursor empty() {
            return new WatchCursor(null, null);
        }
    }

    record PollResult(
            List<CollectedWatchSignal> signals,
            String podResourceVersion,
            String eventResourceVersion,
            Instant collectedAt
    ) {
        public PollResult {
            signals = signals == null ? List.of() : List.copyOf(signals);
        }
    }

    record CollectedWatchSignal(
            String namespace,
            String resourceKind,
            String resourceName,
            String action,
            String reason,
            String status,
            String summary,
            Instant observedAt
    ) {
    }
}
