package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;

class OperationsEventStreamTest {

    @Test
    void disconnectedEmitterDoesNotBreakHeartbeatScheduler() {
        OperationsEventStream stream = new OperationsEventStream(DisconnectedEmitter::new);

        assertThatCode(stream::subscribe).doesNotThrowAnyException();
        assertThatCode(stream::heartbeat).doesNotThrowAnyException();
    }

    private static final class DisconnectedEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("client disconnected");
        }

        @Override
        public synchronized void complete() {
            throw new IllegalStateException("async response already failed");
        }
    }
}
