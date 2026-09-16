package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

public record KubernetesUpgradeCatalog(String version, List<RemovedApiRule> removedApis,
                                       List<AddonRule> addons) {

    private static final String RESOURCE = "readiness/kubernetes-upgrade-catalog.yml";

    /** KubernetesUpgradeCatalog 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public KubernetesUpgradeCatalog {
        removedApis = removedApis == null ? List.of() : List.copyOf(removedApis);
        addons = addons == null ? List.of() : List.copyOf(addons);
    }

    /** KubernetesUpgradeCatalog의 loadDefault 처리 결과를 조회해 반환한다. */
    public static KubernetesUpgradeCatalog loadDefault() {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            KubernetesUpgradeCatalog value = new ObjectMapper(new YAMLFactory())
                    .findAndRegisterModules().readValue(input, KubernetesUpgradeCatalog.class);
            if (value.version() == null || value.version().isBlank()) {
                throw new IllegalStateException("Upgrade catalog version is required");
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load Kubernetes upgrade catalog " + RESOURCE, exception);
        }
    }

    public record RemovedApiRule(String apiVersion, int removedInMinor, String replacement) {
    }

    public record AddonRule(String kind, String category, String guidance) {
    }
}
