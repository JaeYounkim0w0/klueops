package io.strato.aiops.domain.operations;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionEvidenceTest {

    /** ProductionEvidenceTest의 runningEvidenceCanCompleteButTerminalEvidenceCannotRestart 처리의 핵심 작업 흐름을 실행한다. */
    @Test
    void runningEvidenceCanCompleteButTerminalEvidenceCannotRestart() {
        var run = ProductionEvidence.Run.start(UUID.randomUUID(), "release", "local", "operator", Instant.now());
        var passed = run.complete(ProductionEvidence.State.PASSED, Instant.now(), List.of());

        assertThat(passed.state()).isEqualTo(ProductionEvidence.State.PASSED);
        assertThat(passed.completedAt()).isNotNull();
        assertThatThrownBy(() -> passed.complete(ProductionEvidence.State.RUNNING, Instant.now(), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    /** ProductionEvidenceTest의 checksumIsStableAndSecretsAreNotPartOfDisplayDetail 처리 입력과 현재 상태의 유효성을 검증한다. */
    @Test
    void checksumIsStableAndSecretsAreNotPartOfDisplayDetail() {
        String checksum = ProductionEvidence.sha256("password=plain token=abc");

        assertThat(checksum).hasSize(64);
        assertThat(ProductionEvidence.sanitize("password=plain token=abc Bearer ey.secret"))
                .isEqualTo("password=*** token=*** Bearer ***");
    }
}
