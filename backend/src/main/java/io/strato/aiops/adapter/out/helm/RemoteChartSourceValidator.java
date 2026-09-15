package io.strato.aiops.adapter.out.helm;

import org.springframework.stereotype.Component;
import io.strato.aiops.application.port.out.RemoteSourceValidationPort;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

@Component
public class RemoteChartSourceValidator implements RemoteSourceValidationPort {

    @Override
    public URI requirePublicHttps(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Helm repository URL is invalid", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Only public HTTPS Helm repositories are allowed");
        }
        try {
            // DNS 결과 전체를 확인하여 내부망 우회와 메타데이터 엔드포인트 접근을 차단한다.
            if (Arrays.stream(InetAddress.getAllByName(uri.getHost())).anyMatch(this::isPrivateAddress)) {
                throw new IllegalArgumentException("Private or local Helm repository addresses are not allowed");
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Helm repository host could not be resolved", exception);
        }
        return uri;
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        // IPv4 CGNAT(100.64/10), benchmark(198.18/15), 문서용 대역도 외부 소스로 허용하지 않는다.
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return (first == 100 && second >= 64 && second <= 127)
                    || (first == 198 && (second == 18 || second == 19))
                    || first == 0 || first >= 224;
        }
        return false;
    }
}
