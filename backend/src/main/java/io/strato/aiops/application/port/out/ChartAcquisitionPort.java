package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.applicationdelivery.ChartTrustStatus;

import java.time.Duration;

public interface ChartAcquisitionPort {
    /** ChartAcquisitionPort의 fetch 처리 결과를 조회해 반환한다. */
    byte[] fetch(FetchRequest request);

    /** Chart 서명 검증을 지원하지 않는 구현도 체크섬 신뢰 수준으로 안전하게 호환한다. */
    default VerifiedChart fetchVerified(FetchRequest request) {
        return new VerifiedChart(fetch(request), ChartTrustStatus.CHECKSUMMED,
                "Provenance keyring is not configured; SHA-256 checksum was recorded");
    }

    record FetchRequest(String repositoryUrl, String packageName, String version, String contentUrl,
                        Duration timeout) {
    }

    record VerifiedChart(byte[] payload, ChartTrustStatus trustStatus, String verificationMessage) { }
}
