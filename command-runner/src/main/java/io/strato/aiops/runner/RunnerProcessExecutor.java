package io.strato.aiops.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
final class RunnerProcessExecutor {
    interface EventSink { void send(Map<String, Object> event) throws IOException; }

    private final ObjectMapper objectMapper;
    private final String kubectlPath;
    private final Map<UUID, Process> running = new ConcurrentHashMap<>();
    private final Set<UUID> canceled = ConcurrentHashMap.newKeySet();

    RunnerProcessExecutor(ObjectMapper objectMapper, @Value("${runner.kubectl-path:kubectl}") String kubectlPath) {
        this.objectMapper = objectMapper;
        this.kubectlPath = kubectlPath;
    }

    void execute(RunnerRequest request, EventSink sink) throws IOException {
        long started = System.nanoTime();
        Path directory = null;
        Process process = null;
        try {
            directory = Files.createTempDirectory("aiops-runner-");
            restrict(directory, true);
            Path kubeconfig = directory.resolve("config");
            Files.writeString(kubeconfig, kubeconfig(request), StandardCharsets.UTF_8);
            restrict(kubeconfig, false);
            List<String> arguments = materializeManifest(request, directory);
            List<String> command = new ArrayList<>(List.of(kubectlPath, "--kubeconfig", kubeconfig.toString()));
            if (!request.namespace().isBlank() && !hasNamespace(arguments)) command.addAll(List.of("--namespace", request.namespace()));
            command.addAll(arguments);
            ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
            isolateEnvironment(builder, directory);
            process = builder.start();
            running.put(request.executionId(), process);
            BoundedText stdout = new BoundedText(request.maximumOutputBytes());
            BoundedText stderr = new BoundedText(request.maximumOutputBytes());
            CompletableFuture<Void> out = read(process.getInputStream(), "stdout", stdout, sink);
            CompletableFuture<Void> err = read(process.getErrorStream(), "stderr", stderr, sink);
            boolean finished = process.waitFor(request.timeoutMs(), TimeUnit.MILLISECONDS);
            if (!finished) destroy(process);
            out.join();
            err.join();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "result");
            result.put("exitCode", finished ? process.exitValue() : -1);
            result.put("stdout", stdout.value());
            result.put("stderr", stderr.value());
            result.put("timedOut", !finished);
            result.put("canceled", canceled.contains(request.executionId()));
            result.put("truncated", stdout.truncated() || stderr.truncated());
            result.put("durationMs", Duration.ofNanos(System.nanoTime() - started).toMillis());
            sink.send(result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) destroy(process);
            throw new IOException("runner execution interrupted", exception);
        } finally {
            running.remove(request.executionId());
            canceled.remove(request.executionId());
            delete(directory);
        }
    }

    boolean cancel(UUID executionId) {
        Process process = running.remove(executionId);
        if (process == null) return false;
        canceled.add(executionId);
        destroy(process);
        return true;
    }

    String clientVersion(String path) {
        try {
            Process process = new ProcessBuilder(path, "version", "--client=true", "-o", "json").start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) { destroy(process); return "unavailable"; }
            return objectMapper.readTree(process.getInputStream()).path("clientVersion").path("gitVersion").asText("unknown");
        } catch (Exception ignored) { return "unavailable"; }
    }

    private CompletableFuture<Void> read(InputStream input, String channel, BoundedText target, EventSink sink) {
        return CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String chunk = line + "\n";
                    if (target.append(chunk)) sink.send(Map.of("type", "output", "channel", channel, "text", chunk));
                }
            } catch (IOException ignored) {
                // Cancellation closes the process streams.
            }
        });
    }

    private String kubeconfig(RunnerRequest request) throws IOException {
        if ("KUBECONFIG".equals(request.credentialType())) return request.credentialPayload();
        var credential = objectMapper.readTree(request.credentialPayload());
        String certificate = credential.path("caCertificate").asText();
        String certificateData = certificate.contains("BEGIN CERTIFICATE")
                ? Base64.getEncoder().encodeToString(certificate.getBytes(StandardCharsets.UTF_8)) : certificate;
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
                    context: {cluster: remote, user: aiops}
                current-context: remote
                """.formatted(json(credential.path("apiServerUrl").asText()), json(certificateData), json(credential.path("token").asText()));
    }

    private List<String> materializeManifest(RunnerRequest request, Path directory) throws IOException {
        List<String> values = new ArrayList<>(request.arguments());
        if (request.manifest().isBlank()) return values;
        Path manifest = directory.resolve("manifest.yaml");
        Files.writeString(manifest, request.manifest(), StandardCharsets.UTF_8);
        restrict(manifest, false);
        for (int i = 0; i < values.size() - 1; i++) if (("-f".equals(values.get(i)) || "--filename".equals(values.get(i))) && "-".equals(values.get(i + 1))) values.set(i + 1, manifest.toString());
        return values;
    }

    private boolean hasNamespace(List<String> values) { return values.stream().anyMatch(v -> v.equals("-n") || v.equals("--namespace") || v.startsWith("--namespace=")); }
    private String json(String value) throws IOException { return objectMapper.writeValueAsString(value); }
    private void isolateEnvironment(ProcessBuilder builder, Path dir) {
        builder.environment().clear();
        builder.environment().put("PATH", System.getenv().getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin"));
        builder.environment().put("HOME", dir.toString());
        builder.environment().put("TMPDIR", dir.toString());
        builder.environment().put("LANG", "C.UTF-8");
        builder.environment().put("LC_ALL", "C.UTF-8");
    }
    private void destroy(Process process) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
    private void restrict(Path path, boolean directory) { try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------")); } catch (IOException | UnsupportedOperationException ignored) {} }
    private void delete(Path directory) { if (directory == null) return; try (var paths = Files.walk(directory)) { paths.sorted((a,b) -> b.compareTo(a)).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); } catch (IOException ignored) {} }

    private static final class BoundedText {
        private final int limit; private final StringBuilder value = new StringBuilder(); private int bytes; private boolean truncated;
        private BoundedText(int limit) { this.limit = limit; }
        synchronized boolean append(String text) { int size = text.getBytes(StandardCharsets.UTF_8).length; if (bytes + size > limit) { truncated = true; return false; } value.append(text); bytes += size; return true; }
        synchronized String value() { return value.toString(); }
        synchronized boolean truncated() { return truncated; }
    }
}
