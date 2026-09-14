import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

import { ApiError, readCookie, requestJson } from '@/api/http';

export type LoginFailure = 'session' | 'identity-provider' | null;

export interface AuthUser {
  id: string;
  username: string;
  displayName: string;
  email?: string | null;
  active: boolean;
}

export interface AuthScope {
  type: 'PLATFORM' | 'CLUSTER' | 'NAMESPACE';
  clusterId?: string | null;
  namespace?: string | null;
  role: 'PLATFORM_ADMIN' | 'CLUSTER_ADMIN' | 'OPERATOR' | 'VIEWER';
}

export interface BrowserSessionInfo {
  expiresAt: string;
  absoluteExpiresAt: string;
  idleTimeoutSeconds: number;
  canExtend: boolean;
}

export interface AuthSession {
  authenticated: boolean;
  localDevelopment: boolean;
  user: AuthUser | null;
  capabilities: string[];
  scopes: AuthScope[];
  loginUrl: string;
  logoutUrl: string;
  session: BrowserSessionInfo | null;
}

const ANONYMOUS: AuthSession = {
  authenticated: false,
  localDevelopment: false,
  user: null,
  capabilities: [],
  scopes: [],
  loginUrl: '/oauth2/authorization/aiops',
  logoutUrl: '/logout',
  session: null,
};

export const useAuthStore = defineStore('auth', () => {
  const session = ref<AuthSession>({ ...ANONYMOUS });
  const loaded = ref(false);
  const loading = ref(false);
  const authenticated = computed(() => session.value.authenticated);
  const user = computed(() => session.value.user);

  async function load(force = false): Promise<AuthSession> {
    if (loaded.value && !force) return session.value;
    loading.value = true;
    try {
      session.value = await requestJson<AuthSession>('/api/auth/me');
      loaded.value = true;
      return session.value;
    } finally {
      loading.value = false;
    }
  }

  function hasCapability(capability: string): boolean {
    return session.value.capabilities.includes(capability);
  }

  function beginLogin(returnTo = '/'): void {
    if (typeof window === 'undefined') return;
    window.sessionStorage.setItem('aiops.auth.returnTo', normalizeReturnTo(returnTo) ?? '/');
    window.location.assign(session.value.loginUrl || ANONYMOUS.loginUrl);
  }

  async function authenticatedReturnTo(returnTo = '/'): Promise<string | null> {
    const current = await load(true);
    return current.authenticated ? normalizeReturnTo(returnTo) ?? '/' : null;
  }

  async function continueLogin(returnTo = '/'): Promise<string | null> {
    const destination = await authenticatedReturnTo(returnTo);
    if (destination) return destination;
    beginLogin(returnTo);
    return null;
  }

  function consumeReturnTo(): string | null {
    if (typeof window === 'undefined') return null;
    const returnTo = window.sessionStorage.getItem('aiops.auth.returnTo');
    window.sessionStorage.removeItem('aiops.auth.returnTo');
    return returnTo ? normalizeReturnTo(returnTo) : null;
  }

  async function extendSession(): Promise<BrowserSessionInfo> {
    const extended = await requestJson<BrowserSessionInfo>('/api/auth/session/extend', { method: 'POST' });
    session.value = { ...session.value, session: extended };
    return extended;
  }

  function logout(): void {
    if (typeof document === 'undefined') return;
    const form = document.createElement('form');
    form.method = 'post';
    form.action = normalizeSameOriginPath(session.value.logoutUrl) ?? ANONYMOUS.logoutUrl;
    const csrfToken = readCookie('XSRF-TOKEN');
    if (csrfToken) {
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = '_csrf';
      input.value = csrfToken;
      form.appendChild(input);
    }
    document.body.appendChild(form);
    form.submit();
  }

  return {
    session,
    loaded,
    loading,
    authenticated,
    user,
    load,
    hasCapability,
    beginLogin,
    authenticatedReturnTo,
    continueLogin,
    consumeReturnTo,
    extendSession,
    logout,
  };
});

export function classifyLoginFailure(error?: unknown, reason?: unknown): LoginFailure {
  if (reason === 'identity-provider-unavailable' || reason === 'oidc-login-failed') {
    return 'identity-provider';
  }
  if (error instanceof ApiError && typeof error.body === 'object' && error.body !== null) {
    const code = error.body.code ?? '';
    if (['IDENTITY_PROVIDER_UNAVAILABLE', 'OIDC_PROVIDER_UNAVAILABLE', 'OIDC_DISCOVERY_FAILED'].includes(code)) {
      return 'identity-provider';
    }
  }
  return error || reason ? 'session' : null;
}

export function sessionExpiredRedirect(currentPath: string, publicRoute: boolean): {
  name: 'login';
  query: { returnTo: string; reason: 'session-expired' };
} | null {
  if (publicRoute) return null;
  return {
    name: 'login',
    query: {
      returnTo: normalizeReturnTo(currentPath) ?? '/',
      reason: 'session-expired',
    },
  };
}

function normalizeReturnTo(returnTo: string): string | null {
  return returnTo.startsWith('/') && !returnTo.startsWith('//') ? returnTo : null;
}

function normalizeSameOriginPath(path: string): string | null {
  return path.startsWith('/') && !path.startsWith('//') ? path : null;
}
