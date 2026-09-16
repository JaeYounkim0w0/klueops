package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.in.CommandValidationResult;
import io.strato.aiops.application.port.out.KubectlRunRequest;
import io.strato.aiops.application.port.out.KubectlRunResult;
import io.strato.aiops.application.port.out.KubectlRunnerPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.domain.command.CommandSafety;
import io.strato.aiops.domain.command.CommandVerificationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CommandOperationVerifier {
    private static final int SNAPSHOT_LIMIT = 64 * 1024;
    private static final Pattern MANIFEST_KIND = Pattern.compile("(?mi)^kind:\\s*([A-Za-z0-9.-]+)\\s*$");
    private static final Pattern MANIFEST_NAME = Pattern.compile("(?mi)^\\s*name:\\s*([A-Za-z0-9_.-]+)\\s*$");
    private final KubectlRunnerPort runner;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    /** CommandOperationVerifier 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CommandOperationVerifier(KubectlRunnerPort runner, ObjectMapper objectMapper,
                                    @Value("${aiops.command-console.verification-timeout-seconds:15}") long seconds) {
        this.runner = runner;
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofSeconds(Math.max(5, seconds));
    }

    /** CommandOperationVerifier의 captureBefore 처리에 필요한 업무 로직을 수행한다. */
    public ProbeContext captureBefore(CommandValidationResult validation, String manifest,
                                      KubernetesConnectionCredential credential) {
        VerificationPlan plan = plan(validation.arguments(), validation.namespace(), manifest);
        if (plan == null) return ProbeContext.unsupported("대상 리소스를 특정할 수 없어 자동 상태 비교를 생략합니다.");
        Probe probe = probe(plan.arguments(), validation.namespace(), credential);
        return new ProbeContext(plan, probe, rollback(plan, probe));
    }

    /** CommandOperationVerifier의 verify 처리 입력과 현재 상태의 유효성을 검증한다. */
    public Verification verify(ProbeContext before, KubernetesConnectionCredential credential, int commandExitCode) {
        if (before.plan() == null) return new Verification(CommandVerificationStatus.VERIFICATION_FAILED,
                before.message(), before.probe().snapshot(), "", null);
        if (commandExitCode != 0) return new Verification(CommandVerificationStatus.VERIFICATION_FAILED,
                "명령이 실패하여 사후 상태 검증을 수행하지 않았습니다.", before.probe().snapshot(), "", before.rollbackCommand());
        Probe after = probe(before.plan().arguments(), before.plan().namespace(), credential);
        if (before.plan().expectMissing() && before.probe().successful() && !after.successful()) {
            return new Verification(CommandVerificationStatus.VERIFIED_CHANGED,
                    "대상 리소스가 더 이상 조회되지 않아 삭제 결과를 확인했습니다.", before.probe().snapshot(), after.snapshot(), null);
        }
        if (!after.successful()) return new Verification(CommandVerificationStatus.VERIFICATION_FAILED,
                "명령은 완료됐지만 사후 Kubernetes 상태 조회에 실패했습니다: " + concise(after.error()),
                before.probe().snapshot(), after.snapshot(), before.rollbackCommand());
        if (!before.probe().successful()) return new Verification(CommandVerificationStatus.VERIFIED_CHANGED,
                "사전에 조회되지 않던 대상이 명령 후 확인됐습니다.", before.probe().snapshot(), after.snapshot(), before.rollbackCommand());
        boolean changed = !before.probe().hash().equals(after.hash());
        return new Verification(changed ? CommandVerificationStatus.VERIFIED_CHANGED : CommandVerificationStatus.VERIFIED_STABLE,
                changed ? "Kubernetes 상태 변경을 전후 Snapshot으로 확인했습니다."
                        : "명령은 성공했지만 관측 가능한 Kubernetes 상태 변화는 없습니다.",
                before.probe().snapshot(), after.snapshot(), before.rollbackCommand());
    }

    /** CommandOperationVerifier의 required 처리 입력과 현재 상태의 유효성을 검증한다. */
    public boolean required(CommandSafety safety) {
        return safety == CommandSafety.CHANGE || safety == CommandSafety.DESTRUCTIVE;
    }

    /** CommandOperationVerifier의 probe 처리에 필요한 업무 로직을 수행한다. */
    private Probe probe(List<String> arguments, String namespace, KubernetesConnectionCredential credential) {
        KubectlRunResult result = runner.run(new KubectlRunRequest(UUID.randomUUID(), credential, namespace, arguments,
                null, timeout, SNAPSHOT_LIMIT), (channel, text) -> {});
        String raw = result.stdout().isBlank() ? result.stderr() : result.stdout();
        String snapshot = sanitize(raw);
        return new Probe(result.exitCode() == 0, snapshot, hash(snapshot), result.stderr());
    }

    /** CommandOperationVerifier의 plan 처리에 필요한 업무 로직을 수행한다. */
    private VerificationPlan plan(List<String> arguments, String namespace, String manifest) {
        List<String> positional = positional(arguments);
        if (positional.isEmpty()) return null;
        String verb = positional.get(0).toLowerCase(Locale.ROOT);
        if ("rollout".equals(verb) && positional.size() >= 3) {
            return getPlan(verb, positional.get(2), namespace, false);
        }
        if (("scale".equals(verb) || "annotate".equals(verb) || "label".equals(verb) || "patch".equals(verb)) && positional.size() >= 2) {
            return getPlan(verb, combineTarget(positional, 1), namespace, false);
        }
        if ("set".equals(verb) && positional.size() >= 3) return getPlan(verb, positional.get(2), namespace, false);
        if ("delete".equals(verb) && positional.size() >= 2) {
            String target = positional.size() >= 3 && !positional.get(2).contains("=")
                    ? positional.get(1) + "/" + positional.get(2) : positional.get(1);
            return getPlan(verb, target, namespace, true);
        }
        if (("apply".equals(verb) || "create".equals(verb) || "replace".equals(verb)) && manifest != null && !manifest.isBlank()) {
            Matcher kind = MANIFEST_KIND.matcher(manifest);
            Matcher name = MANIFEST_NAME.matcher(manifest);
            if (kind.find() && name.find()) return getPlan(verb,
                    kind.group(1).toLowerCase(Locale.ROOT) + "/" + name.group(1), namespace, false);
        }
        return null;
    }

    /** CommandOperationVerifier의 getPlan 처리 결과를 조회해 반환한다. */
    private VerificationPlan getPlan(String operation, String target, String namespace, boolean expectMissing) {
        return new VerificationPlan(List.of("get", target, "-o", "json", "--ignore-not-found=false"),
                namespace, target, operation, expectMissing);
    }

    /** CommandOperationVerifier의 combineTarget 처리에 필요한 업무 로직을 수행한다. */
    private String combineTarget(List<String> positional, int index) {
        String resource = positional.get(index);
        if (resource.contains("/") || positional.size() <= index + 1 || positional.get(index + 1).contains("=")) return resource;
        return resource + "/" + positional.get(index + 1);
    }

    /** CommandOperationVerifier의 positional 처리에 필요한 업무 로직을 수행한다. */
    private List<String> positional(List<String> arguments) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String value = arguments.get(i);
            if (value.startsWith("-")) {
                if (!value.contains("=") && SetLike.VALUE_OPTIONS.contains(value) && i + 1 < arguments.size()) i++;
                continue;
            }
            values.add(value);
        }
        return values;
    }

    /** CommandOperationVerifier의 rollback 처리에 필요한 업무 로직을 수행한다. */
    private String rollback(VerificationPlan plan, Probe before) {
        String target = plan.target();
        if ("scale".equals(plan.operation()) && before.successful()) {
            try {
                JsonNode replicas = objectMapper.readTree(before.snapshot()).path("spec").path("replicas");
                if (replicas.canConvertToInt()) {
                    return "kubectl scale " + target + " --replicas=" + replicas.intValue() + namespaceSuffix(plan.namespace());
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        if (target.startsWith("deployment/") || target.startsWith("statefulset/") || target.startsWith("daemonset/")) {
            if ("rollout".equals(plan.operation()) || "set".equals(plan.operation())) {
                return "kubectl rollout undo " + target + namespaceSuffix(plan.namespace());
            }
        }
        return null;
    }

    /** CommandOperationVerifier의 namespaceSuffix 처리에 필요한 업무 로직을 수행한다. */
    private String namespaceSuffix(String namespace) {
        return namespace == null || namespace.isBlank() ? "" : " -n " + namespace;
    }

    /** CommandOperationVerifier의 sanitize 처리에 필요한 업무 로직을 수행한다. */
    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(raw);
            scrub(root);
            String value = objectMapper.writeValueAsString(root);
            return value.length() > SNAPSHOT_LIMIT ? value.substring(0, SNAPSHOT_LIMIT) : value;
        } catch (Exception ignored) {
            String value = raw.replaceAll("(?i)(token|password|client-secret)\\s*[:=]\\s*\\S+", "$1: [REDACTED]");
            return value.length() > SNAPSHOT_LIMIT ? value.substring(0, SNAPSHOT_LIMIT) : value;
        }
    }

    /** CommandOperationVerifier의 scrub 처리에 필요한 업무 로직을 수행한다. */
    private void scrub(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove(List.of("managedFields", "resourceVersion", "uid", "creationTimestamp", "selfLink"));
            List<String> fields = new ArrayList<>();
            object.fieldNames().forEachRemaining(fields::add);
            fields.forEach(field -> {
                if (field.toLowerCase(Locale.ROOT).contains("secret") || field.equalsIgnoreCase("token")) {
                    object.put(field, "[REDACTED]");
                } else {
                    scrub(object.get(field));
                }
            });
        } else if (node.isArray()) node.forEach(this::scrub);
    }

    /** CommandOperationVerifier의 hash 처리 조건의 충족 여부를 판단한다. */
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    /** CommandOperationVerifier의 concise 처리에 필요한 업무 로직을 수행한다. */
    private String concise(String value) { return value == null || value.isBlank() ? "unknown error" : value.lines().findFirst().orElse("unknown error"); }

    public record ProbeContext(VerificationPlan plan, Probe probe, String rollbackCommand, String message) {
        /** ProbeContext 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        ProbeContext(VerificationPlan plan, Probe probe, String rollback) { this(plan, probe, rollback, ""); }
        /** ProbeContext의 unsupported 처리에 필요한 업무 로직을 수행한다. */
        static ProbeContext unsupported(String message) { return new ProbeContext(null, new Probe(false, "", hashEmpty(), ""), null, message); }
        /** ProbeContext의 hashEmpty 처리 조건의 충족 여부를 판단한다. */
        private static String hashEmpty() { return ""; }
    }
    public record Verification(CommandVerificationStatus status, String summary, String beforeSnapshot,
                               String afterSnapshot, String rollbackCommand) {}
    record VerificationPlan(List<String> arguments, String namespace, String target, String operation,
                            boolean expectMissing) {}
    record Probe(boolean successful, String snapshot, String hash, String error) {}
    private static final class SetLike {
        private static final java.util.Set<String> VALUE_OPTIONS = java.util.Set.of("-n", "--namespace", "-f", "--filename", "-p", "--patch", "--type", "--replicas", "--timeout", "--selector", "-l", "--container", "-c");
    }
}
