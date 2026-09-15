package io.strato.aiops.application.port.out;

import java.util.List;

public interface ApplicationRuntimeInspectionPort {
    RuntimeOverview inspect(KubernetesConnectionCredential credential, String namespace, String releaseName);

    record RuntimeOverview(int readyPods, int totalPods, int restarts, List<Workload> workloads,
                           List<Endpoint> endpoints) { }
    record Workload(String kind, String name, int ready, int desired, String status) { }
    record Endpoint(String type, String name, String url, String status) { }
}
