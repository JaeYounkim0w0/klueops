package io.strato.aiops.application.port.out;

import java.util.List;

public interface ApplicationRuntimeInspectionPort {
    /** ApplicationRuntimeInspectionPort의 inspect 처리 계약을 정의한다. */
    RuntimeOverview inspect(KubernetesConnectionCredential credential, String namespace, String releaseName);

    record RuntimeOverview(int readyPods, int totalPods, int restarts, List<Workload> workloads,
                           List<Endpoint> endpoints) { }
    record Workload(String kind, String name, int ready, int desired, String status) { }
    record Endpoint(String type, String name, String url, String status, String address, Integer port,
                    String targetPort, Integer nodePort, String accessScope) {
        /** Endpoint 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        public Endpoint(String type, String name, String url, String status) {
            this(type, name, url, status, null, null, null, null, null);
        }
    }
}
