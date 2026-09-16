package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface GetClusterCredentialUseCase {

    /** GetClusterCredentialUseCase의 getClusterCredential 처리 결과를 조회해 반환한다. */
    ClusterCredentialResult getClusterCredential(UUID clusterId, boolean reveal, String actor, String requestId);
}
