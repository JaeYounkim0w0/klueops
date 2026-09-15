package io.strato.aiops.application.port.out;

import java.util.List;

public interface ApplicationExposurePort {
    ExposureResult applyHttpRoute(KubernetesConnectionCredential credential, HttpRouteRequest request);
    void deleteHttpRoute(KubernetesConnectionCredential credential, String namespace, String routeName);
    GatewayDiscovery discoverHttpGateways(KubernetesConnectionCredential credential);

    record HttpRouteRequest(String namespace, String routeName, String gatewayNamespace, String gatewayName,
                            String hostname, String path, String serviceName, int servicePort) { }
    record ExposureResult(String url, String status) { }
    record GatewayDiscovery(String status, String message, List<GatewayOption> gateways) { }
    record GatewayOption(String namespace, String name, String readiness, List<GatewayListener> listeners) { }
    record GatewayListener(String name, String protocol, Integer port, String hostname) { }
}
