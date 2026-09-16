<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';

import { classifyLoginFailure, type LoginFailure, useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const { t } = useI18n();
const checkingSession = ref(true);
const loginFailure = ref<LoginFailure>(classifyLoginFailure(undefined, route.query.reason));
const noticeTitle = computed(() => loginFailure.value === 'identity-provider'
  ? t('auth.identityProviderUnavailable')
  : t('auth.sessionCheckFailed'));
const noticeDetail = computed(() => loginFailure.value === 'identity-provider'
  ? t('auth.identityProviderContact')
  : t('auth.sessionCheckRetry'));

/** returnTo 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function returnTo(): string {
  return typeof route.query.returnTo === 'string' ? route.query.returnTo : '/';
}

/** redirectAuthenticatedSession 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function redirectAuthenticatedSession(): Promise<void> {
  const destination = await auth.authenticatedReturnTo(returnTo());
  if (destination) await router.replace(destination);
}

/** login 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function login(): Promise<void> {
  loginFailure.value = null;
  checkingSession.value = true;
  try {
    const destination = await auth.continueLogin(returnTo());
    if (destination) await router.replace(destination);
  } catch (error) {
    loginFailure.value = classifyLoginFailure(error, route.query.reason);
  } finally {
    checkingSession.value = false;
  }
}

onMounted(async () => {
  try {
    await redirectAuthenticatedSession();
  } catch (error) {
    loginFailure.value = classifyLoginFailure(error, route.query.reason);
  } finally {
    checkingSession.value = false;
  }
});
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel" aria-labelledby="login-title">
      <div class="auth-brand"><span>K</span><strong>KlueOps</strong></div>
      <div>
        <span class="section-eyebrow">SECURE OPERATIONS</span>
        <h1 id="login-title">{{ t('auth.loginTitle') }}</h1>
        <p>{{ t('auth.loginDescription') }}</p>
      </div>
      <div v-if="loginFailure" class="auth-notice" role="alert">
        <strong>{{ noticeTitle }}</strong>
        <span>{{ noticeDetail }}</span>
      </div>
      <button type="button" class="primary-button auth-login-button" :disabled="checkingSession"
        :aria-busy="checkingSession" @click="login">
        <i class="pi pi-sign-in"></i>
        {{ checkingSession ? t('auth.identityProviderPreparing') : (loginFailure ? t('common.retry') : t('auth.login')) }}
      </button>
      <small>{{ t('auth.loginHint') }}</small>
    </section>
  </main>
</template>
