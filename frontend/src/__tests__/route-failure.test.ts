import { describe, expect, it } from 'vitest';

import { clearRouteFailure, reportRouteFailure, routeFailure } from '@/router/routeFailure';

describe('route failure state', () => {
  it('exposes a recoverable message and can be cleared', () => {
    reportRouteFailure(new Error('Failed to fetch dynamically imported module'));

    expect(routeFailure.value?.message).toContain('화면 파일을 불러오지 못했습니다');
    expect(routeFailure.value?.technicalDetail).toContain('dynamically imported module');

    clearRouteFailure();
    expect(routeFailure.value).toBeNull();
  });
});
