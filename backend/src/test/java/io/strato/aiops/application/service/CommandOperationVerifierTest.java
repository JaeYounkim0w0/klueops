package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.in.CommandValidationResult;
import io.strato.aiops.application.port.out.KubectlOutputListener;
import io.strato.aiops.application.port.out.KubectlRunRequest;
import io.strato.aiops.application.port.out.KubectlRunResult;
import io.strato.aiops.application.port.out.KubectlRunnerPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.strato.aiops.domain.command.CommandSafety;
import io.strato.aiops.domain.command.CommandVerificationStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandOperationVerifierTest {
    private final KubernetesConnectionCredential credential = new KubernetesConnectionCredential(ClusterCredentialType.KUBECONFIG, "config");

    @Test
    void detectsWorkloadChangeAndBuildsRollbackCandidate() {
        SequenceRunner runner = new SequenceRunner(success("{\"kind\":\"Deployment\",\"spec\":{\"replicas\":1}}"),
                success("{\"kind\":\"Deployment\",\"spec\":{\"replicas\":2}}"));
        CommandOperationVerifier verifier = new CommandOperationVerifier(runner, new ObjectMapper(), 10);
        var validation = validation(List.of("scale", "deployment/api", "--replicas=2"), CommandSafety.CHANGE);

        var before = verifier.captureBefore(validation, null, credential);
        var result = verifier.verify(before, credential, 0);

        assertThat(result.status()).isEqualTo(CommandVerificationStatus.VERIFIED_CHANGED);
        assertThat(result.rollbackCommand()).isEqualTo("kubectl scale deployment/api --replicas=1 -n default");
        assertThat(runner.requests).allSatisfy(request -> assertThat(request.arguments()).containsExactly(
                "get", "deployment/api", "-o", "json", "--ignore-not-found=false"));
    }

    @Test
    void verifiesDeletionWhenTargetDisappears() {
        SequenceRunner runner = new SequenceRunner(success("{\"kind\":\"Pod\"}"), failure("NotFound"));
        CommandOperationVerifier verifier = new CommandOperationVerifier(runner, new ObjectMapper(), 10);
        var before = verifier.captureBefore(validation(List.of("delete", "pod", "broken"), CommandSafety.DESTRUCTIVE), null, credential);

        var result = verifier.verify(before, credential, 0);

        assertThat(result.status()).isEqualTo(CommandVerificationStatus.VERIFIED_CHANGED);
        assertThat(result.summary()).contains("삭제 결과");
        assertThat(result.rollbackCommand()).isNull();
    }

    @Test
    void doesNotInventVerificationForUnknownMutation() {
        CommandOperationVerifier verifier = new CommandOperationVerifier(new SequenceRunner(), new ObjectMapper(), 10);
        var before = verifier.captureBefore(validation(List.of("cordon", "node-a"), CommandSafety.CHANGE), null, credential);

        var result = verifier.verify(before, credential, 0);

        assertThat(result.status()).isEqualTo(CommandVerificationStatus.VERIFICATION_FAILED);
        assertThat(result.summary()).contains("특정할 수 없어");
    }

    private CommandValidationResult validation(List<String> arguments, CommandSafety safety) {
        return new CommandValidationResult("kubectl " + String.join(" ", arguments), arguments, "default", safety,
                true, false, "target", List.of());
    }

    private static KubectlRunResult success(String output) { return new KubectlRunResult(0, output, "", false, false, false, 1); }
    private static KubectlRunResult failure(String error) { return new KubectlRunResult(1, "", error, false, false, false, 1); }

    private static final class SequenceRunner implements KubectlRunnerPort {
        private final ArrayDeque<KubectlRunResult> results;
        private final java.util.ArrayList<KubectlRunRequest> requests = new java.util.ArrayList<>();
        private SequenceRunner(KubectlRunResult... results) { this.results = new ArrayDeque<>(List.of(results)); }
        @Override public KubectlRunResult run(KubectlRunRequest request, KubectlOutputListener listener) { requests.add(request); return results.removeFirst(); }
        @Override public boolean cancel(java.util.UUID id) { return false; }
        @Override public String clientVersion() { return "test"; }
        @Override public boolean available() { return true; }
    }
}
