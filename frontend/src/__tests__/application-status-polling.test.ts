import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  APPLICATION_PROGRESS_STATUSES,
  createApplicationStatusPoller,
  isApplicationProgressing,
} from '@/composables/useApplicationStatusPolling';

afterEach(() => vi.useRealTimers());

describe('Application status polling contract', () => {
  it.each(APPLICATION_PROGRESS_STATUSES)('treats %s as a progressing lifecycle status', (status) => {
    expect(isApplicationProgressing(status)).toBe(true);
  });

  it.each(['RUNNING', 'RUNNING_ENDPOINT_DEGRADED', 'FAILED', 'DEGRADED', 'UNKNOWN', undefined])(
    'stops lifecycle polling for %s',
    (status) => {
      expect(isApplicationProgressing(status)).toBe(false);
    },
  );

  it('refreshes a progressing application and stops after a terminal transition', async () => {
    vi.useFakeTimers();
    let progressing = true;
    const refresh = vi.fn(async () => { progressing = false; });
    const poller = createApplicationStatusPoller(refresh, () => progressing, 100);

    poller.schedule();
    await vi.advanceTimersByTimeAsync(100);
    await vi.advanceTimersByTimeAsync(500);

    expect(refresh).toHaveBeenCalledTimes(1);
    poller.stop();
  });

  it('does not poll an application that is already terminal', async () => {
    vi.useFakeTimers();
    const refresh = vi.fn(async () => undefined);
    const poller = createApplicationStatusPoller(refresh, () => false, 100);

    poller.schedule();
    await vi.advanceTimersByTimeAsync(500);

    expect(refresh).not.toHaveBeenCalled();
    poller.stop();
  });
});
