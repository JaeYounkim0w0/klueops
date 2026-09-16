package io.strato.aiops.application.port.out;

import java.util.List;

public interface ChartCatalogPort {
    /** ChartCatalogPort의 search 처리 계약을 정의한다. */
    List<CatalogPackage> search(String query, int limit);
    /** ChartCatalogPort의 details 처리 계약을 정의한다. */
    CatalogPackage details(String repository, String name, String version);

    record CatalogPackage(String packageId, String repository, String repositoryDisplayName, String repositoryUrl,
                          String name, String description, String version, String appVersion, String contentUrl,
                          boolean official, boolean verifiedPublisher, List<String> availableVersions) {
    }
}
