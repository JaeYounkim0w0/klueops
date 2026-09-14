package io.strato.aiops.adapter.in.web.security;

import io.strato.aiops.domain.identity.Capability;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAuthorizationInterceptorTest {

    private final ApiAuthorizationInterceptor interceptor = new ApiAuthorizationInterceptor(null, null);

    @Test
    void extractsNamespaceFromAnalysisPathForScopeEvaluation() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analysis/namespaces/team-a/diagnostics");

        assertThat(interceptor.namespace(request, request.getRequestURI())).isEqualTo("team-a");
    }

    @Test
    void extractsWorkspaceFromDirectWorkspacePathForScopeEvaluation() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/workspaces/11111111-1111-1111-1111-111111111111");

        assertThat(interceptor.workspaceId(request.getRequestURI()))
                .isEqualTo(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void requiresOperationExecuteForConsoleCommandsAndHistory() {
        String base = "/api/clusters/11111111-1111-1111-1111-111111111111";

        assertThat(interceptor.requiredCapability(base + "/commands/validate", "POST"))
                .isEqualTo(Capability.OPERATION_EXECUTE);
        assertThat(interceptor.requiredCapability(base + "/command-executions", "GET"))
                .isEqualTo(Capability.OPERATION_EXECUTE);
        assertThat(interceptor.requiredCapability(base + "/command-sessions", "POST"))
                .isEqualTo(Capability.OPERATION_EXECUTE);
        assertThat(interceptor.requiredCapability(base + "/command-favorites", "GET"))
                .isEqualTo(Capability.OPERATION_EXECUTE);
    }

    @Test
    void allowsClusterReadersToInspectConsoleCapabilityOnly() {
        String path = "/api/clusters/11111111-1111-1111-1111-111111111111/command-capabilities";

        assertThat(interceptor.requiredCapability(path, "GET")).isEqualTo(Capability.CLUSTER_READ);
    }
}
