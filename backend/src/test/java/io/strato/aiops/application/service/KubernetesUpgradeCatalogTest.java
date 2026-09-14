package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesUpgradeCatalogTest {

    @Test
    void loadsVersionedApiAndAddonCompatibilityRules() {
        var catalog = KubernetesUpgradeCatalog.loadDefault();

        assertThat(catalog.version()).isEqualTo("kubernetes-compatibility-2026.09");
        assertThat(catalog.removedApis()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(catalog.addons()).extracting(KubernetesUpgradeCatalog.AddonRule::kind)
                .contains("IngressClass", "CSIDriver", "MutatingWebhookConfiguration");
    }
}
