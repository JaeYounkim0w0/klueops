import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/http';
import { classifyLoginFailure, sessionExpiredRedirect, useAuthStore } from '@/stores/auth';

describe('auth session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.unstubAllGlobals();
  });

  it('loads the BFF session and evaluates capabilities', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      authenticated: true,
      localDevelopment: false,
      user: { id: 'u1', username: 'operator', displayName: 'Operator', active: true },
      capabilities: ['cluster:read', 'analysis:run'],
      scopes: [],
      loginUrl: '/oauth2/authorization/aiops',
      logoutUrl: '/logout',
      session: {
        expiresAt: '2026-09-02T06:30:00Z',
        absoluteExpiresAt: '2026-09-02T14:00:00Z',
        idleTimeoutSeconds: 1800,
        canExtend: true,
      },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })));

    const auth = useAuthStore();
    await auth.load();

    expect(auth.authenticated).toBe(true);
    expect(auth.hasCapability('analysis:run')).toBe(true);
    expect(auth.hasCapability('identity:manage')).toBe(false);
    expect(auth.session.session?.idleTimeoutSeconds).toBe(1800);
  });

  it('extends an authenticated browser session', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      expiresAt: '2026-09-02T06:45:00Z',
      absoluteExpiresAt: '2026-09-02T14:00:00Z',
      idleTimeoutSeconds: 1800,
      canExtend: true,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=csrf-value' });

    const auth = useAuthStore();
    await auth.extendSession();

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/session/extend', expect.objectContaining({ method: 'POST' }));
    expect(auth.session.session?.expiresAt).toBe('2026-09-02T06:45:00Z');
  });

  it('submits logout as a browser navigation so the identity provider session is closed', () => {
    const submit = vi.fn();
    const appendChild = vi.fn();
    const createElement = vi.fn((tag: string) => tag === 'form'
      ? { method: '', action: '', appendChild: vi.fn(), submit }
      : { name: '', value: '', type: '' });
    vi.stubGlobal('document', {
      cookie: 'XSRF-TOKEN=csrf-value',
      createElement,
      body: { appendChild },
    });

    const auth = useAuthStore();
    auth.logout();

    expect(createElement).toHaveBeenCalledWith('form');
    expect(appendChild).toHaveBeenCalledOnce();
    expect(submit).toHaveBeenCalledOnce();
  });

  it('accepts only same-origin relative post-login return paths', () => {
    const storage = new Map<string, string>();
    vi.stubGlobal('window', {
      sessionStorage: {
        getItem: (key: string) => storage.get(key) ?? null,
        setItem: (key: string, value: string) => storage.set(key, value),
        removeItem: (key: string) => storage.delete(key),
      },
      location: { assign: vi.fn() },
    });
    const auth = useAuthStore();

    storage.set('aiops.auth.returnTo', '/analysis?clusterId=one');
    expect(auth.consumeReturnTo()).toBe('/analysis?clusterId=one');
    storage.set('aiops.auth.returnTo', '//malicious.example');
    expect(auth.consumeReturnTo()).toBeNull();
  });

  it('does not redirect an already public route back into the login page', () => {
    expect(sessionExpiredRedirect('/analysis?clusterId=one', false)).toEqual({
      name: 'login',
      query: { returnTo: '/analysis?clusterId=one', reason: 'session-expired' },
    });
    expect(sessionExpiredRedirect('/login?reason=session-expired', true)).toBeNull();
    expect(sessionExpiredRedirect('/access-denied', true)).toBeNull();
  });

  it('continues an authenticated local session without opening the OIDC endpoint', async () => {
    const assign = vi.fn();
    vi.stubGlobal('window', {
      sessionStorage: {
        getItem: () => null,
        setItem: vi.fn(),
        removeItem: vi.fn(),
      },
      location: { assign },
    });
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      authenticated: true,
      localDevelopment: true,
      user: { id: 'local-development', username: 'local', displayName: 'Local Operator', active: true },
      capabilities: ['platform:admin'],
      scopes: [],
      loginUrl: '/oauth2/authorization/aiops',
      logoutUrl: '/logout',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })));

    const auth = useAuthStore();
    const destination = await auth.continueLogin('/analysis?clusterId=one');

    expect(destination).toBe('/analysis?clusterId=one');
    expect(assign).not.toHaveBeenCalled();
  });

  it('classifies identity provider failures without exposing provider details', () => {
    const providerError = new ApiError(503, {
      code: 'IDENTITY_PROVIDER_UNAVAILABLE',
      detail: 'connection refused at a private identity URL',
    });

    expect(classifyLoginFailure(providerError)).toBe('identity-provider');
    expect(classifyLoginFailure(undefined, 'identity-provider-unavailable')).toBe('identity-provider');
    expect(classifyLoginFailure(new TypeError('network failure'))).toBe('session');
    expect(classifyLoginFailure()).toBeNull();
  });
});
