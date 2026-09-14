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

    public OperationsEventStream() {
        this(() -> new SseEmitter(STREAM_TIMEOUT_MS));
    }

    OperationsEventStream(Supplier<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

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

    public void publish(String eventName, Object data) {
        emitters.forEach(emitter -> send(emitter, eventName, data));
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        publish("heartbeat", Map.of("observedAt", Instant.now()));
    }

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
