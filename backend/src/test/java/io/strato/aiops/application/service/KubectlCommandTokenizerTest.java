package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.CommandValidationResult;
import io.strato.aiops.domain.command.CommandSafety;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KubectlCommandTokenizerTest {
    private final KubectlCommandTokenizer tokenizer = new KubectlCommandTokenizer();

    /** KubectlCommandTokenizerTest의 validatesReadOnlyCommandAndKeepsQuotedArguments 처리 입력과 현재 상태의 유효성을 검증한다. */
    @Test
    void validatesReadOnlyCommandAndKeepsQuotedArguments() {
        CommandValidationResult result = tokenizer.validate(
                "kubectl get pods -l 'app=my api' -o wide", "production");

        assertThat(result.safety()).isEqualTo(CommandSafety.READ_ONLY);
        assertThat(result.requiresConfirmation()).isFalse();
        assertThat(result.namespace()).isEqualTo("production");
        assertThat(result.arguments()).containsExactly("get", "pods", "-l", "app=my api", "-o", "wide");
    }

    /** KubectlCommandTokenizerTest의 classifiesBooleanGlobalOptionWithoutSkippingVerb 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void classifiesBooleanGlobalOptionWithoutSkippingVerb() {
        CommandValidationResult result = tokenizer.validate(
                "kubectl --insecure-skip-tls-verify get nodes", null);

        assertThat(result.safety()).isEqualTo(CommandSafety.READ_ONLY);
        assertThat(result.targetSummary()).startsWith("get nodes");
    }

    /** KubectlCommandTokenizerTest의 requiresConfirmationForChangeAndDestructiveCommands 처리 입력과 현재 상태의 유효성을 검증한다. */
    @Test
    void requiresConfirmationForChangeAndDestructiveCommands() {
        assertThat(tokenizer.validate("kubectl scale deployment api --replicas=3", "apps").safety())
                .isEqualTo(CommandSafety.CHANGE);
        assertThat(tokenizer.validate("kubectl delete pod api-1", "apps").safety())
                .isEqualTo(CommandSafety.DESTRUCTIVE);
        assertThat(tokenizer.validate("kubectl delete pod api-1", "apps").requiresConfirmation()).isTrue();
    }

    /** KubectlCommandTokenizerTest의 detectsInteractiveExecButAllowsNonInteractiveExecToBeExecutedByRunner 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void detectsInteractiveExecButAllowsNonInteractiveExecToBeExecutedByRunner() {
        assertThat(tokenizer.validate("kubectl exec api -- env", "apps").interactive()).isFalse();
        assertThat(tokenizer.validate("kubectl exec -it api -- sh", "apps").interactive()).isTrue();
        assertThat(tokenizer.validate("kubectl exec -ti api -- sh", "apps").interactive()).isTrue();
    }

    /** KubectlCommandTokenizerTest의 rejectsShellAndCredentialOrContextOverrides 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsShellAndCredentialOrContextOverrides() {
        assertThatThrownBy(() -> tokenizer.validate("kubectl get pods | grep api", "apps"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("shell operators");
        assertThatThrownBy(() -> tokenizer.validate("kubectl --kubeconfig /tmp/other get pods", "apps"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("managed by the platform");
        assertThatThrownBy(() -> tokenizer.validate("kubectl --context other get pods", "apps"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("context overrides");
    }
}
