package io.strato.aiops.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CommandExecutionRequest(
        UUID sourceAnalysisId,
        String namespace,
        @NotBlank @Size(max = 16384) String command,
        @Size(max = 1048576) String manifest,
        boolean confirmed
) {
}
