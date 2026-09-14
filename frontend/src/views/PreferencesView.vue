<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

import { setLocale, type SupportedLocale } from '@/i18n';

const { locale, t } = useI18n();

const activeLanguageName = computed(() =>
  locale.value === 'ko-KR' ? t('common.korean') : t('common.english'),
);

function selectLocale(nextLocale: SupportedLocale): void {
  if (nextLocale === 'ko-KR') {
    setLocale('ko-KR');
    return;
  }
  setLocale('en-US');
}
</script>

<template>
  <section class="page preferences-page">
    <header class="page-header">
      <div>
        <span class="section-eyebrow">{{ t('preferences.eyebrow') }}</span>
        <h1>{{ t('preferences.title') }}</h1>
        <p>{{ t('preferences.description') }}</p>
      </div>
    </header>

    <section class="preferences-band" aria-labelledby="display-language-title">
      <div class="preferences-copy">
        <i class="pi pi-language preferences-icon" aria-hidden="true"></i>
        <div>
          <h2 id="display-language-title">{{ t('preferences.languageTitle') }}</h2>
          <p>{{ t('preferences.languageDescription') }}</p>
        </div>
      </div>
      <div class="segmented-control language-selector" role="group" :aria-label="t('preferences.languageTitle')">
        <button
          type="button"
          :class="{ active: locale === 'ko-KR' }"
          :aria-pressed="locale === 'ko-KR'"
          @click="selectLocale('ko-KR')"
        >
          {{ t('common.korean') }}
        </button>
        <button
          type="button"
          :class="{ active: locale === 'en-US' }"
          :aria-pressed="locale === 'en-US'"
          @click="selectLocale('en-US')"
        >
          {{ t('common.english') }}
        </button>
      </div>
      <p class="preference-status" aria-live="polite">
        <i class="pi pi-check-circle" aria-hidden="true"></i>
        {{ t('preferences.selected', { language: activeLanguageName }) }}
        {{ t('preferences.appliedImmediately') }}
      </p>
    </section>

    <div class="preference-guidance-grid">
      <section class="preference-guidance">
        <i class="pi pi-shield" aria-hidden="true"></i>
        <div>
          <h2>{{ t('preferences.protectedTitle') }}</h2>
          <p>{{ t('preferences.protectedDescription') }}</p>
        </div>
      </section>
      <section class="preference-guidance">
        <i class="pi pi-sparkles" aria-hidden="true"></i>
        <div>
          <h2>{{ t('preferences.aiTitle') }}</h2>
          <p>{{ t('preferences.aiDescription') }}</p>
        </div>
      </section>
    </div>
  </section>
</template>
