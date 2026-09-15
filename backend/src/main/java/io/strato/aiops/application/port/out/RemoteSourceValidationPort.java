package io.strato.aiops.application.port.out;

import java.net.URI;

public interface RemoteSourceValidationPort {
    URI requirePublicHttps(String value);
}
