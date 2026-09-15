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

const canOpenIncidentResponse = computed(() =>
  (auth.hasCapability('analysis:read') && auth.canNavigate('ai'))
  || (auth.hasCapability('cluster:read') && auth.canNavigate('clusters')),
);
const canOpenInfrastructure = computed(() => auth.hasCapability('cluster:read') && auth.canNavigate('clusters'));
const canOpenApplicationDelivery = computed(() => auth.hasCapability('application:read') && auth.canNavigate('applications'));
const canOpenAiOperations = computed(() => auth.hasCapability('analysis:read') && auth.canNavigate('ai'));
const canOpenGovernance = computed(() => canOpenInfrastructure.value || auth.hasCapability('audit:read'));
const canOpenTenantAccess = computed(() => auth.hasCapability('tenant:member:manage') && auth.canNavigate('access'));
const canOpenPlatformAccess = computed(() => !auth.session.localDevelopment && auth.hasCapability('identity:manage'));
const canOpenAccess = computed(() => canOpenTenantAccess.value || canOpenPlatformAccess.value);
const accessDestination = computed(() => canOpenTenantAccess.value ? '/settings/users-access' : '/settings/access');
const canOpenPlatformSettings = computed(() =>
  auth.hasCapability('platform:admin')
  || (auth.hasCapability('ai-routing:manage') && auth.canNavigate('aiProviders'))
  || canOpenAccess.value,
);

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
      </div>
      <button v-if="auth.hasCapability('cluster:read')" type="button" class="sidebar-search-button" @click="operatorSearchOpen = true">
        <i class="pi pi-search"></i><span>{{ t('operatorSearch.title') }}</span><kbd>⌘K</kbd>
      </button>
      <nav id="primary-navigation" class="nav">
        <div v-if="auth.hasCapability('cluster:read') && auth.canNavigate('overview')" class="nav-group">
          <div class="nav-group-title">{{ t('shell.overviewGroup') }}</div>
          <RouterLink to="/" class="nav-item">
            <i class="pi pi-home"></i>
            <span>{{ t('shell.dashboard') }}</span>
          </RouterLink>
        </div>

        <div v-if="canOpenIncidentResponse" class="nav-group">
          <div class="nav-group-title">{{ t('shell.incidentResponse') }}</div>
          <RouterLink v-if="auth.hasCapability('analysis:read') && auth.canNavigate('ai')" to="/triage" class="nav-item">
            <i class="pi pi-filter"></i>
            <span>{{ t('shell.triage') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('cluster:read') && auth.canNavigate('clusters')" to="/operations/fleet" class="nav-item">
            <i class="pi pi-sitemap"></i>
            <span>{{ t('shell.fleet') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('analysis:read') && auth.canNavigate('ai')" to="/incidents" class="nav-item">
            <i class="pi pi-exclamation-circle"></i>
            <span>{{ t('shell.incidents') }}</span>
          </RouterLink>
        </div>

        <div v-if="canOpenInfrastructure" class="nav-group">
          <div class="nav-group-title">{{ t('shell.infrastructure') }}</div>
          <RouterLink to="/clusters" class="nav-item">
            <i class="pi pi-cloud"></i>
            <span>{{ t('shell.clusters') }}</span>
          </RouterLink>
        </div>

        <div v-if="canOpenApplicationDelivery" class="nav-group">
          <div class="nav-group-title">{{ t('shell.applicationDelivery') }}</div>
          <RouterLink to="/applications" class="nav-item">
            <i class="pi pi-box"></i>
            <span>{{ t('shell.applications') }}</span>
          </RouterLink>
        </div>

        <div v-if="canOpenAiOperations" class="nav-group">
          <div class="nav-group-title">{{ t('shell.aiOperations') }}</div>
          <RouterLink to="/analysis" class="nav-item">
            <i class="pi pi-chart-line"></i>
            <span>{{ t('shell.analysis') }}</span>
          </RouterLink>
          <RouterLink to="/ai-chat" class="nav-item">
            <i class="pi pi-comments"></i>
            <span>{{ t('shell.chat') }}</span>
          </RouterLink>
          <RouterLink to="/runbooks" class="nav-item">
            <i class="pi pi-book"></i>
            <span>{{ t('shell.runbooks') }}</span>
          </RouterLink>
          <RouterLink to="/ai/trust" class="nav-item">
            <i class="pi pi-verified"></i>
            <span>{{ t('shell.trustCenter') }}</span>
          </RouterLink>
        </div>

        <div v-if="canOpenGovernance" class="nav-group">
          <div class="nav-group-title">{{ t('shell.governance') }}</div>
          <RouterLink v-if="canOpenInfrastructure" to="/policies" class="nav-item">
            <i class="pi pi-shield"></i>
            <span>{{ t('shell.policies') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('audit:read')" to="/audit" class="nav-item">
            <i class="pi pi-history"></i>
            <span>{{ t('shell.audit') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('audit:read')" to="/settings/reliability" class="nav-item">
            <i class="pi pi-verified"></i>
            <span>{{ t('shell.reliability') }}</span>
          </RouterLink>
        </div>

        <div v-if="canOpenPlatformSettings" class="nav-group">
          <div class="nav-group-title">{{ t('shell.platformSettings') }}</div>
          <RouterLink v-if="auth.hasCapability('platform:admin')" to="/settings/operations" class="nav-item">
            <i class="pi pi-cog"></i>
            <span>{{ t('shell.dataRuntime') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('ai-routing:manage') && auth.canNavigate('aiProviders')" to="/settings/ai-providers" class="nav-item">
            <i class="pi pi-sparkles"></i>
            <span>AI Providers</span>
          </RouterLink>
          <RouterLink
            v-if="canOpenAccess"
            :to="accessDestination"
            class="nav-item"
            :class="{ 'router-link-active': ['/settings/access', '/settings/users-access'].includes(route.path) }"
          >
            <i class="pi pi-user-edit"></i>
            <span>{{ t('shell.userAccess') }}</span>
          </RouterLink>
          <RouterLink v-if="auth.hasCapability('platform:admin')" to="/settings/tenancy" class="nav-item">
            <i class="pi pi-building"></i>
            <span>{{ t('tenancy.manage') }}</span>
          </RouterLink>
        </div>

        <div class="nav-group">
          <div class="nav-group-title">{{ t('shell.personal') }}</div>
          <RouterLink to="/settings/preferences" class="nav-item">
            <i class="pi pi-language"></i>
            <span>{{ t('shell.preferences') }}</span>
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
    <div class="workspace-shell">
      <header class="workspace-topbar">
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
        <TenantWorkspaceSelector />
        <div class="workspace-actions">
          <button v-if="auth.hasCapability('cluster:read')" type="button" class="workspace-action-button workspace-search-trigger" :aria-label="t('operatorSearch.title')" :title="t('operatorSearch.title')" @click="operatorSearchOpen = true">
            <i class="pi pi-search"></i><span>{{ t('operatorSearch.title') }}</span><kbd>⌘K</kbd>
          </button>
          <NotificationCenter />
        </div>
      </header>
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
    </div>
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
