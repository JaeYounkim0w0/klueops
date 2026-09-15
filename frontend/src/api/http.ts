import { getLocale } from '@/i18n/locale';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export interface ApiErrorBody {
  title?: string;
  detail?: string;
  status?: number;
  code?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly body: ApiErrorBody | string | null;

  constructor(status: number, body: ApiErrorBody | string | null) {
    const message = typeof body === 'object' && body !== null
      ? body.detail ?? body.title ?? `Request failed with status ${status}`
      : body ?? `Request failed with status ${status}`;
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

export interface RequestOptions {
  timeoutMs?: number;
}

const DEFAULT_TIMEOUT_MS = 30_000;
const unauthorizedListeners = new Set<() => void>();

export function onUnauthorized(listener: () => void): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

export async function requestJson<T>(path: string, init?: RequestInit, options: RequestOptions = {}): Promise<T> {
  const controller = new AbortController();
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const headers = new Headers(init?.headers);
  // FormData는 브라우저가 multipart boundary를 포함한 Content-Type을 생성해야 한다.
  if (!(init?.body instanceof FormData)) {
    headers.set('Content-Type', headers.get('Content-Type') ?? 'application/json');
  }
  headers.set('X-Request-Id', headers.get('X-Request-Id') ?? createRequestId());
  headers.set('Accept-Language', headers.get('Accept-Language') ?? getLocale());
  if (isMutation(init?.method)) {
    const csrfToken = readCookie('XSRF-TOKEN');
    if (csrfToken) headers.set('X-XSRF-TOKEN', csrfToken);
  }

  const abortFromCaller = () => controller.abort(init?.signal?.reason);
  if (init?.signal?.aborted) {
    abortFromCaller();
  } else {
    init?.signal?.addEventListener('abort', abortFromCaller, { once: true });
  }
  const timeoutId = globalThis.setTimeout(() => controller.abort('CLIENT_TIMEOUT'), timeoutMs);

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      credentials: init?.credentials ?? 'same-origin',
      headers,
      signal: controller.signal,
    });
    if (!response.ok) {
      if (response.status === 401 && path !== '/api/auth/me') {
        unauthorizedListeners.forEach((listener) => listener());
      }
      throw new ApiError(response.status, await parseError(response));
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return response.json() as Promise<T>;
  } catch (error) {
    if (controller.signal.aborted && !init?.signal?.aborted) {
      throw new ApiError(408, {
        status: 408,
        code: 'CLIENT_TIMEOUT',
        title: getLocale() === 'ko-KR' ? '요청 시간 초과' : 'Request timed out',
        detail: getLocale() === 'ko-KR'
          ? `${Math.ceil(timeoutMs / 1000)}초 안에 서버 응답을 받지 못했습니다.`
          : `The server did not respond within ${Math.ceil(timeoutMs / 1000)} seconds.`
      });
    }
    throw error;
  } finally {
    globalThis.clearTimeout(timeoutId);
    init?.signal?.removeEventListener('abort', abortFromCaller);
  }
}

function isMutation(method: string | undefined): boolean {
  return !['GET', 'HEAD', 'OPTIONS'].includes((method ?? 'GET').toUpperCase());
}

export function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const prefix = `${encodeURIComponent(name)}=`;
  const cookie = document.cookie.split(';').map((value) => value.trim()).find((value) => value.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

export async function parseError(response: Response): Promise<ApiErrorBody | string | null> {
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    return response.json() as Promise<ApiErrorBody>;
  }
  const text = await response.text();
  return text || null;
}

function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
