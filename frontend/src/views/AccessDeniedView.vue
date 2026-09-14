<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';

const { t } = useI18n();
const router = useRouter();
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel access-denied-panel" aria-labelledby="denied-title">
      <i class="pi pi-lock auth-state-icon" aria-hidden="true"></i>
      <div>
        <h1 id="denied-title">{{ t('auth.deniedTitle') }}</h1>
        <p>{{ t('auth.deniedDescription') }}</p>
      </div>
      <button type="button" class="secondary-button" @click="router.push('/')">
        <i class="pi pi-home"></i>
        {{ t('auth.backToDashboard') }}
      </button>
    </section>
  </main>
</template>
