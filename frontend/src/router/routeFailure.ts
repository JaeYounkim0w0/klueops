import { ref } from 'vue';

export interface RouteFailure {
  message: string;
  technicalDetail: string;
  occurredAt: string;
}

export const routeFailure = ref<RouteFailure | null>(null);

/** reportRouteFailure 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function reportRouteFailure(error: unknown): void {
  const detail = error instanceof Error ? error.message : String(error);
  const lazyModuleFailure = /dynamically imported module|loading chunk|importing a module script/i.test(detail);
  routeFailure.value = {
    message: lazyModuleFailure
      ? '화면 파일을 불러오지 못했습니다. 새로고침하면 최신 화면으로 복구할 수 있습니다.'
      : '화면 이동 중 오류가 발생했습니다.',
    technicalDetail: detail,
    occurredAt: new Date().toISOString()
  };
}

/** clearRouteFailure 처리 대상과 관련 상태를 안전하게 정리한다. */
export function clearRouteFailure(): void {
  routeFailure.value = null;
}
