package io.strato.aiops.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;

/** Ollama NDJSON에서 첫 완전한 JSON 응답까지만 받아 불필요한 후속 생성을 취소한다. */
final class OllamaValuesBodySubscriber implements HttpResponse.BodySubscriber<String> {
    private final ObjectMapper mapper;
    private final CompletableFuture<String> result = new CompletableFuture<>();
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();
    private final StringBuilder content = new StringBuilder();
    private Subscription subscription;
    private int bytes, depth;
    private boolean started, quoted, escaped;

    /** 공유 JSON parser를 주입하고 사용자 원문은 로그에 남기지 않는다. */
    OllamaValuesBodySubscriber(ObjectMapper mapper) { this.mapper = mapper; }

    /** HTTP client가 기다릴 완료 결과를 제공한다. */
    @Override public CompletionStage<String> getBody() { return result; }

    /** 한 묶음씩 수신해 backpressure를 유지한다. */
    @Override public void onSubscribe(Subscription value) { subscription = value; value.request(1); }

    /** UTF-8 문자는 NDJSON 한 줄이 완성된 뒤 해석하여 네트워크 조각 경계에서 깨지지 않게 한다. */
    @Override public void onNext(List<ByteBuffer> buffers) {
        try {
            for (ByteBuffer buffer : buffers) while (buffer.hasRemaining() && !result.isDone()) {
                if (++bytes > 4_000_000) throw new IllegalArgumentException("AI stream exceeded its size limit");
                byte value = buffer.get();
                if (value == '\n') acceptLine(); else line.write(value);
            }
            if (!result.isDone()) subscription.request(1);
        } catch (Exception failure) { fail(); }
    }

    /** 줄 단위 응답에서 content만 읽으며 별도 thinking trace는 보관하지 않는다. */
    private void acceptLine() throws java.io.IOException {
        if (line.size() == 0) return;
        var packet = mapper.readTree(line.toString(StandardCharsets.UTF_8));
        line.reset();
        if (packet.has("error")) throw new IllegalArgumentException("AI stream failed");
        String fragment = packet.path("message").path("content").asText("");
        for (int i = 0; i < fragment.length() && !result.isDone(); i++) acceptCharacter(fragment.charAt(i));
        if (packet.path("done").asBoolean() && !result.isDone()) fail();
    }

    /** 문자열 내부 괄호와 escape를 구분해 최상위 JSON object의 정확한 끝에서 종료한다. */
    private void acceptCharacter(char value) throws java.io.IOException {
        if (!started) {
            if (Character.isWhitespace(value)) return;
            if (value != '{') throw new IllegalArgumentException("AI JSON object required");
            started = true;
        }
        content.append(value);
        if (content.length() > 48_000) throw new IllegalArgumentException("AI JSON exceeded its size limit");
        if (quoted) {
            if (escaped) escaped = false;
            else if (value == '\\') escaped = true;
            else if (value == '"') quoted = false;
        } else if (value == '"') quoted = true;
        else if (value == '{' || value == '[') depth++;
        else if (value == '}' || value == ']') {
            if (--depth == 0) {
                try (var parser = mapper.createParser(content.toString())) {
                    parser.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
                    if (!mapper.readTree(parser).isObject() || parser.nextToken() != null)
                        throw new IllegalArgumentException("AI JSON object required");
                }
                result.complete(content.toString());
                subscription.cancel();
            }
        }
    }

    /** 원문이나 provider 예외를 노출하지 않고 제한된 실패를 반환한다. */
    private void fail() {
        result.completeExceptionally(new IllegalStateException("AI did not return a complete JSON object"));
        if (subscription != null) subscription.cancel();
    }

    /** transport 실패 시 부분 결과를 성공으로 반환하지 않는다. */
    @Override public void onError(Throwable ignored) { fail(); }

    /** 마지막 줄의 개행이 생략됐어도 처리하되 미완성 JSON은 거부한다. */
    @Override public void onComplete() {
        if (result.isDone()) return;
        try { acceptLine(); } catch (Exception ignored) { fail(); }
        if (!result.isDone()) fail();
    }
}
