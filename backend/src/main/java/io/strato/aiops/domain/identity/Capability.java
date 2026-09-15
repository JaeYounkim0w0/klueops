package io.strato.aiops.domain.identity;

public enum Capability {
    PLATFORM_ADMIN("platform:admin"),
    IDENTITY_MANAGE("identity:manage"),
    TENANT_READ("tenant:read"),
    TENANT_MANAGE("tenant:manage"),
    TENANT_MEMBER_MANAGE("tenant:member:manage"),
    CLUSTER_READ("cluster:read"),
    CLUSTER_MANAGE("cluster:manage"),
    ANALYSIS_READ("analysis:read"),
    ANALYSIS_RUN("analysis:run"),
    OPERATION_EXECUTE("operation:execute"),
    POLICY_MANAGE("policy:manage"),
    AUDIT_READ("audit:read"),
    CHART_READ("chart:read"),
    CHART_IMPORT("chart:import"),
    CHART_MANAGE("chart:manage"),
    VALUES_EDIT("values:edit"),
    APPLICATION_READ("application:read"),
    APPLICATION_DEPLOY("application:deploy"),
    APPLICATION_ROLLBACK("application:rollback"),
    APPLICATION_DELETE("application:delete"),
    APPLICATION_EXPOSURE("application:exposure"),
    NAMESPACE_CREATE("namespace:create"),
    AI_ROUTING_MANAGE("ai-routing:manage"),
    AI_PROVIDER_MANAGE("ai-provider:manage"),
    AI_MODEL_MANAGE("ai-model:manage");

    private final String value;

    Capability(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
