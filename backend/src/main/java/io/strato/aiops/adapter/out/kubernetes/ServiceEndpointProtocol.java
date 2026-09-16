package io.strato.aiops.adapter.out.kubernetes;

import java.util.Locale;
import java.util.Set;

/**
 * Kubernetes Service port를 브라우저용 HTTP와 일반 TCP endpoint로 보수적으로 구분한다.
 */
final class ServiceEndpointProtocol {
    private static final Set<Integer> COMMON_HTTP_PORTS = Set.of(80, 3000, 8000, 8080);
    private static final Set<String> NON_HTTP_NAMES = Set.of(
            "postgres", "postgresql", "mysql", "mariadb", "redis", "mongodb", "amqp", "kafka", "tcp");

    /** ServiceEndpointProtocol 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private ServiceEndpointProtocol() { }

    /** ServiceEndpointProtocol의 scheme 처리에 필요한 업무 로직을 수행한다. */
    static String scheme(String transportProtocol, String appProtocol, String portName, Integer port) {
        String transport = normalized(transportProtocol);
        if (!transport.isBlank() && !"tcp".equals(transport)) return transport;
        String application = normalized(appProtocol);
        if (application.contains("https")) return "https";
        if (application.contains("http") || application.contains("h2c")) return "http";
        String name = normalized(portName);
        if (NON_HTTP_NAMES.stream().anyMatch(name::contains)) return "tcp";
        if (name.startsWith("https") || Integer.valueOf(443).equals(port)) return "https";
        if (name.startsWith("http") || name.startsWith("web") || COMMON_HTTP_PORTS.contains(port)) return "http";
        return "tcp";
    }

    /** ServiceEndpointProtocol의 normalized 처리 데이터를 필요한 표현으로 변환한다. */
    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
