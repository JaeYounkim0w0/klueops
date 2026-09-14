package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.command.CommandSafety;

import java.util.List;

public record CommandValidationResult(
        String normalizedCommand,
        List<String> arguments,
        String namespace,
        CommandSafety safety,
        boolean requiresConfirmation,
        boolean interactive,
        String targetSummary,
        List<String> warnings
) {
}

