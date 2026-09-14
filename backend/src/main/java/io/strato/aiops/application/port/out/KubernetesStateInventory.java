package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;

import java.util.List;

public record KubernetesStateInventory(
        List<KubernetesResourceSnapshot.CollectedResource> resources,
        List<KubernetesEventSnapshot.CollectedEvent> events
) {
    public KubernetesStateInventory {
        resources = List.copyOf(resources);
        events = List.copyOf(events);
    }
}
