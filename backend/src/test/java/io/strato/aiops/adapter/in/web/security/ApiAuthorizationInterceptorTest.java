package io.strato.aiops.adapter.in.web.security;

import io.strato.aiops.domain.identity.Capability;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAuthorizationInterceptorTest {

    private final ApiAuthorizationInterceptor interceptor = new ApiAuthorizationInterceptor(null, null);

    /** ApiAuthorizationInterceptorTest의 extractsNamespaceFromAnalysisPathForScopeEvaluation 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void extractsNamespaceFromAnalysisPathForScopeEvaluation() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analysis/namespaces/team-a/diagnostics");

        assertThat(interceptor.namespace(request, request.getRequestURI())).isEqualTo("team-a");
    }

    /** ApiAuthorizationInterceptorTest의 extractsWorkspaceFromDirectWorkspacePathForScopeEvaluation 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void extractsWorkspaceFromDirectWorkspacePathForScopeEvaluation() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/workspaces/11111111-1111-1111-1111-111111111111");

        assertThat(interceptor.workspaceId(request.getRequestURI()))
                .isEqualTo(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    /** ApiAuthorizationInterceptorTest의 requiresOperationExecuteForConsoleCommandsAndHistory 처리 입력과 현재 상태의 유효성을 검증한다. */
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

    /** ApiAuthorizationInterceptorTest의 allowsClusterReadersToInspectConsoleCapabilityOnly 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void allowsClusterReadersToInspectConsoleCapabilityOnly() {
        String path = "/api/clusters/11111111-1111-1111-1111-111111111111/command-capabilities";

        assertThat(interceptor.requiredCapability(path, "GET")).isEqualTo(Capability.CLUSTER_READ);
    }

    /** ApiAuthorizationInterceptorTest의 requiresClusterManageToRevealStoredCredentialPlaintext 처리 입력과 현재 상태의 유효성을 검증한다. */
    @Test
    void requiresClusterManageToRevealStoredCredentialPlaintext() {
        String path = "/api/clusters/11111111-1111-1111-1111-111111111111/credential";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setParameter("reveal", "true");

        assertThat(interceptor.requiredCapability(request, path)).isEqualTo(Capability.CLUSTER_MANAGE);
        request.setParameter("reveal", "false");
        assertThat(interceptor.requiredCapability(request, path)).isEqualTo(Capability.CLUSTER_READ);
    }
}
