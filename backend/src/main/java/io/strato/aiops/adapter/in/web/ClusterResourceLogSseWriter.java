package io.strato.aiops.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

final class ClusterResourceLogSseWriter {

    private final OutputStream outputStream;
    private final ObjectMapper objectMapper;
    private final AtomicReference<UncheckedIOException> failure = new AtomicReference<>();

    /** ClusterResourceLogSseWriter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    ClusterResourceLogSseWriter(OutputStream outputStream, ObjectMapper objectMapper) {
        this.outputStream = outputStream;
        this.objectMapper = objectMapper;
    }

    /** ClusterResourceLogSseWriter의 writeJson 처리에 필요한 업무 로직을 수행한다. */
    synchronized void writeJson(String event, Object payload) {
        try {
            writeEvent(event, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize resource log stream event", exception);
        }
    }

    /** ClusterResourceLogSseWriter의 writeHeartbeat 처리에 필요한 업무 로직을 수행한다. */
    synchronized void writeHeartbeat() {
        writeEvent("heartbeat", "keep-alive");
    }

    /** ClusterResourceLogSseWriter의 writeError 처리에 필요한 업무 로직을 수행한다. */
    synchronized void writeError(String message) {
        if (failure.get() == null) {
            writeEvent("error", message == null ? "Resource log stream failed" : message);
        }
    }

    /** ClusterResourceLogSseWriter의 failed 처리에 필요한 업무 로직을 수행한다. */
    boolean failed() {
        return failure.get() != null;
    }

    /** ClusterResourceLogSseWriter의 writeEvent 처리에 필요한 업무 로직을 수행한다. */
    private void writeEvent(String event, String data) {
        UncheckedIOException previous = failure.get();
        if (previous != null) {
            throw previous;
        }
        try {
            outputStream.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
            for (String line : data.split("\\R", -1)) {
                outputStream.write(("data: " + line + "\n").getBytes(StandardCharsets.UTF_8));
            }
            outputStream.write('\n');
            outputStream.flush();
        } catch (IOException exception) {
            UncheckedIOException wrapped = new UncheckedIOException(exception);
            failure.compareAndSet(null, wrapped);
            throw wrapped;
        }
    }
}
