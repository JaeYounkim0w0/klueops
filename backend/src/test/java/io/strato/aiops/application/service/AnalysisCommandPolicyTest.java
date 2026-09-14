package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisCommandPolicyTest {

    private final AnalysisCommandPolicy policy = new AnalysisCommandPolicy();

    @Test
    void classifiesReadOnlyAndMutationCommandsConservatively() {
        assertThat(policy.safetyLevel("kubectl get pods -n default")).isEqualTo("READ_ONLY");
        assertThat(policy.safetyLevel("kubectl patch deployment api -p '{}'")).isEqualTo("RISKY_CHANGE");
        assertThat(policy.safetyLevel("kubectl delete pod api-1")).isEqualTo("DESTRUCTIVE");
        assertThat(policy.safetyLevel("bash cleanup.sh")).isEqualTo("REVIEW_REQUIRED");
    }

    @Test
    void treatsAvailabilityReducingCommandsAsDestructive() {
        assertThat(policy.isDestructive("kubectl scale deployment api --replicas=0")).isTrue();
        assertThat(policy.isDestructive("kubectl rollout undo deployment api")).isTrue();
        assertThat(policy.commandType("kubectl rollout restart deployment api", false)).isEqualTo("safe-change");
    }

    @Test
    void assignsOperatorFacingCategoryAndExplanation() {
        assertThat(policy.runbookCategory("kubectl logs pod/api", false)).isEqualTo("diagnosis");
        assertThat(policy.runbookCategory("kubectl rollout status deployment/api", false)).isEqualTo("verification");
        assertThat(policy.beginnerExplanation("READ_ONLY")).contains("조회 명령");
    }
}
