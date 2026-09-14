package io.strato.aiops.adapter.in.web.dto;

import jakarta.validation.constraints.Min;

public record ApplicationRollbackRequest(
        @Min(1)
        Integer targetRevision,
        String confirmText
) {
}
