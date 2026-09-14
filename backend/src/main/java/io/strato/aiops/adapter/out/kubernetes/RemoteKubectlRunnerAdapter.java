package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubectlOutputListener;
import io.strato.aiops.application.port.out.KubectlRunRequest;
import io.strato.aiops.application.port.out.KubectlRunResult;
import io.strato.aiops.application.port.out.KubectlRunnerPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "aiops.command-console.runner.mode", havingValue = "remote")
public class RemoteKubectlRunnerAdapter implements KubectlRunnerPort {
    private static final String TOKEN_HEADER = "X-AIOPS-Runner-Token";
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String token;

    public RemoteKubectlRunnerAdapter(ObjectMapper objectMapper,
                                      @Value("${aiops.command-console.runner.url}") URI baseUri,
                                      @Value("${aiops.command-console.runner.token}") String token) {
        this.objectMapper = objectMapper;
        this.baseUri = baseUri;
        this.token = token;
    }

    @Override
    public KubectlRunResult run(KubectlRunRequest request, KubectlOutputListener listener) {
        HttpURLConnection connection = null;
        try {
            connection = open("/internal/v1/executions", "POST", request.timeout().plusSeconds(10));
            connection.setDoOutput(true);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json");
            objectMapper.writeValue(connection.getOutputStream(), Map.of(
                    "executionId", request.executionId(),
                    "credentialType", request.credential().credentialType().name(),
                    "credentialPayload", request.credential().payload(),
                    "namespace", request.namespace() == null ? "" : request.namespace(),
                    "arguments", request.arguments(),
                    "manifest", request.manifest() == null ? "" : request.manifest(),
                    "timeoutMs", request.timeout().toMillis(),
                    "maximumOutputBytes", request.maximumOutputBytes()
            ));
            int status = connection.getResponseCode();
            if (status != 200) throw failure(connection, status);
            KubectlRunResult result = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode event = objectMapper.readTree(line);
                    if ("output".equals(event.path("type").asText())) {
                        listener.onOutput(event.path("channel").asText(), event.path("text").asText());
                    } else if ("result".equals(event.path("type").asText())) {
                        result = new KubectlRunResult(event.path("exitCode").asInt(-1), event.path("stdout").asText(),
                                event.path("stderr").asText(), event.path("timedOut").asBoolean(),
                                event.path("canceled").asBoolean(), event.path("truncated").asBoolean(),
                                event.path("durationMs").asLong());
                    }
                }
            }
            if (result == null) throw new KubernetesApiException("command runner response did not contain a result", null);
            return result;
        } catch (KubernetesApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KubernetesApiException("command runner unavailable: " + concise(exception), exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    public boolean cancel(UUID executionId) {
        try {
            HttpURLConnection connection = open("/internal/v1/executions/" + executionId, "DELETE", Duration.ofSeconds(5));
            int status = connection.getResponseCode();
            connection.disconnect();
            return status == 204;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public String clientVersion() {
        try {
            HttpURLConnection connection = open("/internal/v1/capabilities", "GET", Duration.ofSeconds(5));
            if (connection.getResponseCode() != 200) return "unavailable";
            String version = objectMapper.readTree(connection.getInputStream()).path("kubectlVersion").asText("unavailable");
            connection.disconnect();
            return version;
        } catch (Exception exception) {
            return "unavailable";
        }
    }

    @Override
    public boolean available() {
        return !"unavailable".equals(clientVersion());
    }

    @Override
    public String executionBoundary() {
        return "ISOLATED_RUNNER";
    }

    private HttpURLConnection open(String path, String method, Duration timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) baseUri.resolve(path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout((int) Math.min(timeout.toMillis(), 10_000));
        connection.setReadTimeout((int) Math.min(timeout.toMillis(), Integer.MAX_VALUE));
        connection.setRequestProperty(TOKEN_HEADER, token);
        connection.setRequestProperty(HttpHeaders.ACCEPT, "application/x-ndjson, application/json");
        return connection;
    }

    private KubernetesApiException failure(HttpURLConnection connection, int status) throws Exception {
        String detail = new String((status >= 400 ? connection.getErrorStream() : connection.getInputStream()).readAllBytes(), StandardCharsets.UTF_8);
        return new KubernetesApiException("command runner rejected request (" + status + "): " + detail, null);
    }

    private String concise(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
