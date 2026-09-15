package io.strato.aiops.application.port.out;

public interface ApplicationExposurePort {
    ExposureResult applyHttpRoute(KubernetesConnectionCredential credential, HttpRouteRequest request);
    void deleteHttpRoute(KubernetesConnectionCredential credential, String namespace, String routeName);

    record HttpRouteRequest(String namespace, String routeName, String gatewayNamespace, String gatewayName,
                            String hostname, String path, String serviceName, int servicePort) { }
    record ExposureResult(String url, String status) { }
}
