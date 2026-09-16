package io.strato.aiops.adapter.out.kubernetes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceEndpointProtocolTest {
    /** ServiceEndpointProtocolTest의 classifiesDatabasePortsAsTcpInsteadOfHttp 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void classifiesDatabasePortsAsTcpInsteadOfHttp() {
        assertThat(ServiceEndpointProtocol.scheme("TCP", null, "postgresql", 5432)).isEqualTo("tcp");
        assertThat(ServiceEndpointProtocol.scheme("TCP", null, "redis", 80)).isEqualTo("tcp");
    }

    /** ServiceEndpointProtocolTest의 honorsApplicationProtocolAndCommonHttpPorts 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void honorsApplicationProtocolAndCommonHttpPorts() {
        assertThat(ServiceEndpointProtocol.scheme("TCP", "kubernetes.io/h2c", "api", 9000)).isEqualTo("http");
        assertThat(ServiceEndpointProtocol.scheme("TCP", null, "web", 8081)).isEqualTo("http");
        assertThat(ServiceEndpointProtocol.scheme("TCP", null, null, 443)).isEqualTo("https");
    }
}
