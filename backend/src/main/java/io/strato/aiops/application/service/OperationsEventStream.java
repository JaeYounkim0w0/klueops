package io.strato.aiops.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Service
public class OperationsEventStream {

    private static final long STREAM_TIMEOUT_MS = 30L * 60L * 1000L;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Supplier<SseEmitter> emitterFactory;

    /** OperationsEventStream 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OperationsEventStream() {
        this(() -> new SseEmitter(STREAM_TIMEOUT_MS));
    }

    /** OperationsEventStream 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    OperationsEventStream(Supplier<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    /** OperationsEventStream의 subscribe 처리에 필요한 업무 로직을 수행한다. */
    public SseEmitter subscribe() {
        SseEmitter emitter = emitterFactory.get();
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // The container may already have completed an errored async response.
            }
        });
        emitter.onError(ignored -> emitters.remove(emitter));
        send(emitter, "connected", Map.of("connectedAt", Instant.now(), "sequence", sequence.get()));
        return emitter;
    }

    /** OperationsEventStream의 publish 처리 결과를 지정된 대상에 전달한다. */
    public void publish(String eventName, Object data) {
        emitters.forEach(emitter -> send(emitter, eventName, data));
    }

    /** OperationsEventStream의 heartbeat 처리에 필요한 업무 로직을 수행한다. */
    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        publish("heartbeat", Map.of("observedAt", Instant.now()));
    }

    /** OperationsEventStream의 send 처리 결과를 지정된 대상에 전달한다. */
    private void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(sequence.incrementAndGet()))
                    .name(eventName)
                    .data(data));
        } catch (IOException | IllegalStateException exception) {
            emitters.remove(emitter);
        }
    }
}
