package io.strato.aiops.domain.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedLocaleTest {

    @Test
    void resolvesSupportedLanguageFamiliesFromAcceptLanguage() {
        assertThat(SupportedLocale.fromAcceptLanguage("ko-KR,ko;q=0.9,en;q=0.8"))
                .isEqualTo(SupportedLocale.KOREAN);
        assertThat(SupportedLocale.fromAcceptLanguage("en-GB,en;q=0.9"))
                .isEqualTo(SupportedLocale.ENGLISH);
    }

    @Test
    void fallsBackToEnglishForMissingOrUnsupportedLanguage() {
        assertThat(SupportedLocale.fromAcceptLanguage(null)).isEqualTo(SupportedLocale.ENGLISH);
        assertThat(SupportedLocale.fromAcceptLanguage("fr-FR,fr;q=0.9")).isEqualTo(SupportedLocale.ENGLISH);
    }
}
