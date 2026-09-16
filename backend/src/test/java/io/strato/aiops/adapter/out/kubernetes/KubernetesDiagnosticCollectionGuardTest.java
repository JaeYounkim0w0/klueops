package io.strato.aiops.adapter.out.kubernetes;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KubernetesDiagnosticCollectionGuardTest {

    /** KubernetesDiagnosticCollectionGuardTest의 isolatesOneSourceFailureAndKeepsSuccessfulEvidence 처리 조건의 충족 여부를 판단한다. */
    @Test
    void isolatesOneSourceFailureAndKeepsSuccessfulEvidence() {
        AtomicInteger items = new AtomicInteger();
        KubernetesDiagnosticCollectionGuard guard = new KubernetesDiagnosticCollectionGuard(2,
                exception -> exception.getMessage());

        guard.run("pods", items::get, () -> items.addAndGet(3));
        guard.run("events", items::get, () -> { throw new IllegalStateException("forbidden"); });
        guard.run("services", items::get, () -> items.incrementAndGet());
        guard.requireSuccessfulSource();

        assertThat(guard.stages()).extracting(stage -> stage.source() + ":" + stage.status())
                .containsExactly("pods:SUCCEEDED", "events:FAILED", "services:SUCCEEDED");
        assertThat(guard.stages().get(0).itemCount()).isEqualTo(3);
        assertThat(guard.stages().get(1).detail()).isEqualTo("forbidden");
    }

    /** KubernetesDiagnosticCollectionGuardTest의 stopsCallingKubernetesAfterFailureBudgetIsExhausted 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void stopsCallingKubernetesAfterFailureBudgetIsExhausted() {
        AtomicInteger calls = new AtomicInteger();
        KubernetesDiagnosticCollectionGuard guard = new KubernetesDiagnosticCollectionGuard(1,
                exception -> "masked");

        guard.run("pods", () -> 0, () -> { calls.incrementAndGet(); throw new IllegalStateException("secret"); });
        guard.run("events", () -> 0, calls::incrementAndGet);

        assertThat(calls).hasValue(1);
        assertThat(guard.stages().get(1).status()).isEqualTo("SKIPPED");
        assertThatThrownBy(guard::requireSuccessfulSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("masked");
    }
}
