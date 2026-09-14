package io.strato.aiops.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommandValidationRequest(
        String namespace,
        @NotBlank @Size(max = 16384) String command
) {
}

