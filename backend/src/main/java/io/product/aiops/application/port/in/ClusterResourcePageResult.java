package io.product.aiops.application.port.in;

import io.product.aiops.domain.sync.KubernetesResourceSnapshot;

import java.util.List;

public record ClusterResourcePageResult(
        List<KubernetesResourceSnapshot> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long problemCount,
        List<Facet> namespaceFacets,
        List<Facet> resourceTypeFacets
) {
    public record Facet(String value, long count) {
    }
}
