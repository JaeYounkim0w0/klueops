package io.strato.aiops.application.port.out;

import java.util.List;
import java.util.UUID;

public record KubernetesTerminalRequest(
        UUID sessionId,
        KubernetesConnectionCredential credential,
        String namespace,
        String verb,
        String pod,
        String container,
        List<String> remoteCommand
) {
}
