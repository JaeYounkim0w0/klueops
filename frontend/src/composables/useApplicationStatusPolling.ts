import { onBeforeUnmount } from 'vue';

export const APPLICATION_PROGRESS_STATUSES = [
  'DEPLOY_REQUESTED',
  'DEPLOYING',
  'UPGRADING',
  'ROLLING_BACK',
  'UNINSTALLING',
] as const;

const APPLICATION_PROGRESS_STATUS_SET = new Set<string>(APPLICATION_PROGRESS_STATUSES);

/** isApplicationProgressing 처리 조건의 충족 여부를 판단한다. */
export function isApplicationProgressing(status?: string): boolean {
  return APPLICATION_PROGRESS_STATUS_SET.has(status || '');
}

/**
 * 진행 중인 Application이 있을 때만 상태를 다시 조회한다.
 * 백그라운드 폴링을 직렬화해 느린 응답이 겹치거나 완료 후 불필요한 요청이 남지 않게 한다.
 */
export function createApplicationStatusPoller(
  refresh: () => Promise<void>,
  shouldContinue: () => boolean,
  intervalMs = 2_000,
) {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let stopped = false;
  let refreshing = false;

  /** stop 처리 대상과 관련 상태를 안전하게 정리한다. */
  function stop(): void {
    stopped = true;
    if (timer) clearTimeout(timer);
  }

  /** schedule 처리에 필요한 화면 또는 업무 로직을 수행한다. */
  function schedule(): void {
    if (timer) clearTimeout(timer);
    if (stopped || !shouldContinue()) return;
    timer = setTimeout(() => void run(), intervalMs);
  }

  /** run 처리의 핵심 작업 흐름을 실행한다. */
  async function run(): Promise<void> {
    if (stopped || refreshing) return;
    refreshing = true;
    try {
      await refresh();
    } finally {
      refreshing = false;
      schedule();
    }
  }

  return { schedule, stop };
}

/** useApplicationStatusPolling 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function useApplicationStatusPolling(
  refresh: () => Promise<void>,
  shouldContinue: () => boolean,
  intervalMs = 2_000,
) {
  const poller = createApplicationStatusPoller(refresh, shouldContinue, intervalMs);

  onBeforeUnmount(poller.stop);

  return poller;
}
