package io.strato.aiops.application.service;

import io.strato.aiops.domain.analysis.AnalysisCommandSafety;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisCommandParserTest {

    private final AnalysisCommandParser parser = new AnalysisCommandParser();

    @Test
    void expandsNamespaceAndParsesReadOnlyLogScope() {
        AnalysisCommandParser.ParsedCommand parsed = parser.parse(
                "kubectl logs pod/api -n ${namespace} -c app --tail=250 --previous",
                "payments"
        );

        assertThat(parsed.executable()).isTrue();
        assertThat(parsed.safety()).isEqualTo(AnalysisCommandSafety.DIAGNOSE);
        assertThat(parsed.namespace()).isEqualTo("payments");
        assertThat(parsed.resourceType()).isEqualTo("Pod");
        assertThat(parsed.resourceName()).isEqualTo("api");
        assertThat(parsed.containerName()).isEqualTo("app");
        assertThat(parsed.tailLines()).isEqualTo(250);
        assertThat(parsed.previousLogs()).isTrue();
    }

    @Test
    void blocksDestructiveCommands() {
        AnalysisCommandParser.ParsedCommand parsed = parser.parse(
                "kubectl delete pod api -n payments",
                "payments"
        );

        assertThat(parsed.executable()).isFalse();
        assertThat(parsed.safety()).isEqualTo(AnalysisCommandSafety.DESTRUCTIVE);
        assertThat(parsed.reason()).contains("차단");
    }

    @Test
    void requiresExplicitRevisionForRollback() {
        AnalysisCommandParser.ParsedCommand blocked = parser.parse(
                "kubectl rollout undo deployment/api -n payments",
                "payments"
        );
        AnalysisCommandParser.ParsedCommand allowed = parser.parse(
                "kubectl rollout undo deployment/api -n payments --to-revision=2",
                "payments"
        );

        assertThat(blocked.executable()).isFalse();
        assertThat(allowed.executable()).isTrue();
        assertThat(allowed.confirmationText()).isEqualTo("ROLLBACK payments/api TO REVISION 2");
    }
}
