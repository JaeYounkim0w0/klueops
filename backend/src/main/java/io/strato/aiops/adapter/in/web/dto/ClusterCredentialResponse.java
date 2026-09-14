package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ClusterCredentialResult;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Cluster credential payload. Masked by default; reveal=true returns the stored plaintext credential and writes an audit log.")
public record ClusterCredentialResponse(
        UUID clusterId,
        ClusterCredentialType credentialType,
        String payload,
        boolean revealed,
        boolean masked,
        Instant updatedAt
) {
    public static ClusterCredentialResponse from(ClusterCredentialResult result) {
        return new ClusterCredentialResponse(
                result.clusterId(),
                result.credentialType(),
                result.payload(),
                result.revealed(),
                result.masked(),
                result.updatedAt()
        );
    }
}
