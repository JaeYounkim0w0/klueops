import { createI18n } from 'vue-i18n';

import enUS from './locales/en-US';
import koKR from './locales/ko-KR';
import { getLocale, subscribeLocale } from './locale';

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: getLocale(),
  fallbackLocale: 'en-US',
  messages: {
    'en-US': enUS,
    'ko-KR': koKR,
  },
  datetimeFormats: {
    'en-US': {
      short: { year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' },
    },
    'ko-KR': {
      short: { year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' },
    },
  },
});

subscribeLocale((locale) => {
  i18n.global.locale.value = locale;
});

export * from './locale';
