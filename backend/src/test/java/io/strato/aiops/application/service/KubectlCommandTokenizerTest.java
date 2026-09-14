package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.CommandValidationResult;
import io.strato.aiops.domain.command.CommandSafety;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KubectlCommandTokenizerTest {
    private final KubectlCommandTokenizer tokenizer = new KubectlCommandTokenizer();

    @Test
    void validatesReadOnlyCommandAndKeepsQuotedArguments() {
        CommandValidationResult result = tokenizer.validate(
                "kubectl get pods -l 'app=my api' -o wide", "production");

        assertThat(result.safety()).isEqualTo(CommandSafety.READ_ONLY);
        assertThat(result.requiresConfirmation()).isFalse();
        assertThat(result.namespace()).isEqualTo("production");
        assertThat(result.arguments()).containsExactly("get", "pods", "-l", "app=my api", "-o", "wide");
    }

    @Test
    void classifiesBooleanGlobalOptionWithoutSkippingVerb() {
        CommandValidationResult result = tokenizer.validate(
                "kubectl --insecure-skip-tls-verify get nodes", null);

        assertThat(result.safety()).isEqualTo(CommandSafety.READ_ONLY);
        assertThat(result.targetSummary()).startsWith("get nodes");
    }

    @Test
    void requiresConfirmationForChangeAndDestructiveCommands() {
        assertThat(tokenizer.validate("kubectl scale deployment api --replicas=3", "apps").safety())
                .isEqualTo(CommandSafety.CHANGE);
        assertThat(tokenizer.validate("kubectl delete pod api-1", "apps").safety())
                .isEqualTo(CommandSafety.DESTRUCTIVE);
        assertThat(tokenizer.validate("kubectl delete pod api-1", "apps").requiresConfirmation()).isTrue();
    }

    @Test
    void detectsInteractiveExecButAllowsNonInteractiveExecToBeExecutedByRunner() {
        assertThat(tokenizer.validate("kubectl exec api -- env", "apps").interactive()).isFalse();
        assertThat(tokenizer.validate("kubectl exec -it api -- sh", "apps").interactive()).isTrue();
        assertThat(tokenizer.validate("kubectl exec -ti api -- sh", "apps").interactive()).isTrue();
    }

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
