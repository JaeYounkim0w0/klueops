package io.strato.aiops.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommandFavoriteRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 300) String description,
        @NotBlank @Size(max = 16384) String command,
        String namespace,
        boolean shared,
        @Min(0) @Max(10000) int sortOrder
) {
}

