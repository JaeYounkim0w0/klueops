package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface HelmValuesSuggestionPort {
    String suggest(UUID tenantId, String currentValuesYaml, String instruction);
}
