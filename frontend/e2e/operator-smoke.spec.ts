import { expect, test, type Page } from '@playwright/test';

const cluster = {
  id: 'cluster-qa-2',
  name: 'qa-2',
  environment: 'DEV',
  provider: 'ON_PREM',
  status: 'REGISTERED'
};

async function mockOperatorApis(page: Page): Promise<void> {
  let conversations = [{
    id: 'chat-1', title: 'Deployment review', mode: 'GENERAL', favorite: false,
    createdAt: '2026-09-03T00:00:00Z', updatedAt: '2026-09-03T00:00:00Z'
  }];
  await page.route(/^https?:\/\/[^/]+\/api\//, async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();

    if (path === '/api/auth/me') {
      return route.fulfill({ json: {
        authenticated: true,
        localDevelopment: false,
        user: {
          id: 'operator-smoke',
          username: 'operator-smoke',
          displayName: 'Operator Smoke',
          email: 'operator-smoke@example.invalid',
          active: true
        },
        capabilities: ['cluster:read', 'analysis:read', 'analysis:run', 'operation:execute'],
        scopes: [{ type: 'PLATFORM', role: 'OPERATOR' }],
        loginUrl: '/oauth2/authorization/aiops',
        logoutUrl: '/logout',
        session: {
          expiresAt: '2099-09-02T01:00:00Z',
          absoluteExpiresAt: '2099-09-02T08:00:00Z',
          idleTimeoutSeconds: 1800,
          canExtend: true
        }
      } });
    }
    if (path === '/api/operations/runtime-readiness') {
      return route.fulfill({ json: {
        status: 'PILOT', mode: 'single-operator-pilot', checkedAt: '2026-09-02T00:00:00Z', checks: []
      } });
    }
    if (path === '/api/notifications/unread-count') {
      return route.fulfill({ json: { count: 1 } });
    }
    if (path === '/api/notifications') {
      return route.fulfill({ json: [{
        id: 'notification-1',
        notificationType: 'SYNC_STALE',
        severity: 'MEDIUM',
        title: 'Cluster sync delayed',
        message: 'qa-2 리소스 동기화 상태를 확인하세요.',
        targetPath: '/clusters/cluster-qa-2',
        occurrenceCount: 1,
        read: false,
        createdAt: '2026-09-03T00:00:00Z',
        updatedAt: '2026-09-03T00:00:00Z'
      }] });
    }
    if (path === '/api/clusters') {
      return route.fulfill({ json: [cluster] });
    }
    if (path === '/api/applications') {
      return route.fulfill({ json: [] });
    }
    if (path === '/api/ai-chat/conversations' && method === 'GET') {
      const archived = url.searchParams.get('archived') === 'true';
      return route.fulfill({ json: conversations.filter((item) => archived === Boolean((item as { archivedAt?: string }).archivedAt)) });
    }
    if (path === '/api/ai-chat/conversations' && method === 'POST') {
      const body = route.request().postDataJSON() as { title: string; mode: 'GENERAL' | 'CLUSTER'; clusterId?: string; namespace?: string };
      const created = { id: `chat-${conversations.length + 1}`, ...body, favorite: false,
        createdAt: '2026-09-03T00:00:00Z', updatedAt: '2026-09-03T00:00:00Z' };
      conversations = [created, ...conversations];
      return route.fulfill({ status: 201, json: created });
    }
    const chatMatch = path.match(/^\/api\/ai-chat\/conversations\/([^/]+)$/);
    if (chatMatch && method === 'PATCH') {
      const body = route.request().postDataJSON() as { title?: string; favorite?: boolean; archived?: boolean };
      const current = conversations.find((item) => item.id === chatMatch[1])!;
      const updated = { ...current, ...body, archivedAt: body.archived === undefined
        ? (current as { archivedAt?: string }).archivedAt : body.archived ? '2026-09-03T00:10:00Z' : undefined };
      conversations = conversations.map((item) => item.id === updated.id ? updated : item);
      return route.fulfill({ json: updated });
    }
    if (chatMatch && method === 'DELETE') {
      conversations = conversations.filter((item) => item.id !== chatMatch[1]);
      return route.fulfill({ status: 204 });
    }
    if (/^\/api\/ai-chat\/conversations\/[^/]+\/messages$/.test(path)) {
      return route.fulfill({ json: [] });
    }
    if (/^\/api\/ai-chat\/conversations\/[^/]+\/context-references$/.test(path)) {
      return route.fulfill({ json: [] });
    }
    if (path === `/api/clusters/${cluster.id}/namespaces`) {
      return route.fulfill({ json: [{ name: 'default', phase: 'Active' }, { name: 'nginx', phase: 'Active' }] });
    }
    if (path === '/api/analysis/history') {
      return route.fulfill({ json: [] });
    }
    if (path.includes('/diagnostics')) {
      return route.fulfill({ json: {
        clusterId: cluster.id,
        namespace: url.pathname.includes('/nginx/') ? 'nginx' : 'default',
        collectedAt: '2026-09-02T00:00:00Z',
        resourceCount: 3,
        eventCount: 0,
        warningEventCount: 0,
        podLogCount: 0,
        changeTimeline: [],
        runbookActions: [],
        resourceKinds: [{ resourceType: 'Pod', count: 1 }],
        problemResources: [],
        warningEvents: [],
        evidenceSignals: [],
        podLogSources: []
      } });
    }
    if (path === '/api/incidents') {
      return route.fulfill({ json: [{
        id: 'incident-1',
        fingerprint: 'qa-2/default/failed-mount',
        clusterId: cluster.id,
        clusterName: cluster.name,
        namespace: 'default',
        resourceKind: 'Pod',
        resourceName: 'configmap-db-pod',
        category: 'STORAGE_CONFIG',
        severity: 'HIGH',
        state: 'OPEN',
        title: 'ConfigMap volume mount failed',
        summary: 'Pod가 참조하는 ConfigMap을 찾지 못했습니다.',
        occurrenceCount: 7,
        reopenCount: 0,
        firstDetectedAt: '2026-09-03T00:00:00Z',
        lastDetectedAt: '2026-09-03T00:10:00Z'
      }] });
    }
    if (path === '/api/runbooks/library') {
      return route.fulfill({ json: [{
        id: 'probe-pod-v1',
        sourceType: 'SYSTEM',
        signal: 'PROBE',
        category: 'PROBE',
        resourceKind: 'Pod',
        title: 'Probe failure diagnosis',
        beginnerExplanation: 'health probe의 port와 path를 순서대로 확인합니다.',
        verificationCommand: 'kubectl describe pod sample -n default',
        expectedResult: 'Probe event and endpoint are visible.',
        safeAction: 'Probe 설정을 워크로드 선언과 비교합니다.',
        validationCommand: 'kubectl get pod sample -n default',
        rollbackGuidance: '',
        safetyLevel: 'READ_ONLY',
        version: 1,
        enabled: true,
        owner: 'SYSTEM'
      }] });
    }
    return route.fulfill({ json: [] });
  });
}

test.beforeEach(async ({ page }) => {
  await mockOperatorApis(page);
});

test('operator can open the application shell and cluster workspace', async ({ page }) => {
  await page.goto('/clusters');

  await expect(page.getByText('KlueOps')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Clusters' })).toBeVisible();
  await expect(page.getByText('qa-2', { exact: true })).toBeVisible();
});

test('operations notification titles remain readable on the light popover', async ({ page }) => {
  await page.goto('/clusters');
  await expect(page.getByRole('heading', { name: 'Clusters' })).toBeVisible();

  const notificationTrigger = page.getByRole('button', { name: /운영 알림|Operations notifications/ });
  if (!(await notificationTrigger.isVisible())) {
    await page.getByRole('button', { name: /메뉴 열기|Open menu/ }).click();
  }
  await notificationTrigger.click();
  const title = page.getByText('Cluster sync delayed', { exact: true });
  await expect(title).toBeVisible();
  await expect(title).toHaveCSS('color', 'rgb(24, 34, 48)');
});

test('incident titles remain readable on light list rows', async ({ page }) => {
  await page.goto('/incidents');

  const title = page.getByText('ConfigMap volume mount failed', { exact: true });
  await expect(title).toBeVisible();
  await expect(title).toHaveCSS('color', 'rgb(24, 34, 48)');
});

test('core navigation and commands are keyboard reachable', async ({ page }) => {
  await page.goto('/clusters');
  await page.keyboard.press('Tab');
  const firstFocus = await page.evaluate(() => document.activeElement?.tagName);
  expect(['A', 'BUTTON']).toContain(firstFocus);

  const register = page.getByRole('button', { name: /클러스터 등록|Register cluster/ });
  await register.focus();
  await expect(register).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();
});

test('analysis scope follows the selected cluster and namespace', async ({ page }) => {
  await page.goto('/analysis');

  const fields = page.locator('.analysis-run-fields select');
  await expect(fields.nth(1)).toHaveValue(cluster.id);
  await fields.nth(2).selectOption('nginx');

  await expect(fields.nth(2)).toHaveValue('nginx');
  await expect(page.getByRole('button', { name: 'Namespace AI 분석 실행' })).toBeEnabled();
  await expect(page.getByText('nginx namespace')).toBeVisible();
});

test('runbook titles remain readable and details expand', async ({ page }) => {
  await page.goto('/runbooks');

  const title = page.locator('.runbook-library-title');
  await expect(title).toHaveText('Probe failure diagnosis');
  await expect(title).not.toHaveCSS('color', 'rgb(255, 255, 255)');

  await page.getByRole('button', { name: /Probe failure diagnosis/ }).click();
  await expect(page.getByText('원인 확인')).toBeVisible();
  await expect(page.getByText('조치 후 검증')).toBeVisible();
});

test('AI chat conversations can be favorited, renamed, archived and permanently deleted', async ({ page }) => {
  await page.goto('/ai-chat');

  await expect(page.getByRole('heading', { name: 'Deployment review' })).toBeVisible();
  const toolbar = page.locator('.chat-conversation-actions');
  await toolbar.getByTitle(/Add to favorites|즐겨찾기 추가/).click();
  await expect(toolbar.getByTitle(/Remove from favorites|즐겨찾기 해제/)).toBeVisible();

  await toolbar.getByTitle(/Rename|이름 변경/).click();
  await page.getByLabel(/Conversation title|대화 제목/).fill('Production rollout review');
  await page.getByRole('button', { name: /Save|저장/ }).click();
  await expect(page.getByRole('heading', { name: 'Production rollout review' })).toBeVisible();

  await toolbar.getByTitle(/Archive|보관/).click();
  await page.getByRole('tab', { name: /Archive|보관함/ }).click();
  await expect(page.getByRole('heading', { name: 'Production rollout review' })).toBeVisible();

  await page.locator('.chat-conversation-actions').getByTitle(/Delete permanently|영구 삭제/).click();
  await page.getByRole('dialog').getByRole('button', { name: /Delete permanently|영구 삭제/ }).click();
  await expect(page.getByText(/No archived conversations|보관한 대화가 없습니다/)).toBeVisible();
});
