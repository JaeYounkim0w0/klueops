import { describe, expect, it } from 'vitest';
import { formatElapsedDuration } from '@/utils/time';

describe('formatElapsedDuration', () => {
  it('formats running jobs using the current time', () => {
    expect(formatElapsedDuration(
      '2026-07-09T00:00:00.000Z',
      undefined,
      new Date('2026-07-09T00:00:12.000Z')
    )).toBe('12초');
  });

  it('formats completed jobs with minute precision', () => {
    expect(formatElapsedDuration(
      '2026-07-09T00:00:00.000Z',
      '2026-07-09T00:02:05.000Z'
    )).toBe('2분 05초');
  });
});
