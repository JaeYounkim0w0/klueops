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
      /** length 처리에 필요한 화면 또는 업무 로직을 수행한다. */
      get length() {
        return values.size;
      },
      clear: /** clear 처리 대상과 관련 상태를 안전하게 정리한다. */ () => values.clear(),
      getItem: /** getItem 처리 결과를 조회해 반환한다. */ (key: string) => values.get(key) ?? null,
      key: /** key 처리에 필요한 화면 또는 업무 로직을 수행한다. */ (index: number) => [...values.keys()][index] ?? null,
      removeItem: /** removeItem 처리 대상과 관련 상태를 안전하게 정리한다. */ (key: string) => {
        values.delete(key);
      },
      setItem: /** setItem 처리 대상의 상태를 갱신한다. */ (key: string, value: string) => {
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
