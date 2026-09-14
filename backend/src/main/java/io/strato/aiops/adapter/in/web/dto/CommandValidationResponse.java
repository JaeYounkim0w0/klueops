package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.CommandValidationResult;

import java.util.List;

public record CommandValidationResponse(
        String normalizedCommand,
        List<String> arguments,
        String namespace,
        String safety,
        boolean requiresConfirmation,
        boolean interactive,
        String targetSummary,
        List<String> warnings
) {
    public static CommandValidationResponse from(CommandValidationResult value) {
        return new CommandValidationResponse(value.normalizedCommand(), value.arguments(), value.namespace(),
                value.safety().name(), value.requiresConfirmation(), value.interactive(), value.targetSummary(), value.warnings());
    }
}

