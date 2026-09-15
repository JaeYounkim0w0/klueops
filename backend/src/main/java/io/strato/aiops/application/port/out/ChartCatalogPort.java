package io.strato.aiops.application.port.out;

import java.util.List;

public interface ChartCatalogPort {
    List<CatalogPackage> search(String query, int limit);
    CatalogPackage details(String repository, String name, String version);

    record CatalogPackage(String packageId, String repository, String repositoryDisplayName, String repositoryUrl,
                          String name, String description, String version, String appVersion, String contentUrl,
                          boolean official, boolean verifiedPublisher, List<String> availableVersions) {
    }
}
