package io.strato.aiops.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface KubernetesWatchPort {

    /** KubernetesWatchPort의 watch 처리 계약을 정의한다. */
    WatchRegistration watch(UUID clusterId, KubernetesConnectionCredential credential, WatchListener listener);

    /** KubernetesWatchPort의 watch 처리에 필요한 업무 로직을 수행한다. */
    default WatchRegistration watch(UUID clusterId, KubernetesConnectionCredential credential,
                                    WatchCursor cursor, WatchListener listener) {
        return watch(clusterId, credential, listener);
    }

    /** KubernetesWatchPort의 poll 처리에 필요한 업무 로직을 수행한다. */
    default PollResult poll(UUID clusterId, KubernetesConnectionCredential credential, WatchCursor cursor) {
        throw new UnsupportedOperationException("Kubernetes polling is not supported");
    }

    interface WatchRegistration extends AutoCloseable {
        /** WatchRegistration의 close 처리 대상과 관련 상태를 안전하게 정리한다. */
        @Override
        void close();
    }

    interface WatchListener {
        /** WatchListener의 onSignal 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
        void onSignal(CollectedWatchSignal signal);

        /** WatchListener의 onClosed 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
        void onClosed(String message);

        /** WatchListener의 onHeartbeat 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
        default void onHeartbeat(Instant observedAt) {
        }

        /** WatchListener의 onCheckpoint 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
        default void onCheckpoint(String podResourceVersion, String eventResourceVersion,
                                  Instant reconciledAt, int gapSignalCount) {
        }
    }

    record WatchCursor(String podResourceVersion, String eventResourceVersion) {
        /** WatchCursor의 empty 처리에 필요한 업무 로직을 수행한다. */
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
        /** PollResult 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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
