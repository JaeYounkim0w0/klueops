package io.strato.aiops.application.port.in;

public record UpdateClusterSyncSettingsCommand(
        Boolean autoSyncEnabled,
        Integer syncIntervalSeconds
) {
}
