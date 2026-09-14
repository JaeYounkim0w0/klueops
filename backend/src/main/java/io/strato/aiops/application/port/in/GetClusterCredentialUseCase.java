package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface GetClusterCredentialUseCase {

    ClusterCredentialResult getClusterCredential(UUID clusterId, boolean reveal, String actor, String requestId);
}
