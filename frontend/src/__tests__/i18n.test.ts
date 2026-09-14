import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  DEFAULT_LOCALE,
  LOCALE_STORAGE_KEY,
  getLocale,
  normalizeLocale,
  resolveInitialLocale,
  setLocale,
} from '@/i18n/locale';

describe('locale preference', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('normalizes only the two supported locale families', () => {
    expect(normalizeLocale('ko')).toBe('ko-KR');
    expect(normalizeLocale('ko-KR')).toBe('ko-KR');
    expect(normalizeLocale('en-GB')).toBe('en-US');
    expect(normalizeLocale('fr-FR')).toBeNull();
  });

  it('prefers an explicit saved locale over the browser language', () => {
    expect(resolveInitialLocale('en-US', 'ko-KR')).toBe('en-US');
    expect(resolveInitialLocale(null, 'ko-KR')).toBe('ko-KR');
    expect(resolveInitialLocale(null, 'fr-FR')).toBe(DEFAULT_LOCALE);
  });

  it('persists a locale and updates the document language', () => {
    const values = new Map<string, string>();
    const storage: Storage = {
      get length() {
        return values.size;
      },
      clear: () => values.clear(),
      getItem: (key: string) => values.get(key) ?? null,
      key: (index: number) => [...values.keys()][index] ?? null,
      removeItem: (key: string) => {
        values.delete(key);
      },
      setItem: (key: string, value: string) => {
        values.set(key, value);
      },
    };
    const documentElement = { lang: '' } as HTMLElement;

    setLocale('ko-KR', storage, documentElement);

    expect(values.get(LOCALE_STORAGE_KEY)).toBe('ko-KR');
    expect(documentElement.lang).toBe('ko-KR');
    expect(getLocale()).toBe('ko-KR');
  });
});
