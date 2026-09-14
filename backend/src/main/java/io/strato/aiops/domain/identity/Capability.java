package io.strato.aiops.domain.identity;

public enum Capability {
    PLATFORM_ADMIN("platform:admin"),
    IDENTITY_MANAGE("identity:manage"),
    CLUSTER_READ("cluster:read"),
    CLUSTER_MANAGE("cluster:manage"),
    ANALYSIS_READ("analysis:read"),
    ANALYSIS_RUN("analysis:run"),
    OPERATION_EXECUTE("operation:execute"),
    POLICY_MANAGE("policy:manage"),
    AUDIT_READ("audit:read");

    private final String value;

    Capability(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
