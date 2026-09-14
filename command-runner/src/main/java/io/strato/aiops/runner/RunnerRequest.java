package io.strato.aiops.runner;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

record RunnerRequest(
        @NotNull UUID executionId,
        @NotBlank String credentialType,
        @NotBlank @Size(max = 1_048_576) String credentialPayload,
        @Size(max = 253) String namespace,
        @NotEmpty @Size(max = 256) List<@NotBlank @Size(max = 16_384) String> arguments,
        @Size(max = 1_048_576) String manifest,
        @Min(5_000) @Max(900_000) long timeoutMs,
        @Min(65_536) @Max(10_485_760) int maximumOutputBytes
) {
}
