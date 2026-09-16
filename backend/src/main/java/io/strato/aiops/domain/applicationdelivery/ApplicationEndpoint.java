package io.strato.aiops.domain.applicationdelivery;

import java.time.Instant;
import java.util.UUID;

public record ApplicationEndpoint(UUID id, UUID applicationId, String endpointType, String url, String hostname,
                                  String status, Instant createdAt, Instant updatedAt) { }
