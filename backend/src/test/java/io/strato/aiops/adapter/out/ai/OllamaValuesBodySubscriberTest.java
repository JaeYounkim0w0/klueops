package io.strato.aiops.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Flow;
import static org.assertj.core.api.Assertions.*;

class OllamaValuesBodySubscriberTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /** UTF-8 경계와 문자열 안의 괄호를 보존하고 JSON 뒤 무한 출력은 취소한다. */
    @Test void stopsAtFirstCompleteJsonAcrossNetworkBoundaries() throws Exception {
        var reader = new OllamaValuesBodySubscriber(mapper);
        boolean[] canceled = {false};
        reader.onSubscribe(subscription(canceled));
        String expected = "{\"text\":\"한글 😀 } \\\"\",\"value\":{\"port\":80}}";
        String packet = mapper.writeValueAsString(Map.of("message", Map.of("content", expected + "]}unwanted"), "done", false)) + "\n";
        for (byte value : packet.getBytes(StandardCharsets.UTF_8)) reader.onNext(List.of(ByteBuffer.wrap(new byte[]{value})));
        assertThat(reader.getBody().toCompletableFuture().join()).isEqualTo(expected);
        assertThat(canceled[0]).isTrue();
    }

    /** 중복 키와 미완성 응답은 제안으로 반환하지 않는다. */
    @Test void rejectsDuplicateKeysAndIncompleteJson() throws Exception {
        for (String content : List.of("{\"x\":1,\"x\":2}", "{\"x\":")) {
            var reader = new OllamaValuesBodySubscriber(mapper);
            reader.onSubscribe(subscription(new boolean[1]));
            String packet = mapper.writeValueAsString(Map.of("message", Map.of("content", content), "done", true));
            reader.onNext(List.of(ByteBuffer.wrap(packet.getBytes(StandardCharsets.UTF_8))));
            reader.onComplete();
            assertThat(reader.getBody().toCompletableFuture()).isCompletedExceptionally();
        }
    }

    /** 네트워크 요청 없이 수신 제어 동작을 관찰한다. */
    private Flow.Subscription subscription(boolean[] canceled) {
        return new Flow.Subscription() {
            /** 시험 데이터는 직접 전달하므로 추가 수신 요청만 허용한다. */
            public void request(long count) { }
            /** 완성된 응답 이후 취소 여부를 기록한다. */
            public void cancel() { canceled[0] = true; }
        };
    }
}
