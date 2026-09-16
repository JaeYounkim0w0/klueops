package io.strato.aiops.application.port.out;

import java.net.URI;

public interface RemoteSourceValidationPort {
    /** RemoteSourceValidationPort의 requirePublicHttps 처리 입력과 현재 상태의 유효성을 검증한다. */
    URI requirePublicHttps(String value);
}
