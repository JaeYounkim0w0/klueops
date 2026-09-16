package io.strato.aiops.adapter.out.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretMaskerTest {

    private final SecretMasker secretMasker = new SecretMasker();

    /** SecretMaskerTest의 detectsSensitiveKeyPatterns 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void detectsSensitiveKeyPatterns() {
        assertThat(secretMasker.isSensitiveKey("database-password")).isTrue();
        assertThat(secretMasker.isSensitiveKey("api_token")).isTrue();
        assertThat(secretMasker.isSensitiveKey("displayName")).isFalse();
    }

    /** SecretMaskerTest의 masksNonBlankValues 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void masksNonBlankValues() {
        assertThat(secretMasker.maskValue("secret-value")).isEqualTo("***");
    }
}

