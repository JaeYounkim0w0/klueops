<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';

import JobDock from '@/components/JobDock.vue';
import GlobalOperatorSearch from '@/components/GlobalOperatorSearch.vue';
import NotificationCenter from '@/components/NotificationCenter.vue';
import RuntimeReadinessBanner from '@/components/RuntimeReadinessBanner.vue';
import TenantWorkspaceSelector from '@/components/TenantWorkspaceSelector.vue';
import { clearRouteFailure, routeFailure } from '@/router/routeFailure';
import { sessionExpiredRedirect, useAuthStore } from '@/stores/auth';
import { useRouter } from 'vue-router';
import { onUnauthorized } from '@/api/http';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const { t } = useI18n();
const mobileNavOpen = ref(false);
const sessionWarningOpen = ref(false);
const sessionSecondsRemaining = ref(0);
const extendingSession = ref(false);
const operatorSearchOpen = ref(false);
let stopUnauthorizedListener: (() => void) | undefined;
let sessionTimer: number | undefined;
let lastOperatorActivityAt = Date.now();
let lastExtensionAttemptAt = 0;

const sessionRemainingLabel = computed(() => {
  const minutes = Math.floor(sessionSecondsRemaining.value / 60);
  const seconds = sessionSecondsRemaining.value % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
});

onMounted(() => {
  stopUnauthorizedListener = onUnauthorized(() => {
    void redirectToExpiredSession();
  });
  window.addEventListener('pointerdown', markOperatorActivity, { passive: true });
  window.addEventListener('keydown', markOperatorActivity);
  window.addEventListener('keydown', openOperatorSearchShortcut);
  document.addEventListener('visibilitychange', evaluateSession);
  sessionTimer = window.setInterval(evaluateSession, 30_000);
  void evaluateSession();
});
onUnmounted(() => {
  stopUnauthorizedListener?.();
  window.removeEventListener('pointerdown', markOperatorActivity);
  window.removeEventListener('keydown', markOperatorActivity);
  window.removeEventListener('keydown', openOperatorSearchShortcut);
  document.removeEventListener('visibilitychange', evaluateSession);
  if (sessionTimer !== undefined) window.clearInterval(sessionTimer);
});

function reloadApplication(): void {
  clearRouteFailure();
  window.location.reload();
}

function logout(): void {
  auth.logout();
}

function markOperatorActivity(): void {
  lastOperatorActivityAt = Date.now();
}

function openOperatorSearchShortcut(event: KeyboardEvent): void {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    operatorSearchOpen.value = true;
  }
}

async function evaluateSession(): Promise<void> {
  const policy = auth.session.session;
  if (!auth.authenticated || !policy) {
    sessionWarningOpen.value = false;
    return;
  }
  const now = Date.now();
  const idleRemaining = Date.parse(policy.expiresAt) - now;
  const absoluteRemaining = Date.parse(policy.absoluteExpiresAt) - now;
  const remaining = Math.max(0, Math.min(idleRemaining, absoluteRemaining));
  sessionSecondsRemaining.value = Math.ceil(remaining / 1000);
  if (remaining <= 0) {
    sessionWarningOpen.value = false;
    await redirectToExpiredSession();
    return;
  }

  const recentlyActive = now - lastOperatorActivityAt <= 5 * 60_000;
  const mayAutoExtend = policy.canExtend
    && document.visibilityState !== 'hidden'
    && recentlyActive
    && idleRemaining <= 10 * 60_000
    && absoluteRemaining > 5 * 60_000
    && now - lastExtensionAttemptAt >= 60_000;
  if (mayAutoExtend) {
    lastExtensionAttemptAt = now;
    try {
      await auth.extendSession();
      sessionWarningOpen.value = false;
      return;
    } catch {
      // The warning below remains available as a deliberate recovery action.
    }
  }
  sessionWarningOpen.value = remaining <= 5 * 60_000;
}

async function redirectToExpiredSession(): Promise<void> {
  const target = sessionExpiredRedirect(route.fullPath, route.meta.public === true);
  if (target) await router.replace(target);
}

async function extendSession(): Promise<void> {
  extendingSession.value = true;
  try {
    await auth.extendSession();
    markOperatorActivity();
    sessionWarningOpen.value = false;
    await evaluateSession();
  } finally {
    extendingSession.value = false;
  }
}

watch(
  () => route.fullPath,
  () => {
    mobileNavOpen.value = false;
  },
);
</script>

<template>
  <RouterView v-if="route.meta.public === true" />
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ 'nav-open': mobileNavOpen }">
      <div class="sidebar-header">
        <div class="brand">
          <span class="brand-mark">K</span>
          <div>
            <strong>KlueOps</strong>
            <small>{{ t('shell.console') }}</small>
          </div>
        </div>
        <button
          type="button"
          class="mobile-nav-toggle"
          :aria-expanded="mobileNavOpen"
          aria-controls="primary-navigation"
          :aria-label="mobileNavOpen ? t('shell.closeMenu') : t('shell.openMenu')"
          :title="mobileNavOpen ? t('shell.closeMenu') : t('shell.openMenu')"
          @click="mobileNavOpen = !mobileNavOpen"
        >
          <i :class="mobileNavOpen ? 'pi pi-times' : 'pi pi-bars'"></i>
        </button>
      </div>
      <TenantWorkspaceSelector />
      <button v-if="auth.hasCapability('cluster:read')" type="button" class="sidebar-search-button" @click="operatorSearchOpen = true">
        <i class="pi pi-search"></i><span>{{ t('operatorSearch.title') }}</span><kbd>⌘K</kbd>
      </button>
      <nav id="primary-navigation" class="nav">
        <NotificationCenter />
        <RouterLink v-if="auth.hasCapability('cluster:read')" to="/" class="nav-item">
          <i class="pi pi-home"></i>
          <span>{{ t('shell.dashboard') }}</span>
        </RouterLink>

        <div class="nav-group">
          <div class="nav-group-title">{{ t('shell.operations') }}</div>
          <RouterLink v-if="auth.hasCapability('analysis:read')" to="/triage" class="nav-item nav-child">
            <i class="pi pi-filter"></i>
            <span>{{ t('shell.triage') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('cluster:read')" to="/operations/fleet" class="nav-item nav-child">
            <i class="pi pi-sitemap"></i>
            <span>{{ t('shell.fleet') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('analysis:read')" to="/incidents" class="nav-item nav-child">
            <i class="pi pi-exclamation-circle"></i>
            <span>{{ t('shell.incidents') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('cluster:read')" to="/clusters" class="nav-item nav-child">
            <i class="pi pi-cloud"></i>
            <span>{{ t('shell.clusters') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('application:read')" to="/applications" class="nav-item nav-child">
            <i class="pi pi-box"></i>
            <span>{{ t('shell.applications') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('cluster:read')" to="/policies" class="nav-item nav-child">
            <i class="pi pi-shield"></i>
            <span>{{ t('shell.policies') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('audit:read')" to="/audit" class="nav-item nav-child">
            <i class="pi pi-history"></i>
            <span>{{ t('shell.audit') }}</span>
          </RouterLink>
        </div>

        <div class="nav-group">
          <div class="nav-group-title">{{ t('shell.ai') }}</div>
          <RouterLink v-if="auth.hasCapability('analysis:read')" to="/analysis" class="nav-item">
            <i class="pi pi-chart-line"></i>
            <span>{{ t('shell.analysis') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('analysis:read')" to="/ai-chat" class="nav-item">
            <i class="pi pi-comments"></i>
            <span>{{ t('shell.chat') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('analysis:read')" to="/runbooks" class="nav-item">
            <i class="pi pi-book"></i>
            <span>{{ t('shell.runbooks') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('analysis:read')" to="/ai/trust" class="nav-item">
            <i class="pi pi-verified"></i>
            <span>{{ t('shell.trustCenter') }}</span>
          </RouterLink>
        </div>

        <div class="nav-group">
          <div class="nav-group-title">{{ t('shell.settings') }}</div>
          <RouterLink to="/settings/preferences" class="nav-item">
            <i class="pi pi-language"></i>
            <span>{{ t('shell.preferences') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('platform:admin')" to="/settings/operations" class="nav-item">
            <i class="pi pi-cog"></i>
            <span>{{ t('shell.dataRuntime') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('ai:routing:manage')" to="/settings/ai-providers" class="nav-item">
            <i class="pi pi-sparkles"></i>
            <span>AI Providers</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('audit:read')" to="/settings/reliability" class="nav-item">
            <i class="pi pi-verified"></i>
            <span>{{ t('shell.reliability') }}</span>
          </RouterLink>
          <RouterLink v-if="!auth.session.localDevelopment && auth.hasCapability('identity:manage')" to="/settings/access" class="nav-item">
            <i class="pi pi-users"></i>
            <span>{{ t('shell.access') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('tenant:member:manage')" to="/settings/users-access" class="nav-item">
            <i class="pi pi-user-edit"></i>
            <span>Users &amp; Access</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('platform:admin')" to="/settings/tenancy" class="nav-item">
            <i class="pi pi-building"></i>
            <span>{{ t('tenancy.manage') }}</span>
          </RouterLink>
        </div>
      </nav>
      <div class="identity-summary">
        <span class="identity-avatar" aria-hidden="true">{{ auth.user?.displayName?.slice(0, 1).toUpperCase() ?? '?' }}</span>
        <div>
          <strong>{{ auth.user?.displayName ?? t('auth.unknownUser') }}</strong>
          <small v-if="auth.session.localDevelopment">{{ t('auth.localMode') }}</small>
          <small v-else>{{ auth.user?.username }}</small>
        </div>
        <button v-if="!auth.session.localDevelopment" type="button" class="icon-button" :title="t('auth.logout')" :aria-label="t('auth.logout')" @click="logout">
          <i class="pi pi-sign-out"></i>
        </button>
      </div>
    </aside>
    <button
      v-if="mobileNavOpen"
      type="button"
      class="mobile-nav-backdrop"
      :aria-label="t('shell.closeMenu')"
      @click="mobileNavOpen = false"
    ></button>
    <main class="content">
      <RuntimeReadinessBanner />
      <section v-if="routeFailure" class="route-failure-banner" role="alert">
        <i class="pi pi-exclamation-triangle"></i>
        <div>
          <strong>{{ t('shell.routeFailureTitle') }}</strong>
          <span>{{ routeFailure.message }}</span>
        </div>
        <button type="button" class="secondary-button" @click="reloadApplication">
          <i class="pi pi-refresh"></i>
          {{ t('common.refresh') }}
        </button>
        <button type="button" class="icon-button" :aria-label="t('shell.dismissError')" :title="t('common.close')" @click="clearRouteFailure">
          <i class="pi pi-times"></i>
        </button>
      </section>
      <RouterView />
    </main>
    <JobDock />
    <GlobalOperatorSearch :open="operatorSearchOpen" @close="operatorSearchOpen = false" />
    <div v-if="sessionWarningOpen" class="session-warning-backdrop">
      <section class="session-warning" role="dialog" aria-modal="true" aria-labelledby="session-warning-title">
        <i class="pi pi-clock" aria-hidden="true"></i>
        <div>
          <strong id="session-warning-title">{{ t('auth.sessionExpiringTitle') }}</strong>
          <p>{{ t('auth.sessionExpiringDescription', { time: sessionRemainingLabel }) }}</p>
        </div>
        <div class="session-warning-actions">
          <button type="button" class="secondary-button" @click="logout">{{ t('auth.logout') }}</button>
          <button type="button" class="primary-button" :disabled="extendingSession" @click="extendSession">
            <i class="pi pi-refresh"></i>
            {{ extendingSession ? t('auth.sessionExtending') : t('auth.extendSession') }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
