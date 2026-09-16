package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface TestClusterConnectionUseCase {

    /** TestClusterConnectionUseCase의 testClusterConnection 처리 계약을 정의한다. */
    ClusterConnectionTestResult testClusterConnection(UUID clusterId, String actor, String requestId);
}
