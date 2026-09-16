import { afterEach, describe, expect, it, vi } from 'vitest';

import { onUnauthorized, requestJson } from '@/api/http';
import { setLocale } from '@/i18n/locale';

describe('requestJson', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('adds a request id and parses a successful JSON response', async () => {
    setLocale('en-US', undefined, undefined);
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
      const headers = new Headers(init?.headers);
      expect(headers.get('X-Request-Id')).toMatch(/.+/);
      expect(headers.get('Accept-Language')).toBe('en-US');
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(requestJson<{ ok: boolean }>('/api/test')).resolves.toEqual({ ok: true });
  });

  it('uses the BFF session and sends the CSRF cookie on mutations', async () => {
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=csrf-value' });
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
      const headers = new Headers(init?.headers);
      expect(init?.credentials).toBe('same-origin');
      expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-value');
      return new Response(null, { status: 204 });
    });
    vi.stubGlobal('fetch', fetchMock);

    await requestJson('/api/test', { method: 'POST', body: '{}' });
  });

  it('aborts a stalled request with a typed client timeout error', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', vi.fn((_url: string, init?: RequestInit) => new Promise<Response>((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
    })));

    const request = requestJson('/api/stalled', undefined, { timeoutMs: 25 });
    const assertion = expect(request).rejects.toMatchObject({
      status: 408,
      body: { code: 'CLIENT_TIMEOUT' }
    });
    await vi.advanceTimersByTimeAsync(25);

    await assertion;
  });

  it('notifies the shell when an authenticated API session expires', async () => {
    const listener = vi.fn();
    const unsubscribe = onUnauthorized(listener);
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ detail: 'expired' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    })));

    await expect(requestJson('/api/clusters')).rejects.toMatchObject({ status: 401 });
    expect(listener).toHaveBeenCalledOnce();
    unsubscribe();
  });

  it('parses RFC Problem Details instead of exposing raw JSON text', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      title: 'Internal server error', detail: 'Unexpected server error', status: 500,
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/problem+json' },
    })));

    await expect(requestJson('/api/failure')).rejects.toMatchObject({
      status: 500,
      message: 'Unexpected server error',
    });
  });

  it('includes validation field details in the user-facing API error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      title: 'Invalid request', detail: 'Request validation failed', status: 400,
      errors: ['instruction: size must be between 0 and 2000'],
    }), {
      status: 400,
      headers: { 'Content-Type': 'application/problem+json' },
    })));

    await expect(requestJson('/api/invalid')).rejects.toMatchObject({
      status: 400,
      message: 'Request validation failed (instruction: size must be between 0 and 2000)',
    });
  });
});
