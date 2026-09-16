package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminalCommandParserTest {
    private final TerminalCommandParser parser = new TerminalCommandParser();

    /** TerminalCommandParserTest의 parsesExecTtyPodContainerAndRemoteCommand 처리 데이터를 필요한 표현으로 변환한다. */
    @Test
    void parsesExecTtyPodContainerAndRemoteCommand() {
        TerminalCommandSpec result = parser.parse(List.of(
                "exec", "-it", "api-7d9", "-c", "server", "--", "/bin/sh", "-l"));

        assertThat(result.verb()).isEqualTo("exec");
        assertThat(result.pod()).isEqualTo("api-7d9");
        assertThat(result.container()).isEqualTo("server");
        assertThat(result.remoteCommand()).containsExactly("/bin/sh", "-l");
    }

    /** TerminalCommandParserTest의 parsesAttachAndNamespaceOptions 처리 데이터를 필요한 표현으로 변환한다. */
    @Test
    void parsesAttachAndNamespaceOptions() {
        TerminalCommandSpec result = parser.parse(List.of(
                "attach", "-it", "worker-0", "--namespace", "jobs", "--container=worker"));

        assertThat(result.verb()).isEqualTo("attach");
        assertThat(result.pod()).isEqualTo("worker-0");
        assertThat(result.container()).isEqualTo("worker");
        assertThat(result.remoteCommand()).isEmpty();
    }

    /** TerminalCommandParserTest의 rejectsMissingPodOrExecCommand 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsMissingPodOrExecCommand() {
        assertThatThrownBy(() -> parser.parse(List.of("exec", "-it")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pod name");
        assertThatThrownBy(() -> parser.parse(List.of("exec", "-it", "api-0")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires a command");
    }
}
