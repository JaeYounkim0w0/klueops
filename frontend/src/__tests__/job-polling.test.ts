import { describe, expect, it } from 'vitest';
import { ApiError } from '@/api/http';
import { isRetryableJobPollError } from '@/utils/jobPolling';

describe('Job polling retry policy', () => {
  it('retries client timeout and server errors', () => {
    expect(isRetryableJobPollError(new ApiError(408, 'timeout'))).toBe(true);
    expect(isRetryableJobPollError(new ApiError(503, 'unavailable'))).toBe(true);
    expect(isRetryableJobPollError(new TypeError('network failed'))).toBe(true);
  });

  it('does not retry authentication or validation failures', () => {
    expect(isRetryableJobPollError(new ApiError(401, 'unauthorized'))).toBe(false);
    expect(isRetryableJobPollError(new ApiError(422, 'invalid'))).toBe(false);
    expect(isRetryableJobPollError(new Error('unknown'))).toBe(false);
  });
});
