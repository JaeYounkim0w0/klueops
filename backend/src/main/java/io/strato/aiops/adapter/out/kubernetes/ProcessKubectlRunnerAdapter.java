package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubectlOutputListener;
import io.strato.aiops.application.port.out.KubectlRunRequest;
import io.strato.aiops.application.port.out.KubectlRunResult;
import io.strato.aiops.application.port.out.KubectlRunnerPort;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "aiops.command-console.runner.mode", havingValue = "local", matchIfMissing = true)
public class ProcessKubectlRunnerAdapter implements KubectlRunnerPort {
    private final String kubectlPath;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Process> running = new ConcurrentHashMap<>();
    private final Set<UUID> canceledExecutions = ConcurrentHashMap.newKeySet();

    /** ProcessKubectlRunnerAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ProcessKubectlRunnerAdapter(
            ObjectMapper objectMapper,
            @Value("${aiops.command-console.kubectl-path:kubectl}") String kubectlPath
    ) {
        this.objectMapper = objectMapper;
        this.kubectlPath = kubectlPath;
    }

    /** ProcessKubectlRunnerAdapter의 run 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    public KubectlRunResult run(KubectlRunRequest request, KubectlOutputListener listener) {
        long started = System.nanoTime();
        Path directory = null;
        Process process = null;
        AtomicBoolean canceled = new AtomicBoolean(false);
        try {
            directory = Files.createTempDirectory("aiops-kubectl-");
            restrict(directory);
            Path kubeconfig = directory.resolve("config");
            Files.writeString(kubeconfig, kubeconfig(request), StandardCharsets.UTF_8);
            restrict(kubeconfig);

            List<String> arguments = materializeManifest(request.arguments(), request.manifest(), directory);
            List<String> command = new ArrayList<>();
            command.add(kubectlPath);
            command.add("--kubeconfig");
            command.add(kubeconfig.toString());
            if (request.namespace() != null && !request.namespace().isBlank() && !hasNamespace(arguments)) {
                command.add("--namespace");
                command.add(request.namespace());
            }
            command.addAll(arguments);

            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(false);
            isolateEnvironment(builder, directory);
            process = builder.start();
            running.put(request.executionId(), process);

            BoundedOutput stdout = new BoundedOutput(request.maximumOutputBytes(), listener, "stdout");
            BoundedOutput stderr = new BoundedOutput(request.maximumOutputBytes(), listener, "stderr");
            CompletableFuture<Void> stdoutReader = read(process.getInputStream(), stdout);
            CompletableFuture<Void> stderrReader = read(process.getErrorStream(), stderr);
            boolean finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroy(process);
            }
            stdoutReader.join();
            stderrReader.join();
            canceled.set(canceledExecutions.remove(request.executionId()));
            int exitCode = finished ? process.exitValue() : -1;
            return new KubectlRunResult(exitCode, stdout.value(), stderr.value(), !finished && !canceled.get(),
                    canceled.get(), stdout.truncated() || stderr.truncated(), elapsedMs(started));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) destroy(process);
            return new KubectlRunResult(-1, "", "kubectl execution interrupted", false, true, false, elapsedMs(started));
        } catch (Exception exception) {
            if (process != null) destroy(process);
            throw new KubernetesApiException("kubectl execution failed: " + conciseMessage(exception), exception);
        } finally {
            running.remove(request.executionId());
            canceledExecutions.remove(request.executionId());
            deleteRecursively(directory);
        }
    }

    /** ProcessKubectlRunnerAdapter의 cancel 처리 조건의 충족 여부를 판단한다. */
    @Override
    public boolean cancel(UUID executionId) {
        Process process = running.remove(executionId);
        if (process == null) return false;
        canceledExecutions.add(executionId);
        destroy(process);
        return true;
    }

    /** ProcessKubectlRunnerAdapter의 clientVersion 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public String clientVersion() {
        try {
            Process process = new ProcessBuilder(kubectlPath, "version", "--client=true", "-o", "json").start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                destroy(process);
                return "unavailable";
            }
            JsonNode root = objectMapper.readTree(process.getInputStream());
            return root.path("clientVersion").path("gitVersion").asText("unknown");
        } catch (Exception ignored) {
            return "unavailable";
        }
    }

    /** ProcessKubectlRunnerAdapter의 available 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public boolean available() {
        return !"unavailable".equals(clientVersion());
    }

    /** ProcessKubectlRunnerAdapter의 kubeconfig 처리에 필요한 업무 로직을 수행한다. */
    private String kubeconfig(KubectlRunRequest request) throws IOException {
        if (request.credential().credentialType() == ClusterCredentialType.KUBECONFIG) {
            return request.credential().payload();
        }
        JsonNode credential = objectMapper.readTree(request.credential().payload());
        String server = credential.path("apiServerUrl").asText();
        String token = credential.path("token").asText();
        String certificate = credential.path("caCertificate").asText();
        String certificateData = certificate.contains("BEGIN CERTIFICATE")
                ? Base64.getEncoder().encodeToString(certificate.getBytes(StandardCharsets.UTF_8))
                : certificate;
        return """
                apiVersion: v1
                kind: Config
                clusters:
                  - name: remote
                    cluster:
                      server: %s
                      certificate-authority-data: %s
                users:
                  - name: aiops
                    user:
                      token: %s
                contexts:
                  - name: remote
                    context:
                      cluster: remote
                      user: aiops
                current-context: remote
                """.formatted(jsonString(server), jsonString(certificateData), jsonString(token));
    }

    /** ProcessKubectlRunnerAdapter의 materializeManifest 처리에 필요한 업무 로직을 수행한다. */
    private List<String> materializeManifest(List<String> original, String manifest, Path directory) throws IOException {
        List<String> values = new ArrayList<>(original);
        if (manifest == null || manifest.isBlank()) return values;
        Path file = directory.resolve("manifest.yaml");
        Files.writeString(file, manifest, StandardCharsets.UTF_8);
        restrict(file);
        for (int index = 0; index < values.size() - 1; index++) {
            if (("-f".equals(values.get(index)) || "--filename".equals(values.get(index)))
                    && "-".equals(values.get(index + 1))) {
                values.set(index + 1, file.toString());
            }
        }
        return values;
    }

    /** ProcessKubectlRunnerAdapter의 hasNamespace 처리 조건의 충족 여부를 판단한다. */
    private boolean hasNamespace(List<String> arguments) {
        return arguments.stream().anyMatch(value -> value.equals("-n") || value.equals("--namespace")
                || value.startsWith("--namespace="));
    }

    /** ProcessKubectlRunnerAdapter의 isolateEnvironment 처리 조건의 충족 여부를 판단한다. */
    private void isolateEnvironment(ProcessBuilder builder, Path directory) {
        String path = System.getenv().getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin");
        builder.environment().clear();
        builder.environment().put("PATH", path);
        builder.environment().put("HOME", directory.toString());
        builder.environment().put("TMPDIR", directory.toString());
        builder.environment().put("LANG", "C.UTF-8");
        builder.environment().put("LC_ALL", "C.UTF-8");
    }

    /** ProcessKubectlRunnerAdapter의 read 처리 결과를 조회해 반환한다. */
    private CompletableFuture<Void> read(InputStream input, BoundedOutput output) {
        return CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line + "\n");
            } catch (IOException ignored) {
                // Process cancellation closes streams while readers are blocked.
            }
        });
    }

    /** ProcessKubectlRunnerAdapter의 destroy 처리에 필요한 업무 로직을 수행한다. */
    private void destroy(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /** ProcessKubectlRunnerAdapter의 restrict 처리에 필요한 업무 로직을 수행한다. */
    private void restrict(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(Files.isDirectory(path) ? "rwx------" : "rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows/non-POSIX development environments still use a unique temporary directory.
        }
    }

    /** ProcessKubectlRunnerAdapter의 jsonString 처리에 필요한 업무 로직을 수행한다. */
    private String jsonString(String value) throws IOException {
        return objectMapper.writeValueAsString(value);
    }

    /** ProcessKubectlRunnerAdapter의 elapsedMs 처리에 필요한 업무 로직을 수행한다. */
    private long elapsedMs(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    /** ProcessKubectlRunnerAdapter의 conciseMessage 처리에 필요한 업무 로직을 수행한다. */
    private String conciseMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    /** ProcessKubectlRunnerAdapter의 deleteRecursively 처리 대상과 관련 상태를 안전하게 정리한다. */
    private void deleteRecursively(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A later OS temporary-directory cleanup can remove a locked path.
                }
            });
        } catch (IOException ignored) {
            // Nothing sensitive is logged from the temporary directory.
        }
    }

    private static final class BoundedOutput {
        private final int limit;
        private final KubectlOutputListener listener;
        private final String channel;
        private final StringBuilder value = new StringBuilder();
        private final AtomicInteger bytes = new AtomicInteger();
        private final AtomicBoolean truncated = new AtomicBoolean();

        /** BoundedOutput 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private BoundedOutput(int limit, KubectlOutputListener listener, String channel) {
            this.limit = limit;
            this.listener = listener;
            this.channel = channel;
        }

        /** BoundedOutput의 append 처리에 필요한 업무 로직을 수행한다. */
        synchronized void append(String text) {
            int nextBytes = text.getBytes(StandardCharsets.UTF_8).length;
            if (bytes.get() + nextBytes <= limit) {
                value.append(text);
                bytes.addAndGet(nextBytes);
                listener.onOutput(channel, text);
            } else {
                truncated.set(true);
            }
        }

        /** BoundedOutput의 value 처리에 필요한 업무 로직을 수행한다. */
        synchronized String value() {
            return value.toString();
        }

        /** BoundedOutput의 truncated 처리에 필요한 업무 로직을 수행한다. */
        boolean truncated() {
            return truncated.get();
        }
    }
}
