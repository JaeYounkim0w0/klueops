package io.strato.aiops.domain.identity;

public enum FeatureKey {
    CORE_OVERVIEW(true),
    CLUSTER_OPERATIONS(false),
    KUBERNETES_CONSOLE(false),
    AI_OPERATIONS(false),
    APPLICATION_DELIVERY(false),
    AI_PROVIDER_ROUTING(false),
    AI_PROVIDER_PLATFORM(false),
    ACCESS_CONTROL(true),
    AUDIT(true),
    PLATFORM_ADMINISTRATION(true);

    private final boolean mandatory;

    FeatureKey(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean mandatory() {
        return mandatory;
    }
}
