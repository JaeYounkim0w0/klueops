package io.strato.aiops.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Async job start response")
public record StartJobResponse(
        @Schema(description = "Started job ID") UUID jobId
) {
}

