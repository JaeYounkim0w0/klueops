package io.strato.aiops.domain.analysis;

import java.util.Locale;

public enum SupportedLocale {
    KOREAN("ko-KR", "Korean"),
    ENGLISH("en-US", "English");

    private final String tag;
    private final String responseLanguage;

    SupportedLocale(String tag, String responseLanguage) {
        this.tag = tag;
        this.responseLanguage = responseLanguage;
    }

    public String tag() {
        return tag;
    }

    public String responseLanguage() {
        return responseLanguage;
    }

    public static SupportedLocale fromAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return ENGLISH;
        }
        String primaryTag = acceptLanguage.split(",", 2)[0].trim().split(";", 2)[0].trim();
        String language = Locale.forLanguageTag(primaryTag).getLanguage();
        return "ko".equalsIgnoreCase(language) ? KOREAN : ENGLISH;
    }
}
