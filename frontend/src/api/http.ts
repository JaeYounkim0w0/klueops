import { getLocale } from '@/i18n/locale';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export interface ApiErrorBody {
  title?: string;
  detail?: string;
  status?: number;
  code?: string;
  errors?: string[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly body: ApiErrorBody | string | null;

  /** 컴포넌트 또는 서비스 인스턴스를 필요한 초기 상태로 구성한다. */
  constructor(status: number, body: ApiErrorBody | string | null) {
    const fieldDetails = typeof body === 'object' && body !== null && body.errors?.length
      ? body.errors.join(', ')
      : undefined;
    const message = typeof body === 'object' && body !== null
      ? fieldDetails
        ? `${body.detail ?? body.title ?? `Request failed with status ${status}`} (${fieldDetails})`
        : body.detail ?? body.title ?? `Request failed with status ${status}`
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

/** onUnauthorized 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
export function onUnauthorized(listener: () => void): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

/** requestJson 처리에 필요한 화면 또는 업무 로직을 수행한다. */
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

  const abortFromCaller = /** abortFromCaller 처리에 필요한 화면 또는 업무 로직을 수행한다. */ () => controller.abort(init?.signal?.reason);
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

/** isMutation 처리 조건의 충족 여부를 판단한다. */
function isMutation(method: string | undefined): boolean {
  return !['GET', 'HEAD', 'OPTIONS'].includes((method ?? 'GET').toUpperCase());
}

/** readCookie 처리 결과를 조회해 반환한다. */
export function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const prefix = `${encodeURIComponent(name)}=`;
  const cookie = document.cookie.split(';').map((value) => value.trim()).find((value) => value.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

/** parseError 처리 데이터를 화면 또는 API 표현으로 변환한다. */
export async function parseError(response: Response): Promise<ApiErrorBody | string | null> {
  const contentType = response.headers.get('content-type') ?? '';
  // RFC 9457 Problem Details(application/problem+json)도 구조화된 오류로 해석한다.
  if (contentType.toLowerCase().includes('json')) {
    return response.json() as Promise<ApiErrorBody>;
  }
  const text = await response.text();
  return text || null;
}

/** createRequestId 처리에 필요한 데이터를 생성하거나 저장한다. */
function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
