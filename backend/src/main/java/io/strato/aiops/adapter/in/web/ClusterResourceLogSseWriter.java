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

    ClusterResourceLogSseWriter(OutputStream outputStream, ObjectMapper objectMapper) {
        this.outputStream = outputStream;
        this.objectMapper = objectMapper;
    }

    synchronized void writeJson(String event, Object payload) {
        try {
            writeEvent(event, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize resource log stream event", exception);
        }
    }

    synchronized void writeHeartbeat() {
        writeEvent("heartbeat", "keep-alive");
    }

    synchronized void writeError(String message) {
        if (failure.get() == null) {
            writeEvent("error", message == null ? "Resource log stream failed" : message);
        }
    }

    boolean failed() {
        return failure.get() != null;
    }

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
