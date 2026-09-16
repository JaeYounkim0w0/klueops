package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;

class OperationsEventStreamTest {

    /** OperationsEventStreamTest의 disconnectedEmitterDoesNotBreakHeartbeatScheduler 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void disconnectedEmitterDoesNotBreakHeartbeatScheduler() {
        OperationsEventStream stream = new OperationsEventStream(DisconnectedEmitter::new);

        assertThatCode(stream::subscribe).doesNotThrowAnyException();
        assertThatCode(stream::heartbeat).doesNotThrowAnyException();
    }

    private static final class DisconnectedEmitter extends SseEmitter {
        /** DisconnectedEmitter의 send 처리 결과를 지정된 대상에 전달한다. */
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("client disconnected");
        }

        /** DisconnectedEmitter의 complete 처리에 필요한 업무 로직을 수행한다. */
        @Override
        public synchronized void complete() {
            throw new IllegalStateException("async response already failed");
        }
    }
}
