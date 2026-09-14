export const SUPPORTED_LOCALES = ['ko-KR', 'en-US'] as const;
export type SupportedLocale = typeof SUPPORTED_LOCALES[number];

export const DEFAULT_LOCALE: SupportedLocale = 'en-US';
export const LOCALE_STORAGE_KEY = 'k8s-aiops.locale';

let activeLocale: SupportedLocale = resolveInitialLocale(readSavedLocale(), readBrowserLocale());
const listeners = new Set<(locale: SupportedLocale) => void>();

export function normalizeLocale(value: string | null | undefined): SupportedLocale | null {
  const normalized = value?.trim().toLowerCase();
  if (!normalized) return null;
  if (normalized === 'ko' || normalized.startsWith('ko-')) return 'ko-KR';
  if (normalized === 'en' || normalized.startsWith('en-')) return 'en-US';
  return null;
}

export function resolveInitialLocale(
  savedLocale: string | null | undefined = readSavedLocale(),
  browserLocale: string | null | undefined = readBrowserLocale(),
): SupportedLocale {
  return normalizeLocale(savedLocale) ?? normalizeLocale(browserLocale) ?? DEFAULT_LOCALE;
}

export function getLocale(): SupportedLocale {
  return activeLocale;
}

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

export function subscribeLocale(listener: (locale: SupportedLocale) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function readSavedLocale(): string | null {
  try {
    return getStorage()?.getItem(LOCALE_STORAGE_KEY) ?? null;
  } catch {
    return null;
  }
}

function readBrowserLocale(): string | null {
  return typeof navigator === 'undefined' ? null : navigator.language;
}

function getStorage(): Storage | undefined {
  return typeof localStorage === 'undefined' ? undefined : localStorage;
}

function getDocumentElement(): HTMLElement | undefined {
  return typeof document === 'undefined' ? undefined : document.documentElement;
}

setLocale(activeLocale);
