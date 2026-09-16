package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import io.strato.aiops.application.port.out.KubernetesMutationResult;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnosticsPort;
import io.strato.aiops.application.port.out.KubernetesPodLogs;
import io.strato.aiops.application.port.out.KubernetesRollbackPlan;
import io.strato.aiops.domain.analysis.AnalysisCommandSafety;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Performs guarded execution for the bounded kubectl subset accepted by the analysis console. */
final class AnalysisCommandExecutor {

    private final KubernetesMutationPort mutationPort;
    private final KubernetesNamespaceDiagnosticsPort diagnosticsPort;
    private final CredentialResolver credentialResolver;
    private final DiagnosticsResolver diagnosticsResolver;

    /** AnalysisCommandExecutor 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    AnalysisCommandExecutor(KubernetesMutationPort mutationPort,
                            KubernetesNamespaceDiagnosticsPort diagnosticsPort,
                            CredentialResolver credentialResolver,
                            DiagnosticsResolver diagnosticsResolver) {
        this.mutationPort = mutationPort;
        this.diagnosticsPort = diagnosticsPort;
        this.credentialResolver = credentialResolver;
        this.diagnosticsResolver = diagnosticsResolver;
    }

    /** AnalysisCommandExecutor의 guard 처리에 필요한 업무 로직을 수행한다. */
    GuardResult guard(UUID clusterId, AnalysisCommandParser.ParsedCommand command) {
        if (!command.executable()) return GuardResult.blocked(command.reason());
        if (command.safety() != AnalysisCommandSafety.CHANGE) {
            return GuardResult.allowed(command.reason(), false, "", "", "");
        }
        try {
            KubernetesConnectionCredential credential = credentialResolver.resolve(clusterId);
            if (isRolloutUndo(command)) {
                KubernetesRollbackPlan plan = mutationPort.previewRollbackDeployment(credential, command.namespace(),
                        requireText(command.resourceName(), "deploymentName"), command.targetRevision());
                if (!plan.executable()) {
                    return GuardResult.blocked(plan.reason(), true, true, false, plan.reason(), rollbackSummary(plan));
                }
                return GuardResult.allowed(plan.reason(), true, plan.confirmationText(), plan.reason(),
                        rollbackSummary(plan), true, true, true);
            }
            KubernetesMutationResult dryRun = "rollout".equals(command.operation())
                    ? mutationPort.dryRunRolloutRestartDeployment(credential, command.namespace(),
                    requireText(command.resourceName(), "deploymentName"))
                    : mutationPort.dryRunScaleDeployment(credential, command.namespace(),
                    requireText(command.resourceName(), "deploymentName"), command.replicas());
            return GuardResult.allowed("RBAC and dry-run guard passed. " + command.reason(), true,
                    command.confirmationText(), "RBAC and dry-run guard passed.", mutationSummary(dryRun),
                    true, true, true);
        } catch (RuntimeException exception) {
            return GuardResult.blocked("RBAC/dry-run guard failed: " + value(exception.getMessage()),
                    false, false, false, value(exception.getMessage()), "");
        }
    }

    /** AnalysisCommandExecutor의 execute 처리의 핵심 작업 흐름을 실행한다. */
    String execute(UUID clusterId, AnalysisCommandParser.ParsedCommand command) {
        return switch (command.operation()) {
            case "logs" -> executeLogs(clusterId, command);
            case "get", "describe" -> executeDiagnostics(clusterId, command);
            case "rollout" -> executeRollout(clusterId, command);
            case "scale" -> executeScale(clusterId, command);
            case "auth" -> "권한 확인 명령은 현재 UI 실행기에서 지원하지 않습니다. 터미널에서 별도 확인하세요.\n"
                    + command.command();
            default -> throw new IllegalArgumentException("Unsupported read-only command: " + command.operation());
        };
    }

    /** AnalysisCommandExecutor의 executeRollout 처리의 핵심 작업 흐름을 실행한다. */
    private String executeRollout(UUID clusterId, AnalysisCommandParser.ParsedCommand command) {
        KubernetesConnectionCredential credential = credentialResolver.resolve(clusterId);
        KubernetesMutationResult result = isRolloutUndo(command)
                ? mutationPort.rollbackDeployment(credential, command.namespace(),
                requireText(command.resourceName(), "deploymentName"), command.targetRevision())
                : mutationPort.rolloutRestartDeployment(credential, command.namespace(),
                requireText(command.resourceName(), "deploymentName"));
        return mutationSummary(result);
    }

    /** AnalysisCommandExecutor의 executeScale 처리의 핵심 작업 흐름을 실행한다. */
    private String executeScale(UUID clusterId, AnalysisCommandParser.ParsedCommand command) {
        return mutationSummary(mutationPort.scaleDeployment(credentialResolver.resolve(clusterId), command.namespace(),
                requireText(command.resourceName(), "deploymentName"), command.replicas()));
    }

    /** AnalysisCommandExecutor의 executeLogs 처리의 핵심 작업 흐름을 실행한다. */
    private String executeLogs(UUID clusterId, AnalysisCommandParser.ParsedCommand command) {
        KubernetesPodLogs logs = diagnosticsPort.collectPodLogs(credentialResolver.resolve(clusterId), command.namespace(),
                requireText(command.resourceName(), "podName"), command.containerName(),
                Math.max(10, Math.min(command.tailLines(), 1000)), command.previousLogs());
        StringBuilder output = new StringBuilder("kubectl logs result\n")
                .append("namespace=").append(logs.namespace()).append(" pod=").append(logs.podName())
                .append(" tailLines=").append(logs.tailLines()).append(" previous=").append(command.previousLogs()).append('\n');
        logs.containers().forEach(container -> output.append("\n# container=").append(container.containerName())
                .append(container.truncated() ? " truncated=true" : "").append('\n')
                .append(value(container.log())).append('\n'));
        return output.toString();
    }

    /** AnalysisCommandExecutor의 executeDiagnostics 처리의 핵심 작업 흐름을 실행한다. */
    private String executeDiagnostics(UUID clusterId, AnalysisCommandParser.ParsedCommand command) {
        KubernetesNamespaceDiagnostics diagnostics = diagnosticsResolver.resolve(clusterId, command.namespace());
        String resourceType = normalizeResourceType(command.resourceType());
        if ("Event".equals(resourceType)) return formatEvents(command, diagnostics);
        if (resourceType.isBlank() || command.resourceName().isBlank()) return formatResourceList(resourceType, diagnostics);
        return formatResourceDetails(command, diagnostics, resourceType);
    }

    /** AnalysisCommandExecutor의 formatEvents 처리에 필요한 업무 로직을 수행한다. */
    private String formatEvents(AnalysisCommandParser.ParsedCommand command, KubernetesNamespaceDiagnostics diagnostics) {
        List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events = diagnostics.events().stream()
                .filter(event -> command.fieldSelectorInvolvedName().isBlank()
                        || command.fieldSelectorInvolvedName().equals(event.involvedName()))
                .sorted(Comparator.comparing(KubernetesNamespaceDiagnostics.DiagnosticEvent::eventTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50).toList();
        StringBuilder output = new StringBuilder("Kubernetes events\nnamespace=")
                .append(command.namespace()).append(" count=").append(events.size()).append('\n');
        events.forEach(event -> output.append("- ").append(value(event.type())).append(' ')
                .append(value(event.reason())).append(' ').append(value(event.involvedKind())).append('/')
                .append(value(event.involvedName())).append(" count=").append(event.count())
                .append(" time=").append(event.eventTime()).append(" message=").append(value(event.message())).append('\n'));
        return output.toString();
    }

    /** AnalysisCommandExecutor의 formatResourceList 처리에 필요한 업무 로직을 수행한다. */
    private String formatResourceList(String resourceType, KubernetesNamespaceDiagnostics diagnostics) {
        List<KubernetesNamespaceDiagnostics.DiagnosticResource> resources = diagnostics.resources().stream()
                .filter(resource -> resourceType.isBlank()
                        || resourceType.equals(normalizeResourceType(resource.resourceType())))
                .limit(100).toList();
        StringBuilder output = new StringBuilder("Kubernetes resources\ncount=").append(resources.size()).append('\n');
        resources.forEach(resource -> output.append("- ").append(resource.resourceType()).append('/')
                .append(resource.resourceName()).append(" namespace=").append(resource.namespace())
                .append(" status=").append(value(resource.status())).append('\n'));
        return output.toString();
    }

    /** AnalysisCommandExecutor의 formatResourceDetails 처리에 필요한 업무 로직을 수행한다. */
    private String formatResourceDetails(AnalysisCommandParser.ParsedCommand command,
                                         KubernetesNamespaceDiagnostics diagnostics, String resourceType) {
        KubernetesNamespaceDiagnostics.DiagnosticResource matched = diagnostics.resources().stream()
                .filter(resource -> resourceType.equals(normalizeResourceType(resource.resourceType())))
                .filter(resource -> command.resourceName().equals(resource.resourceName())).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Resource not found in diagnostics context: "
                        + resourceType + "/" + command.resourceName()));
        StringBuilder output = new StringBuilder("Kubernetes resource detail\n")
                .append(matched.resourceType()).append('/').append(matched.resourceName())
                .append(" namespace=").append(matched.namespace()).append(" status=").append(value(matched.status()))
                .append("\nsummary=").append(value(matched.summaryJson())).append("\n\nRelated events\n");
        diagnostics.events().stream().filter(event -> command.resourceName().equals(event.involvedName())).limit(20)
                .forEach(event -> output.append("- ").append(value(event.type())).append(' ')
                        .append(value(event.reason())).append(" count=").append(event.count())
                        .append(" message=").append(value(event.message())).append('\n'));
        return output.toString();
    }

    /** AnalysisCommandExecutor의 isRolloutUndo 처리 조건의 충족 여부를 판단한다. */
    private boolean isRolloutUndo(AnalysisCommandParser.ParsedCommand command) {
        return "rollout".equals(command.operation())
                && command.command().toLowerCase(Locale.ROOT).contains(" rollout undo ");
    }

    /** AnalysisCommandExecutor의 mutationSummary 처리에 필요한 업무 로직을 수행한다. */
    private String mutationSummary(KubernetesMutationResult result) {
        return "Kubernetes mutation result\naction=" + result.action() + '\n'
                + "target=" + result.resourceType() + "/" + result.resourceName() + '\n'
                + "namespace=" + result.namespace() + '\n'
                + "previous=" + value(result.previousState()) + '\n'
                + "next=" + value(result.nextState()) + '\n'
                + "changedAt=" + result.changedAt();
    }

    /** AnalysisCommandExecutor의 rollbackSummary 처리에 필요한 업무 로직을 수행한다. */
    private String rollbackSummary(KubernetesRollbackPlan plan) {
        return "Kubernetes rollback guard\ntarget=Deployment/" + plan.deploymentName() + '\n'
                + "namespace=" + plan.namespace() + '\n' + "currentRevision=" + value(plan.currentRevision()) + '\n'
                + "targetRevision=" + value(plan.targetRevision()) + '\n' + "executable=" + plan.executable() + '\n'
                + "reason=" + value(plan.reason()) + '\n' + "current=" + value(plan.currentState()) + '\n'
                + "target=" + value(plan.targetState()) + '\n' + "confirmationText=" + value(plan.confirmationText());
    }

    /** AnalysisCommandExecutor의 normalizeResourceType 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeResourceType(String resourceType) {
        return switch (value(resourceType).toLowerCase(Locale.ROOT)) {
            case "", "all" -> "";
            case "pod", "pods", "po" -> "Pod";
            case "service", "services", "svc" -> "Service";
            case "configmap", "configmaps", "cm" -> "ConfigMap";
            case "secret", "secrets" -> "Secret";
            case "persistentvolumeclaim", "persistentvolumeclaims", "pvc" -> "PersistentVolumeClaim";
            case "deployment", "deployments", "deploy" -> "Deployment";
            case "replicaset", "replicasets", "rs" -> "ReplicaSet";
            case "statefulset", "statefulsets", "sts" -> "StatefulSet";
            case "daemonset", "daemonsets", "ds" -> "DaemonSet";
            case "job", "jobs" -> "Job";
            case "cronjob", "cronjobs" -> "CronJob";
            case "ingress", "ingresses", "ing" -> "Ingress";
            case "horizontalpodautoscaler", "horizontalpodautoscalers", "hpa" -> "HorizontalPodAutoscaler";
            case "event", "events", "ev" -> "Event";
            default -> resourceType;
        };
    }

    /** AnalysisCommandExecutor의 requireText 처리 입력과 현재 상태의 유효성을 검증한다. */
    private String requireText(String input, String name) {
        String normalized = value(input).trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    /** AnalysisCommandExecutor의 value 처리에 필요한 업무 로직을 수행한다. */
    private static String value(Object input) {
        return input == null ? "" : input.toString();
    }

    @FunctionalInterface
    interface CredentialResolver {
        /** CredentialResolver의 resolve 처리에 필요한 결과를 조합해 반환한다. */
        KubernetesConnectionCredential resolve(UUID clusterId);
    }

    @FunctionalInterface
    interface DiagnosticsResolver {
        /** DiagnosticsResolver의 resolve 처리에 필요한 결과를 조합해 반환한다. */
        KubernetesNamespaceDiagnostics resolve(UUID clusterId, String namespace);
    }

    record GuardResult(boolean executable, String reason, boolean requiresConfirmation, String confirmationText,
                       boolean rbacAllowed, boolean dryRunPassed, boolean rollbackGuardPassed,
                       String guardMessage, String dryRunSummary) {
        /** GuardResult의 allowed 처리에 필요한 업무 로직을 수행한다. */
        static GuardResult allowed(String reason, boolean requiresConfirmation, String confirmationText,
                                   String guardMessage, String dryRunSummary) {
            return allowed(reason, requiresConfirmation, confirmationText, guardMessage, dryRunSummary,
                    true, true, false);
        }

        /** GuardResult의 allowed 처리에 필요한 업무 로직을 수행한다. */
        static GuardResult allowed(String reason, boolean requiresConfirmation, String confirmationText,
                                   String guardMessage, String dryRunSummary, boolean rbacAllowed,
                                   boolean dryRunPassed, boolean rollbackGuardPassed) {
            return new GuardResult(true, reason, requiresConfirmation, confirmationText, rbacAllowed,
                    dryRunPassed, rollbackGuardPassed, guardMessage, dryRunSummary);
        }

        /** GuardResult의 blocked 처리에 필요한 업무 로직을 수행한다. */
        static GuardResult blocked(String reason) {
            return blocked(reason, false, false, false, reason, "");
        }

        /** GuardResult의 blocked 처리에 필요한 업무 로직을 수행한다. */
        static GuardResult blocked(String reason, boolean rbacAllowed, boolean dryRunPassed,
                                   boolean rollbackGuardPassed, String guardMessage, String dryRunSummary) {
            return new GuardResult(false, reason, false, "", rbacAllowed, dryRunPassed,
                    rollbackGuardPassed, guardMessage, dryRunSummary);
        }
    }
}
