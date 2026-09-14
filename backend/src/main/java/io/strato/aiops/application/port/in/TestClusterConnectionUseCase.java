package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface TestClusterConnectionUseCase {

    ClusterConnectionTestResult testClusterConnection(UUID clusterId, String actor, String requestId);
}
