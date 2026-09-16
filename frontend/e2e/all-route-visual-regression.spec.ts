import { expect, test } from '@playwright/test';

const routes = [
  '/', '/clusters', '/applications', '/applications/discover', '/applications/library', '/applications/sources',
  '/incidents', '/triage', '/operations/fleet', '/policies', '/runbooks', '/audit', '/analysis', '/ai/trust', '/ai-chat',
  '/settings/operations', '/settings/reliability', '/settings/preferences', '/settings/access',
  '/settings/users-access', '/settings/ai-providers', '/settings/tenancy',
] as const;

test.beforeEach(async ({ page }) => {
  // 전 경로 visual gate에서는 메뉴/route 권한에 가려지지 않도록 Platform Manager 계약을 사용한다.
  await page.route(/^https?:\/\/[^/]+\/api\//, (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/auth/me') return route.fulfill({ json: {
    authenticated: true, localDevelopment: false,
    user: { id: 'visual-admin', username: 'visual-admin', displayName: 'Visual Admin', active: true },
    capabilities: ['platform:admin', 'cluster:read', 'cluster:manage', 'analysis:read', 'analysis:run', 'operation:execute',
      'application:read', 'application:deploy', 'application:exposure', 'application:delete', 'application:rollback',
      'chart:read', 'chart:manage', 'chart:import', 'values:edit', 'audit:read', 'identity:manage',
      'tenant:member:manage', 'ai-provider:manage', 'ai-model:manage', 'ai-routing:manage'],
    scopes: [{ type: 'PLATFORM', role: 'PLATFORM_MANAGER' }], loginUrl: '/login', logoutUrl: '/logout',
    session: { expiresAt: '2099-01-01T00:00:00Z', absoluteExpiresAt: '2099-01-01T08:00:00Z', idleTimeoutSeconds: 1800, canExtend: true },
    } });
    if (path === '/api/tenants') return route.fulfill({ json: [{ id: 'tenant-visual', name: 'Visual Tenant', status: 'ACTIVE' }] });
    if (path === '/api/tenants/tenant-visual/workspaces') return route.fulfill({ json: [{ id: 'workspace-visual', tenantId: 'tenant-visual', name: 'Visual Workspace', status: 'ACTIVE' }] });
    if (path === '/api/me/access') return route.fulfill({ json: { platformRole: 'PLATFORM_MANAGER', tenantId: 'tenant-visual', workspaceId: 'workspace-visual', effectiveCapabilities: ['platform:admin', 'cluster:read', 'cluster:manage', 'analysis:read', 'analysis:run', 'operation:execute', 'application:read', 'application:deploy', 'application:exposure', 'application:delete', 'application:rollback', 'chart:read', 'chart:manage', 'chart:import', 'values:edit', 'audit:read', 'identity:manage', 'tenant:member:manage', 'ai-provider:manage', 'ai-model:manage', 'ai-routing:manage'], enabledFeatures: ['APPLICATION_DELIVERY', 'AI_PROVIDER_ROUTING'], navigation: { overview: true, clusters: true, applications: true, ai: true, aiProviders: true, access: true } } });
    if (path === '/api/operations/runtime-readiness') return route.fulfill({ json: { status: 'READY', mode: 'visual-test', checkedAt: '2026-09-16T00:00:00Z', checks: [] } });
    if (path === '/api/operations/telemetry') return route.fulfill({ json: {
      schemaVersion: 'operations-telemetry.v1', generatedAt: '2026-09-16T00:00:00Z',
      retention: 'INSTANCE_SNAPSHOT', requestCount: 0, failureCount: 0, series: [],
    } });
    if (path === '/api/notifications/unread-count') return route.fulfill({ json: { count: 0 } });
    if (path === '/api/operations/overview') return route.fulfill({ json: {
      generatedAt: '2026-09-16T00:00:00Z', clusters: 0, openIncidents: 0, criticalIncidents: 0,
      unreadNotifications: 0, failedPolicyEvaluations: 0, runningJobs: 0, failedJobs: 0,
      averageJobDurationMs: 0, successfulAnalyses: 0, failedAnalyses: 0, priorityQueue: [], clusterHealth: [],
      capacityPosture: { dataSource: 'visual-test', workloads: 0, unavailableWorkloads: 0, pods: 0,
        unhealthyPods: 0, pendingPvcs: 0, namespacesWithoutNetworkPolicy: 0, governanceEvidenceGaps: 0,
        summary: 'No active workload evidence.' },
      aiQuality: { feedbackCount: 0, correctCount: 0, partialCount: 0, incorrectCount: 0,
        resolvedOrImprovedCount: 0, dangerousSuggestionCount: 0, successfulAnalyses: 0, failedAnalyses: 0,
        successRate: 0, verifiedAccuracyRate: 0 },
    } });
    if (path === '/api/operations/triage') return route.fulfill({ json: {
      generatedAt: '2026-09-16T00:00:00Z', openItems: 0, highPriority: 0, promotedSignals: 0,
      suppressedSignals: 0, items: [],
    } });
    if (path === '/api/operations/scorecard') return route.fulfill({ json: {
      generatedAt: '2026-09-16T00:00:00Z', totalIncidents: 0, openIncidents: 0, resolvedIncidents: 0,
      meanTimeToAcknowledgeMinutes: 0, meanTimeToResolveMinutes: 0, recurrenceRate: 0,
      mitigationSuccessRate: 0, analysisSuccessRate: 0, fallbackRate: 0, openSignalGroups: 0,
      promotedSignalGroups: 0, suppressedSignalGroups: 0,
      weeklyTrend: { currentIncidents: 0, previousIncidents: 0, currentResolved: 0, previousResolved: 0,
        currentAnalyses: 0, previousAnalyses: 0, direction: 'STABLE' }, hotspots: [],
    } });
    if (path === '/api/operations/ai-calibration') return route.fulfill({ json: {
      feedbackCount: 0, groundTruthCount: 0, groundTruthCoverageRate: 0, verifiedAccuracyRate: 0,
      resolutionRate: 0, dangerousSuggestionCount: 0, profiles: [], recentGroundTruth: [],
    } });
    if (path === '/api/operations/fleet-queue') return route.fulfill({ json: {
      generatedAt: '2026-09-16T00:00:00Z', totalItems: 0, immediateItems: 0, degradedCollectors: 0, items: [],
    } });
    if (path === '/api/operations/shift-briefing') return route.fulfill({ json: {
      generatedAt: '2026-09-16T00:00:00Z', posture: 'STABLE', beginnerSummary: 'No active incident.',
      expertSummary: 'No active incident.', openIncidents: 0, criticalIncidents: 0, runningJobs: 0,
      failedJobs: 0, degradedCollectors: 0, immediateActions: [], watchItems: [],
    } });
    if (path === '/api/operations/validation-lab/live/policy') return route.fulfill({ json: {
      enabled: false, safetyMode: 'LIVE_GUARDED', namespacePrefix: 'visual-', maximumTtlSeconds: 300,
      requiredConfirmation: 'RUN VISUAL', safeguards: [],
    } });
    if (path === '/api/operations/validation-lab/benchmarks/latest') return route.fulfill({ json: null });
    if (path === '/api/operations/reliability-trend') return route.fulfill({ json: {
      generatedAt: '2026-09-16T00:00:00Z', windowDays: 14, evidenceType: 'EVENT_BASED', incidentsDetected: 0,
      incidentsResolved: 0, recurringIncidents: 0, recurrenceRate: 0, meanTimeToAcknowledgeMinutes: 0,
      meanTimeToResolveMinutes: 0, remediationSuccessRate: 0, collectorCoverageRate: 100, daily: [], scopes: [],
    } });
    if (path === '/api/operations/ai-trust') return route.fulfill({ json: {
      schemaVersion: 'ai-trust.v1', generatedAt: '2026-09-16T00:00:00Z', state: 'NEEDS_EVIDENCE',
      quality: { feedbackCount: 0, correctCount: 0, partialCount: 0, incorrectCount: 0,
        resolvedOrImprovedCount: 0, dangerousSuggestionCount: 0, successfulAnalyses: 0, failedAnalyses: 0,
        successRate: 0, verifiedAccuracyRate: 0 },
      calibration: { feedbackCount: 0, groundTruthCount: 0, groundTruthCoverageRate: 0,
        verifiedAccuracyRate: 0, resolutionRate: 0, dangerousSuggestionCount: 0, profiles: [], recentGroundTruth: [] },
      corpus: { version: 'visual-v1', distinctCases: 0, categories: [], abstentionCases: 0,
        generatedVariantsRemoved: true },
      contractEvaluation: { state: 'INSUFFICIENT_EVIDENCE', sampleCount: 0, macroF1: 0,
        abstentionAccuracy: 0, categories: [] }, recentRegressions: [], recentReleaseGates: [],
    } });
    if (path === '/api/v2/ai-configuration/providers') return route.fulfill({ json: [{
      id: 'provider-visual', name: 'Visual Ollama', providerType: 'OLLAMA', baseUrl: 'http://ollama.visual:11434',
      credentialConfigured: false, defaultModel: 'visual:7b', allowedModels: ['visual:7b'], enabled: true,
      externalDataTransfer: false, validationStatus: 'VALID', lastValidatedAt: '2026-09-16T00:00:00Z',
    }] });
    if (path === '/api/v2/ai-configuration/routing') return route.fulfill({ json: ['ANALYSIS', 'CHAT', 'HELM_VALUES'].map((purpose) => ({
      purpose, primaryProfileId: 'provider-visual', model: 'visual:7b', externalTransferAllowed: false,
      maximumContextChars: 16000, maximumOutputTokens: 2048, updatedAt: '2026-09-16T00:00:00Z',
    })) });
    if (/^\/api\/v2\/ai-configuration\/providers\/[^/]+\/models$/.test(path)) return route.fulfill({ json: [] });
    return route.fulfill({ json: [] });
  });
});

for (const path of routes) {
  test(`visual contract ${path}`, async ({ page }, testInfo) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    // 화면별 최상위 CSS class가 서로 달라도 실제 route outlet이 렌더링됐는지 공통 landmark로 확인한다.
    await expect(page.locator('main.content')).toBeVisible();
    await expect(page).toHaveScreenshot(`${testInfo.project.name}-${path === '/' ? 'dashboard' : path.slice(1).replaceAll('/', '-')}.png`, {
      fullPage: true, animations: 'disabled', caret: 'hide', maxDiffPixelRatio: 0.01,
      mask: [page.locator('time'), page.locator('[data-dynamic-time]')],
    });
    expect(pageErrors, `화면 렌더링 중 예외가 없어야 합니다: ${path}`).toEqual([]);
  });
}

test('modal focus remains trapped and returns to the trigger', async ({ page }) => {
  await page.goto('/clusters');
  // 모바일에서는 sidebar가 접히므로 항상 viewport 안에 있는 topbar 검색 버튼을 사용한다.
  const trigger = page.locator('.workspace-topbar').getByRole('button', { name: /Search operations|운영 통합 검색/ });
  await trigger.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  for (let index = 0; index < 8; index += 1) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => document.activeElement?.closest('[role="dialog"]') !== null)).toBe(true);
  }
  await dialog.getByRole('button', { name: /닫기|Close/ }).click();
  await expect(trigger).toBeFocused();
});
