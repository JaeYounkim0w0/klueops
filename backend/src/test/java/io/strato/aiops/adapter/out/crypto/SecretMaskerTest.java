package io.strato.aiops.adapter.out.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretMaskerTest {

    private final SecretMasker secretMasker = new SecretMasker();

    @Test
    void detectsSensitiveKeyPatterns() {
        assertThat(secretMasker.isSensitiveKey("database-password")).isTrue();
        assertThat(secretMasker.isSensitiveKey("api_token")).isTrue();
        assertThat(secretMasker.isSensitiveKey("displayName")).isFalse();
    }

    @Test
    void masksNonBlankValues() {
        assertThat(secretMasker.maskValue("secret-value")).isEqualTo("***");
    }
}

