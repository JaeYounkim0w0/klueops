package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.strato.aiops.application.port.out.KubernetesTerminalListener;
import io.strato.aiops.application.port.out.KubernetesTerminalPort;
import io.strato.aiops.application.port.out.KubernetesTerminalRequest;
import io.strato.aiops.application.port.out.KubernetesTerminalSession;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

@Component
public class Fabric8KubernetesTerminalAdapter implements KubernetesTerminalPort {
    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;

    public Fabric8KubernetesTerminalAdapter(ObjectMapper objectMapper,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs) {
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    @Override
    public KubernetesTerminalSession open(KubernetesTerminalRequest request, KubernetesTerminalListener listener) {
        KubernetesClient client = createClient(request);
        try {
            var pod = client.pods().inNamespace(request.namespace()).withName(request.pod());
            ContainerResource target = request.container() == null || request.container().isBlank()
                    ? pod : pod.inContainer(request.container());
            OutputStream stdout = output(listener, "stdout");
            OutputStream stderr = output(listener, "stderr");
            // Fabric8 only exposes ExecWatch#getInput when stdin redirection is
            // requested before the exec/attach operation is created.
            var executable = target.redirectingInput().writingOutput(stdout).writingError(stderr).withTTY();
            ExecWatch watch = "attach".equals(request.verb())
                    ? executable.attach()
                    : executable.exec(request.remoteCommand().toArray(String[]::new));
            return new Fabric8TerminalSession(client, watch);
        } catch (RuntimeException exception) {
            client.close();
            throw new KubernetesApiException("Kubernetes terminal session failed: " + concise(exception), exception);
        }
    }

    private KubernetesClient createClient(KubernetesTerminalRequest request) {
        var credential = request.credential();
        if (credential.credentialType() == ClusterCredentialType.KUBECONFIG) {
            Config config = Config.fromKubeconfig(credential.payload());
            config.setConnectionTimeout(connectTimeoutMs);
            config.setRequestTimeout(requestTimeoutMs);
            return new KubernetesClientBuilder().withConfig(config).build();
        }
        try {
            ServiceAccountPayload payload = objectMapper.readValue(credential.payload(), ServiceAccountPayload.class);
            String certificate = payload.caCertificate();
            String caData = certificate != null && certificate.contains("BEGIN CERTIFICATE")
                    ? Base64.getEncoder().encodeToString(certificate.getBytes(StandardCharsets.UTF_8)) : certificate;
            Config config = new ConfigBuilder().withMasterUrl(payload.apiServerUrl()).withOauthToken(payload.token())
                    .withCaCertData(caData).withConnectionTimeout(connectTimeoutMs).withRequestTimeout(requestTimeoutMs).build();
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    private OutputStream output(KubernetesTerminalListener listener, String channel) {
        return new OutputStream() {
            @Override public void write(int value) { listener.onOutput(channel, String.valueOf((char) value)); }
            @Override public void write(byte[] values, int offset, int length) {
                listener.onOutput(channel, new String(values, offset, length, StandardCharsets.UTF_8));
            }
        };
    }

    private String concise(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record ServiceAccountPayload(String apiServerUrl, String caCertificate, String token) {
    }

    private static final class Fabric8TerminalSession implements KubernetesTerminalSession {
        private final KubernetesClient client;
        private final ExecWatch watch;
        private final OutputStream input;

        private Fabric8TerminalSession(KubernetesClient client, ExecWatch watch) {
            this.client = client;
            this.watch = watch;
            this.input = watch.getInput();
            if (input == null) {
                try {
                    watch.close();
                } finally {
                    client.close();
                }
                throw new IllegalStateException("Kubernetes terminal stdin channel is unavailable");
            }
        }

        @Override public void input(String data) {
            try {
                input.write(data.getBytes(StandardCharsets.UTF_8));
                input.flush();
            } catch (IOException exception) {
                throw new KubernetesApiException("Failed to write terminal input", exception);
            }
        }

        @Override public void resize(int columns, int rows) { watch.resize(columns, rows); }
        @Override public CompletableFuture<Integer> exitCode() { return watch.exitCode(); }
        @Override public void close() { try { watch.close(); } finally { client.close(); } }
    }
}
