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

    /** FeatureKey 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    FeatureKey(boolean mandatory) {
        this.mandatory = mandatory;
    }

    /** FeatureKey의 mandatory 처리에 필요한 업무 로직을 수행한다. */
    public boolean mandatory() {
        return mandatory;
    }
}
