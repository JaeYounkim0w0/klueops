package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ClusterResourcePageResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated Kubernetes resource snapshot inventory")
public record ClusterResourcePageResponse(
        List<KubernetesResourceSnapshotResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long problemCount,
        List<FacetResponse> namespaceFacets,
        List<FacetResponse> resourceTypeFacets
) {
    /** ClusterResourcePageResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static ClusterResourcePageResponse from(ClusterResourcePageResult result) {
        return new ClusterResourcePageResponse(
                result.items().stream().map(KubernetesResourceSnapshotResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(), result.problemCount(),
                result.namespaceFacets().stream().map(FacetResponse::from).toList(),
                result.resourceTypeFacets().stream().map(FacetResponse::from).toList()
        );
    }

    public record FacetResponse(String value, long count) {
        /** FacetResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        private static FacetResponse from(ClusterResourcePageResult.Facet facet) {
            return new FacetResponse(facet.value(), facet.count());
        }
    }
}
