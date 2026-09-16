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

    /** CommandEventStream의 subscribe 처리에 필요한 업무 로직을 수행한다. */
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

    /** CommandEventStream의 output 처리에 필요한 업무 로직을 수행한다. */
    public void output(UUID executionId, String channel, String text) {
        publish(executionId, channel, Map.of("text", text));
    }

    /** CommandEventStream의 status 처리에 필요한 업무 로직을 수행한다. */
    public void status(CommandExecution execution) {
        publish(execution.id(), "status", execution);
        if (execution.completedAt() != null) complete(execution.id());
    }

    /** CommandEventStream의 publish 처리 결과를 지정된 대상에 전달한다. */
    private void publish(UUID executionId, String name, Object data) {
        emitters.getOrDefault(executionId, new CopyOnWriteArrayList<>())
                .forEach(emitter -> send(emitter, name, data));
    }

    /** CommandEventStream의 send 처리 결과를 지정된 대상에 전달한다. */
    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException exception) {
            emitter.complete();
        }
    }

    /** CommandEventStream의 complete 처리에 필요한 업무 로직을 수행한다. */
    private void complete(UUID executionId) {
        CopyOnWriteArrayList<SseEmitter> listeners = emitters.remove(executionId);
        if (listeners != null) listeners.forEach(SseEmitter::complete);
    }

    /** CommandEventStream의 remove 처리 대상과 관련 상태를 안전하게 정리한다. */
    private void remove(UUID executionId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> listeners = emitters.get(executionId);
        if (listeners == null) return;
        listeners.remove(emitter);
        if (listeners.isEmpty()) emitters.remove(executionId, listeners);
    }
}

