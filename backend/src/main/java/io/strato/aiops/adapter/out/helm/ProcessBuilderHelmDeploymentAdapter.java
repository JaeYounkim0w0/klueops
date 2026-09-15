package io.strato.aiops.adapter.out.helm;

import io.strato.aiops.application.port.out.HelmDeploymentPort;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessBuilderHelmDeploymentAdapter implements HelmDeploymentPort {
    private static final int MAXIMUM_OUTPUT_BYTES = 1024 * 1024;
    private final String helmPath;

    public ProcessBuilderHelmDeploymentAdapter(@Value("${aiops.application-delivery.helm-path:helm}") String helmPath) {
        this.helmPath = helmPath;
    }

    @Override
    public HelmExecutionResult execute(List<String> arguments, Duration timeout) {
        if (arguments == null || arguments.isEmpty()) throw new IllegalArgumentException("Helm arguments are required");
        List<String> command = new java.util.ArrayList<>();
        command.add(helmPath);
        command.addAll(arguments);
        try {
            Process process = new ProcessBuilder(command).start();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread stdoutReader = reader(process.getInputStream(), stdout, "helm-stdout");
            Thread stderrReader = reader(process.getErrorStream(), stderr, "helm-stderr");
            boolean completed = process.waitFor(Math.max(1, timeout.toSeconds()), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                stdoutReader.join(1000);
                stderrReader.join(1000);
                return new HelmExecutionResult(124, text(stdout), "Helm command timed out\n" + text(stderr));
            }
            stdoutReader.join(1000);
            stderrReader.join(1000);
            return new HelmExecutionResult(process.exitValue(), text(stdout), text(stderr));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Helm execution was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Helm execution failed", exception);
        }
    }

    private Thread reader(InputStream source, ByteArrayOutputStream target, String name) {
        Thread thread = new Thread(() -> {
            try (source) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = source.read(buffer)) >= 0) {
                    int writable = Math.min(read, Math.max(0, MAXIMUM_OUTPUT_BYTES - target.size()));
                    if (writable > 0) target.write(buffer, 0, writable);
                }
            } catch (IOException ignored) {
                // 프로세스 종료 중 stream close는 exit code와 기존 출력으로 판정한다.
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private String text(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}
