package io.strato.aiops.application.service;

import org.springframework.stereotype.Component;

@Component
public class CommandOutputSanitizer {
    /** CommandOutputSanitizer의 sanitize 처리에 필요한 업무 로직을 수행한다. */
    public String sanitize(String value) {
        if (value == null || value.isEmpty()) return "";
        return value
                .replaceAll("(?im)^([\\s-]*(?:token|password|client-key-data|client-certificate-data|certificate-authority-data):\\s*).+$", "$1***")
                .replaceAll("(?i)(authorization[=:]\\s*(?:bearer\\s+)?)[A-Za-z0-9._~+/-]{12,}", "$1***")
                .replaceAll("(?s)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----.*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", "***PRIVATE KEY REDACTED***");
    }
}
