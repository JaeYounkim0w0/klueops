package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface HelmValuesSuggestionPort {
    String suggest(UUID tenantId, SuggestionRequest request);

    record SuggestionRequest(
            String promptVersion,
            String chartName,
            String packageName,
            String providerName,
            String sourceType,
            String chartVersion,
            String applicationVersion,
            String currentValuesYaml,
            String defaultValuesSkeleton,
            String valuesSchemaJson,
            String instruction,
            String validationFeedback
    ) { }
}
