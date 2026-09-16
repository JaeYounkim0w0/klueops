package io.strato.aiops.application.port.out;

import java.util.List;

public interface ApplicationExposurePort {
    /** ApplicationExposurePort의 applyHttpRoute 처리 계약을 정의한다. */
    ExposureResult applyHttpRoute(KubernetesConnectionCredential credential, HttpRouteRequest request);
    /** ApplicationExposurePort의 deleteHttpRoute 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteHttpRoute(KubernetesConnectionCredential credential, String namespace, String routeName);
    /** ApplicationExposurePort의 discoverHttpGateways 처리 계약을 정의한다. */
    GatewayDiscovery discoverHttpGateways(KubernetesConnectionCredential credential, String routeNamespace);
    /** Ingress companion resource를 적용한다. */
    ExposureResult applyIngress(KubernetesConnectionCredential credential, IngressRequest request);
    /** Ingress companion resource를 제거한다. */
    void deleteIngress(KubernetesConnectionCredential credential, String namespace, String name);
    /** TCPRoute companion resource를 적용한다. */
    ExposureResult applyTcpRoute(KubernetesConnectionCredential credential, TcpRouteRequest request);
    /** TCPRoute companion resource를 제거한다. */
    void deleteTcpRoute(KubernetesConnectionCredential credential, String namespace, String name);
    /** TCPRoute를 허용하는 Gateway listener를 조회한다. */
    GatewayDiscovery discoverTcpGateways(KubernetesConnectionCredential credential, String routeNamespace);
    /** cross-namespace backend가 ReferenceGrant로 명시적으로 허용됐는지 검증한다. */
    void requireBackendReference(KubernetesConnectionCredential credential, String routeNamespace,
                                 String serviceNamespace, String serviceName, int servicePort, String routeKind);
    /** Helm uninstall 전에 보존 대상 PVC/TLS Secret에 keep 정책을 적용한다. */
    CleanupInventory prepareUninstall(KubernetesConnectionCredential credential, String namespace, String releaseName,
                                      boolean preservePvcs, boolean preserveTls);
    /** Helm uninstall 뒤 사용자가 제거를 선택한 PVC/TLS Secret을 확실히 정리한다. */
    void finalizeUninstall(KubernetesConnectionCredential credential, String namespace, String releaseName,
                           boolean preservePvcs, boolean preserveTls);

    record HttpRouteRequest(String namespace, String routeName, String gatewayNamespace, String gatewayName,
                            String hostname, String path, String serviceNamespace, String serviceName, int servicePort) { }
    record IngressRequest(String namespace, String name, String hostname, String path,
                          String serviceName, int servicePort) { }
    record TcpRouteRequest(String namespace, String routeName, String gatewayNamespace, String gatewayName,
                           String serviceNamespace, String serviceName, int servicePort) { }
    record ExposureResult(String url, String status) { }
    record GatewayDiscovery(String status, String message, List<GatewayOption> gateways) { }
    record GatewayOption(String namespace, String name, String readiness, List<GatewayListener> listeners) { }
    record GatewayListener(String name, String protocol, Integer port, String hostname) { }
    record CleanupInventory(int pvcCount, int tlsSecretCount) { }
}
