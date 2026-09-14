package io.strato.aiops.application.service;

import io.strato.aiops.domain.command.CommandExecution;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class CommandEventStream {
    private static final long TIMEOUT_MS = 16 * 60 * 1_000L;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(CommandExecution execution) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> listeners = emitters.computeIfAbsent(execution.id(), ignored -> new CopyOnWriteArrayList<>());
        listeners.add(emitter);
        emitter.onCompletion(() -> remove(execution.id(), emitter));
        emitter.onTimeout(() -> remove(execution.id(), emitter));
        emitter.onError(ignored -> remove(execution.id(), emitter));
        send(emitter, "snapshot", execution);
        if (execution.completedAt() != null) {
            emitter.complete();
            remove(execution.id(), emitter);
        }
        return emitter;
    }

    public void output(UUID executionId, String channel, String text) {
        publish(executionId, channel, Map.of("text", text));
    }

    public void status(CommandExecution execution) {
        publish(execution.id(), "status", execution);
        if (execution.completedAt() != null) complete(execution.id());
    }

    private void publish(UUID executionId, String name, Object data) {
        emitters.getOrDefault(executionId, new CopyOnWriteArrayList<>())
                .forEach(emitter -> send(emitter, name, data));
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException exception) {
            emitter.complete();
        }
    }

    private void complete(UUID executionId) {
        CopyOnWriteArrayList<SseEmitter> listeners = emitters.remove(executionId);
        if (listeners != null) listeners.forEach(SseEmitter::complete);
    }

    private void remove(UUID executionId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> listeners = emitters.get(executionId);
        if (listeners == null) return;
        listeners.remove(emitter);
        if (listeners.isEmpty()) emitters.remove(executionId, listeners);
    }
}

