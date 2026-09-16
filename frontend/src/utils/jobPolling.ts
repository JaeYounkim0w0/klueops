import { ApiError } from '@/api/http';

/**
 * Job 자체 실패와 상태 조회의 일시 장애를 분리한다.
 * 읽기 요청의 timeout/서버 오류/브라우저 네트워크 오류만 자동 재시도한다.
 */
export function isRetryableJobPollError(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.status === 408 || error.status >= 500;
  }
  return error instanceof TypeError;
}
