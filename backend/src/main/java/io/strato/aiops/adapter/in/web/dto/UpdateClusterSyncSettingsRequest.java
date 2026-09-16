package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.UpdateClusterSyncSettingsCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Cluster sync settings update request")
public record UpdateClusterSyncSettingsRequest(
        @Schema(description = "Enable automatic synchronization")
        Boolean autoSyncEnabled,

        @Schema(description = "Synchronization interval seconds. Must be between 60 and 3600.")
        @Min(60)
        @Max(3600)
        Integer syncIntervalSeconds
) {
    /** UpdateClusterSyncSettingsRequest의 toCommand 처리 데이터를 필요한 표현으로 변환한다. */
    public UpdateClusterSyncSettingsCommand toCommand() {
        return new UpdateClusterSyncSettingsCommand(autoSyncEnabled, syncIntervalSeconds);
    }
}
