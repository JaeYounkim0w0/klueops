package io.strato.aiops.domain.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedLocaleTest {

    /** SupportedLocaleTest의 resolvesSupportedLanguageFamiliesFromAcceptLanguage 처리에 필요한 결과를 조합해 반환한다. */
    @Test
    void resolvesSupportedLanguageFamiliesFromAcceptLanguage() {
        assertThat(SupportedLocale.fromAcceptLanguage("ko-KR,ko;q=0.9,en;q=0.8"))
                .isEqualTo(SupportedLocale.KOREAN);
        assertThat(SupportedLocale.fromAcceptLanguage("en-GB,en;q=0.9"))
                .isEqualTo(SupportedLocale.ENGLISH);
    }

    /** SupportedLocaleTest의 fallsBackToEnglishForMissingOrUnsupportedLanguage 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void fallsBackToEnglishForMissingOrUnsupportedLanguage() {
        assertThat(SupportedLocale.fromAcceptLanguage(null)).isEqualTo(SupportedLocale.ENGLISH);
        assertThat(SupportedLocale.fromAcceptLanguage("fr-FR,fr;q=0.9")).isEqualTo(SupportedLocale.ENGLISH);
    }
}
