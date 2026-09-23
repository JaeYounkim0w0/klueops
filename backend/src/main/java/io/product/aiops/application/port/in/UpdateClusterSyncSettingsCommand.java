package io.product.aiops.application.port.in;

public record UpdateClusterSyncSettingsCommand(
        Boolean autoSyncEnabled,
        Integer syncIntervalSeconds
) {
}
