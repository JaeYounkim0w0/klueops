package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;

import java.util.List;

public record KubernetesStateInventory(
        List<KubernetesResourceSnapshot.CollectedResource> resources,
        List<KubernetesEventSnapshot.CollectedEvent> events
) {
    /** KubernetesStateInventory 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public KubernetesStateInventory {
        resources = List.copyOf(resources);
        events = List.copyOf(events);
    }
}
