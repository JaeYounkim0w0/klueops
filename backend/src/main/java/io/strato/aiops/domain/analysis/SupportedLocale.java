package io.strato.aiops.domain.analysis;

import java.util.Locale;

public enum SupportedLocale {
    KOREAN("ko-KR", "Korean"),
    ENGLISH("en-US", "English");

    private final String tag;
    private final String responseLanguage;

    /** SupportedLocale 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    SupportedLocale(String tag, String responseLanguage) {
        this.tag = tag;
        this.responseLanguage = responseLanguage;
    }

    /** SupportedLocale의 tag 처리에 필요한 업무 로직을 수행한다. */
    public String tag() {
        return tag;
    }

    /** SupportedLocale의 responseLanguage 처리에 필요한 업무 로직을 수행한다. */
    public String responseLanguage() {
        return responseLanguage;
    }

    /** SupportedLocale의 fromAcceptLanguage 처리 데이터를 필요한 표현으로 변환한다. */
    public static SupportedLocale fromAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return ENGLISH;
        }
        String primaryTag = acceptLanguage.split(",", 2)[0].trim().split(";", 2)[0].trim();
        String language = Locale.forLanguageTag(primaryTag).getLanguage();
        return "ko".equalsIgnoreCase(language) ? KOREAN : ENGLISH;
    }
}
