package io.strato.aiops.application.port.out;

import java.util.UUID;
import java.util.List;

public interface HelmValuesSuggestionPort {
    /** HelmValuesSuggestionPort의 suggest 처리 계약을 정의한다. */
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
            List<String> requiredRootKeys,
            String instruction,
            String validationFeedback
    ) { }
}
