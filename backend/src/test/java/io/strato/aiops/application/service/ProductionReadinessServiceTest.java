package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionReadinessServiceTest {

    @Test
    void productionRequiresTrustedPortalAndAiTransport() {
        ProductionReadinessService service = service("http://portal.example.com", "http://ollama.example.com:11434");

        ProductionReadinessService.RuntimeReadiness readiness = service.assess();

        assertThat(readiness.status()).isEqualTo(ProductionReadinessService.ReadinessStatus.BLOCKED);
        assertThat(readiness.checks()).filteredOn(check -> check.code().equals("PUBLIC_PORTAL_ORIGIN"))
                .extracting(ProductionReadinessService.ReadinessCheck::status)
                .containsExactly(ProductionReadinessService.ReadinessStatus.BLOCKED);
        assertThat(readiness.checks()).filteredOn(check -> check.code().equals("AI_PROVIDER_TRANSPORT"))
                .extracting(ProductionReadinessService.ReadinessCheck::status)
                .containsExactly(ProductionReadinessService.ReadinessStatus.BLOCKED);
    }

    @Test
    void productionAcceptsHttpsPortalAndClusterInternalAiTransport() {
        ProductionReadinessService service = service("https://portal.example.com", "http://ollama.aiops-system.svc:11434");

        ProductionReadinessService.RuntimeReadiness readiness = service.assess();

        assertThat(readiness.checks()).filteredOn(check -> check.code().equals("PUBLIC_PORTAL_ORIGIN")
                        || check.code().equals("AI_PROVIDER_TRANSPORT")
                        || check.code().equals("COMMAND_RUNNER_ISOLATION"))
                .allMatch(check -> check.status() == ProductionReadinessService.ReadinessStatus.READY);
    }

    private ProductionReadinessService service(String publicBaseUrl, String aiBaseUrl) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("postgres", "security-oidc");
        return new ProductionReadinessService(environment, "jdbc:postgresql://postgres/aiops", "strong-key", false,
                false, false, "https://id.example.com/realms/aiops", "aiops-bff", true, false,
                "", "", "", "", publicBaseUrl, aiBaseUrl, "remote");
    }
}
