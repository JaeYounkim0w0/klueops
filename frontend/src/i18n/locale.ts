export const SUPPORTED_LOCALES = ['ko-KR', 'en-US'] as const;
export type SupportedLocale = typeof SUPPORTED_LOCALES[number];

export const DEFAULT_LOCALE: SupportedLocale = 'en-US';
export const LOCALE_STORAGE_KEY = 'k8s-aiops.locale';

let activeLocale: SupportedLocale = resolveInitialLocale(readSavedLocale(), readBrowserLocale());
const listeners = new Set<(locale: SupportedLocale) => void>();

/** normalizeLocale 처리 데이터를 화면 또는 API 표현으로 변환한다. */
export function normalizeLocale(value: string | null | undefined): SupportedLocale | null {
  const normalized = value?.trim().toLowerCase();
  if (!normalized) return null;
  if (normalized === 'ko' || normalized.startsWith('ko-')) return 'ko-KR';
  if (normalized === 'en' || normalized.startsWith('en-')) return 'en-US';
  return null;
}

/** resolveInitialLocale 처리에 필요한 결과를 조합해 반환한다. */
export function resolveInitialLocale(
  savedLocale: string | null | undefined = readSavedLocale(),
  browserLocale: string | null | undefined = readBrowserLocale(),
): SupportedLocale {
  return normalizeLocale(savedLocale) ?? normalizeLocale(browserLocale) ?? DEFAULT_LOCALE;
}

/** getLocale 처리 결과를 조회해 반환한다. */
export function getLocale(): SupportedLocale {
  return activeLocale;
}

/** setLocale 처리 대상의 상태를 갱신한다. */
export function setLocale(
  locale: SupportedLocale,
  storage: Storage | undefined = getStorage(),
  documentElement: HTMLElement | undefined = getDocumentElement(),
): void {
  activeLocale = locale;
  try {
    storage?.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // The active page still changes when storage is unavailable.
  }
  if (documentElement) documentElement.lang = locale;
  listeners.forEach((listener) => listener(locale));
}

/** subscribeLocale 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function subscribeLocale(listener: (locale: SupportedLocale) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** readSavedLocale 처리 결과를 조회해 반환한다. */
function readSavedLocale(): string | null {
  try {
    return getStorage()?.getItem(LOCALE_STORAGE_KEY) ?? null;
  } catch {
    return null;
  }
}

/** readBrowserLocale 처리 결과를 조회해 반환한다. */
function readBrowserLocale(): string | null {
  return typeof navigator === 'undefined' ? null : navigator.language;
}

/** getStorage 처리 결과를 조회해 반환한다. */
function getStorage(): Storage | undefined {
  return typeof localStorage === 'undefined' ? undefined : localStorage;
}

/** getDocumentElement 처리 결과를 조회해 반환한다. */
function getDocumentElement(): HTMLElement | undefined {
  return typeof document === 'undefined' ? undefined : document.documentElement;
}

setLocale(activeLocale);
