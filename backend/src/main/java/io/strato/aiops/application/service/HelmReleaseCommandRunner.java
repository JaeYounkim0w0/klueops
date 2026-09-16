package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.HelmDeploymentPort;
import io.strato.aiops.domain.application.ManagedApplication;
import io.strato.aiops.domain.applicationdelivery.DeploymentPlan;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class HelmReleaseCommandRunner {
    private final HelmDeploymentPort helm;

    /** HelmReleaseCommandRunner 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public HelmReleaseCommandRunner(HelmDeploymentPort helm) { this.helm = helm; }

    /** HelmReleaseCommandRunner의 render 처리에 필요한 업무 로직을 수행한다. */
    public String render(String releaseName, String namespace, byte[] archive, String values) {
        return inWorkspace("klueops-preview-", directory -> {
            Path chart = directory.resolve("chart.tgz");
            Files.write(chart, archive);
            List<String> arguments = new ArrayList<>(List.of("template", releaseName, chart.toString(),
                    "--namespace", namespace, "--include-crds"));
            appendValues(arguments, directory, values);
            var result = helm.execute(arguments, Duration.ofSeconds(45));
            if (result.exitCode() != 0) throw new IllegalArgumentException("Helm template failed: " + bounded(result.stderr()));
            return result.stdout();
        });
    }

    /** HelmReleaseCommandRunner의 install 처리에 필요한 업무 로직을 수행한다. */
    public void install(DeploymentPlan plan, byte[] archive, String values, String kubeconfig) {
        inWorkspace("klueops-deploy-", directory -> {
            Path chart = directory.resolve("chart.tgz");
            Path kubeconfigFile = directory.resolve("kubeconfig");
            Files.write(chart, archive);
            Files.writeString(kubeconfigFile, kubeconfig, StandardCharsets.UTF_8);
            List<String> arguments = new ArrayList<>(List.of("upgrade", "--install", plan.releaseName(), chart.toString(),
                    "--namespace", plan.namespace(), "--atomic", "--wait", "--timeout", "5m",
                    "--kubeconfig", kubeconfigFile.toString()));
            if (plan.createNamespace()) arguments.add("--create-namespace");
            appendValues(arguments, directory, values);
            var result = helm.execute(arguments, Duration.ofMinutes(6));
            if (result.exitCode() != 0) throw new IllegalStateException("Helm install failed: " + bounded(result.stderr()));
            return null;
        });
    }

    /** HelmReleaseCommandRunner의 lifecycle 처리에 필요한 업무 로직을 수행한다. */
    public void lifecycle(ManagedApplication application, String operationType, Integer revision, String kubeconfig) {
        inWorkspace("klueops-lifecycle-", directory -> {
            Path kubeconfigFile = directory.resolve("kubeconfig");
            Files.writeString(kubeconfigFile, kubeconfig, StandardCharsets.UTF_8);
            List<String> arguments = new ArrayList<>();
            if ("ROLLBACK".equals(operationType)) {
                arguments.addAll(List.of("rollback", application.helmReleaseName(), String.valueOf(revision),
                        "--namespace", application.namespace(), "--wait", "--timeout", "5m"));
            } else {
                // 설치가 중간에 실패해 Release가 없어도 KlueOps 메타데이터 정리를 완료한다.
                arguments.addAll(List.of("uninstall", application.helmReleaseName(), "--namespace", application.namespace(),
                        "--ignore-not-found", "--wait", "--timeout", "5m"));
            }
            arguments.addAll(List.of("--kubeconfig", kubeconfigFile.toString()));
            var result = helm.execute(arguments, Duration.ofMinutes(6));
            if (result.exitCode() != 0) throw new IllegalStateException("Helm " + operationType.toLowerCase()
                    + " failed: " + bounded(result.stderr()));
            return null;
        });
    }

    /** HelmReleaseCommandRunner의 appendValues 처리에 필요한 업무 로직을 수행한다. */
    private void appendValues(List<String> arguments, Path directory, String values) throws IOException {
        if (values == null) return;
        Path valuesFile = directory.resolve("values.yaml");
        Files.writeString(valuesFile, values, StandardCharsets.UTF_8);
        arguments.addAll(List.of("--values", valuesFile.toString()));
    }

    /** HelmReleaseCommandRunner의 inWorkspace 처리에 필요한 업무 로직을 수행한다. */
    private <T> T inWorkspace(String prefix, WorkspaceAction<T> action) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory(prefix);
            return action.execute(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Helm execution workspace could not be created", exception);
        } finally {
            delete(directory);
        }
    }

    /** HelmReleaseCommandRunner의 bounded 처리에 필요한 업무 로직을 수행한다. */
    private String bounded(String value) {
        if (value == null) return "Unknown Helm error";
        return value.length() <= 1800 ? value : value.substring(0, 1800);
    }

    /** HelmReleaseCommandRunner의 delete 처리 대상과 관련 상태를 안전하게 정리한다. */
    private void delete(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
            // 임시 파일 정리 실패가 실제 Helm 결과를 덮지 않도록 한다.
        }
    }

    @FunctionalInterface
    private interface WorkspaceAction<T> { /** WorkspaceAction의 execute 처리의 핵심 작업 흐름을 실행한다. */ T execute(Path directory) throws IOException; }
}
