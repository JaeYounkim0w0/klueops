package io.strato.aiops.application.port.in;

public record UpdateAnalysisWorkflowStateCommand(String status, String note) {
}
