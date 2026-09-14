package io.strato.aiops.adapter.in.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class AiChatSseWriter {

    private final OutputStream outputStream;
    private IOException failure;

    AiChatSseWriter(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    synchronized void writeEvent(String eventType, String value) {
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
        String normalized = value == null ? "" : value.replace("\r", "");
        StringBuilder event = new StringBuilder("event: ").append(eventType).append('\n');
        for (String line : normalized.split("\n", -1)) {
            event.append("data: ").append(line).append('\n');
        }
        event.append('\n');
        try {
            outputStream.write(event.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException exception) {
            failure = exception;
            throw new UncheckedIOException(exception);
        }
    }

    void writeHeartbeat() {
        writeEvent("heartbeat", "keep-alive");
    }
}
