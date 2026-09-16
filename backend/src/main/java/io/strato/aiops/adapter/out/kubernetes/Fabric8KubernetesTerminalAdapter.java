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

    /** Fabric8KubernetesTerminalAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Fabric8KubernetesTerminalAdapter(ObjectMapper objectMapper,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs) {
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    /** Fabric8KubernetesTerminalAdapter의 open 처리에 필요한 업무 로직을 수행한다. */
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

    /** Fabric8KubernetesTerminalAdapter의 createClient 처리에 필요한 데이터를 생성하거나 저장한다. */
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

    /** Fabric8KubernetesTerminalAdapter의 output 처리에 필요한 업무 로직을 수행한다. */
    private OutputStream output(KubernetesTerminalListener listener, String channel) {
        return new OutputStream() {
            /** 익명 구현체의 write 처리에 필요한 업무 로직을 수행한다. */
            @Override public void write(int value) { listener.onOutput(channel, String.valueOf((char) value)); }
            /** 익명 구현체의 write 처리에 필요한 업무 로직을 수행한다. */
            @Override public void write(byte[] values, int offset, int length) {
                listener.onOutput(channel, new String(values, offset, length, StandardCharsets.UTF_8));
            }
        };
    }

    /** Fabric8KubernetesTerminalAdapter의 concise 처리에 필요한 업무 로직을 수행한다. */
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

        /** Fabric8TerminalSession 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

        /** Fabric8TerminalSession의 input 처리에 필요한 업무 로직을 수행한다. */
        @Override public void input(String data) {
            try {
                input.write(data.getBytes(StandardCharsets.UTF_8));
                input.flush();
            } catch (IOException exception) {
                throw new KubernetesApiException("Failed to write terminal input", exception);
            }
        }

        /** Fabric8TerminalSession의 resize 처리에 필요한 업무 로직을 수행한다. */
        @Override public void resize(int columns, int rows) { watch.resize(columns, rows); }
        /** Fabric8TerminalSession의 exitCode 처리에 필요한 업무 로직을 수행한다. */
        @Override public CompletableFuture<Integer> exitCode() { return watch.exitCode(); }
        /** Fabric8TerminalSession의 close 처리 대상과 관련 상태를 안전하게 정리한다. */
        @Override public void close() { try { watch.close(); } finally { client.close(); } }
    }
}
