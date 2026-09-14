package io.strato.aiops.adapter.out.crypto;

import io.strato.aiops.application.port.out.SensitiveDataMaskingPort;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SecretMasker implements SensitiveDataMaskingPort {

    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+\\-/]+=*");
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)((?:password|passwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)\\s*[:=]\\s*)[^\\s,;]+"
    );

    private static final Set<String> SENSITIVE_TOKENS = Set.of(
            "password",
            "passwd",
            "secret",
            "token",
            "apikey",
            "accesskey",
            "privatekey",
            "certificate",
            "credential"
    );

    @Override
    public boolean isSensitiveKey(String key) {
        String normalized = key.replace("-", "").replace("_", "").toLowerCase();
        return SENSITIVE_TOKENS.stream().anyMatch(normalized::contains);
    }

    @Override
    public String maskValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return "***";
    }

    @Override
    public String maskText(String value) {
        if (value == null || value.isBlank()) return value;
        return ASSIGNMENT.matcher(BEARER.matcher(value).replaceAll("$1***")).replaceAll("$1***");
    }
}
