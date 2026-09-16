package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.command.CommandFavorite;

import java.time.Instant;
import java.util.UUID;

public record CommandFavoriteResponse(
        UUID id,
        UUID clusterId,
        String ownerUserId,
        String name,
        String description,
        String command,
        String namespace,
        boolean shared,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
    /** CommandFavoriteResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static CommandFavoriteResponse from(CommandFavorite value) {
        return new CommandFavoriteResponse(value.id(), value.clusterId(), value.ownerUserId(), value.name(),
                value.description(), value.command(), value.namespace(), value.shared(), value.sortOrder(),
                value.createdAt(), value.updatedAt());
    }
}

