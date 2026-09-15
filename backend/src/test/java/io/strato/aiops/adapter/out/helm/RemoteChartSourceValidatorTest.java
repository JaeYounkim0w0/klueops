package io.strato.aiops.adapter.out.helm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteChartSourceValidatorTest {
    private final RemoteChartSourceValidator validator = new RemoteChartSourceValidator();

    @Test
    void rejectsNonHttpsAndCredentialBearingUrls() {
        assertThatThrownBy(() -> validator.requirePublicHttps("http://charts.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.requirePublicHttps("https://user:secret@charts.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLoopbackAndPrivateAddresses() {
        assertThatThrownBy(() -> validator.requirePublicHttps("https://127.0.0.1/index.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private or local");
        assertThatThrownBy(() -> validator.requirePublicHttps("https://10.0.0.8/index.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private or local");
    }
}
