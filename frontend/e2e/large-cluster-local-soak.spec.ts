import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { waitForAuthenticatedPortal } from './helpers/live-auth';

const enabled = process.env.AIOPS_LARGE_CLUSTER_SOAK === 'true';
const applicationUrl = process.env.AIOPS_LIVE_APPLICATION_URL ?? 'http://127.0.0.1:30081';
const clusterName = process.env.AIOPS_SOAK_CLUSTER_NAME ?? '';
const namespace = process.env.AIOPS_SOAK_NAMESPACE ?? '';
const podName = process.env.AIOPS_SOAK_LOG_POD ?? '';
const reportPath = process.env.AIOPS_SOAK_REPORT ?? '';
const expectedResources = Number(process.env.AIOPS_SOAK_RESOURCE_COUNT ?? '500');
const concurrencySteps = (process.env.AIOPS_SOAK_CONCURRENCY ?? '20,50,100')
  .split(',').map(Number).filter((value) => Number.isInteger(value) && value > 0);
const pageP95LimitMs = Number(process.env.AIOPS_SOAK_PAGE_P95_MS ?? '1500');
const syncP95LimitMs = Number(process.env.AIOPS_SOAK_SYNC_P95_MS ?? '180000');
const minimumSuccessRatio = Number(process.env.AIOPS_SOAK_MIN_SUCCESS_RATIO ?? '0.95');

interface Cluster { id: string; name: string; description?: string }
interface Job { id: string; status: string; errorCode?: string; errorMessage?: string }
interface StartJob { jobId: string; analysisId?: string }
interface SyncStatus { status: string; resourceCount: number; eventCount: number }
interface ResourcePage { items: unknown[]; page: number; size: number; totalElements: number; totalPages: number }
interface Analysis { id: string; status: string; resultJson: string }
interface TimedResult<T> { ok: boolean; durationMs: number; status: number; value?: T; error?: string }

async function login(page: Page): Promise<void> {
  const username = process.env.AIOPS_SOAK_USERNAME ?? '';
  const password = process.env.AIOPS_SOAK_PASSWORD ?? '';
  expect(username).not.toBe('');
  expect(password).not.toBe('');
  await page.goto(`${applicationUrl}/login`);
  await page.getByRole('button', { name: /로그인 계속하기|Continue to sign in|재시도|Retry/ }).click();
  await page.getByLabel(/Username or email|사용자 이름|Username/i).fill(username);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: /Sign In|로그인/i }).click();
  await waitForAuthenticatedPortal(page, applicationUrl);
}

async function csrf(request: APIRequestContext): Promise<string> {
  await request.get(`${applicationUrl}/api/auth/me`);
  const state = await request.storageState();
  return state.cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
}

async function timed<T>(work: () => Promise<{ status(): number; ok(): boolean; text(): Promise<string>; json(): Promise<unknown> }>): Promise<TimedResult<T>> {
  const started = performance.now();
  try {
    const response = await work();
    const durationMs = Math.round(performance.now() - started);
    if (!response.ok()) {
      return { ok: false, durationMs, status: response.status(), error: (await response.text()).slice(0, 500) };
    }
    return { ok: true, durationMs, status: response.status(), value: await response.json() as T };
  } catch (error) {
    return { ok: false, durationMs: Math.round(performance.now() - started), status: 0,
      error: error instanceof Error ? error.message : String(error) };
  }
}

async function waitForJob(request: APIRequestContext, jobId: string, timeoutMs = 360_000): Promise<Job> {
  const started = Date.now();
  let latest: Job = { id: jobId, status: 'PENDING' };
  while (Date.now() - started < timeoutMs) {
    const response = await request.get(`${applicationUrl}/api/jobs/${jobId}`);
    if (response.ok()) {
      latest = await response.json() as Job;
      if (/^(SUCCEEDED|FAILED|CANCELED|TIMEOUT)$/.test(latest.status)) return latest;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return { ...latest, status: 'TIMEOUT' };
}

function percentile(values: number[], quantile: number): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil(sorted.length * quantile) - 1)];
}

function latency(values: number[]) {
  return { samples: values.length, p50Ms: percentile(values, 0.50), p95Ms: percentile(values, 0.95), p99Ms: percentile(values, 0.99) };
}

test.describe('bounded local large-cluster soak', () => {
  test.skip(!enabled, 'Explicit large-cluster soak is disabled.');

  test('measures sync, pagination, job dedup/cancel, analysis context, and SSE disconnects', async ({ page }) => {
    test.setTimeout(1_200_000);
    expect(clusterName).not.toBe('');
    expect(namespace).toMatch(/^aiops-soak-[a-z0-9-]+$/);
    expect(reportPath).not.toBe('');
    expect(expectedResources).toBeGreaterThan(0);
    expect(concurrencySteps.length).toBeGreaterThan(0);
    await login(page);
    const token = await csrf(page.request);
    const headers = { 'X-XSRF-TOKEN': token, 'Accept-Language': 'en' };
    const credential = JSON.parse(await readFile(process.env.AIOPS_SOAK_CREDENTIAL_FILE ?? '', 'utf8')) as {
      apiServerUrl: string; caCertificate: string; token: string;
    };

    const existing = await page.request.get(`${applicationUrl}/api/clusters`);
    const existingClusters = existing.ok() ? await existing.json() as Cluster[] : [];
    for (const stale of existingClusters.filter((item) => item.name.startsWith('aiops-soak-')
      && item.description === 'Ephemeral bounded large-cluster soak fixture')) {
      await page.request.delete(`${applicationUrl}/api/clusters/${stale.id}`, { headers });
    }

    const registration = await timed<Cluster>(() => page.request.post(`${applicationUrl}/api/clusters`, {
      headers,
      data: {
        name: clusterName,
        description: 'Ephemeral bounded large-cluster soak fixture',
        environment: 'DEV', provider: 'KIND', region: 'local-soak',
        credentialType: 'SERVICE_ACCOUNT_TOKEN', serviceAccount: credential,
        namespaceAccess: { clusterWide: true, allowedNamespaces: [], defaultNamespace: namespace },
        syncSettings: { autoSyncEnabled: false, syncIntervalSeconds: 300 },
      },
      timeout: 60_000,
    }));
    if (!registration.ok || !registration.value) throw new Error(`cluster registration failed: ${registration.error}`);
    const cluster = registration.value;

    const errors: string[] = [];
    let syncMetric: Record<string, unknown> = {};
    let inventoryMetric: Record<string, unknown> = {};
    let paginationMetric: Record<string, unknown> = {};
    let analysisMetric: Record<string, unknown> = {};
    let cancellationMetric: Record<string, unknown> = {};
    let disconnectMetric: Record<string, unknown> = {};

    try {
      const connection = await timed<unknown>(() => page.request.post(
        `${applicationUrl}/api/clusters/${cluster.id}/connection-test`, { headers, timeout: 60_000 }));
      if (!connection.ok) errors.push(`connection-test:${connection.status}`);

      const syncConcurrency = Math.max(...concurrencySteps);
      const syncStarts = await Promise.all(Array.from({ length: syncConcurrency }, () =>
        timed<StartJob>(() => page.request.post(`${applicationUrl}/api/clusters/${cluster.id}/sync`, {
          headers, timeout: 60_000,
        }))));
      const syncJobIds = [...new Set(syncStarts.filter((item) => item.ok && item.value?.jobId)
        .map((item) => item.value!.jobId))];
      const syncTerminal = await Promise.all(syncJobIds.map((jobId) => waitForJob(page.request, jobId)));
      const syncDurations = syncStarts.map((item) => item.durationMs);
      syncMetric = {
        concurrency: syncConcurrency,
        requests: syncStarts.length,
        successfulSubmissions: syncStarts.filter((item) => item.ok).length,
        uniqueJobIds: syncJobIds.length,
        deduplicated: syncJobIds.length === 1,
        terminalStatuses: syncTerminal.map((item) => item.status),
        latency: latency(syncDurations),
      };
      if (syncStarts.some((item) => !item.ok)) errors.push('sync-submission-failed');
      if (syncJobIds.length !== 1) errors.push(`sync-dedup-unique-jobs:${syncJobIds.length}`);
      if (syncTerminal.some((item) => item.status !== 'SUCCEEDED')) errors.push('sync-job-failed');

      const statusResponse = await page.request.get(`${applicationUrl}/api/clusters/${cluster.id}/sync-status`);
      const syncStatus = statusResponse.ok() ? await statusResponse.json() as SyncStatus : undefined;
      const inventoryPageResponse = await page.request.get(
        `${applicationUrl}/api/clusters/${cluster.id}/resources/page?namespace=${encodeURIComponent(namespace)}&resourceType=ConfigMap&page=0&size=20`);
      const inventoryPage = inventoryPageResponse.ok() ? await inventoryPageResponse.json() as ResourcePage : undefined;
      inventoryMetric = {
        syncStatus,
        expectedSyntheticResources: expectedResources,
        synchronizedConfigMaps: inventoryPage?.totalElements ?? 0,
      };
      if ((inventoryPage?.totalElements ?? 0) < expectedResources) errors.push('inventory-resource-count-too-low');

      const pageSamples: TimedResult<ResourcePage>[] = [];
      const waves: Array<Record<string, unknown>> = [];
      for (const concurrency of concurrencySteps) {
        const wave = await Promise.all(Array.from({ length: concurrency }, (_, index) =>
          timed<ResourcePage>(() => page.request.get(
            `${applicationUrl}/api/clusters/${cluster.id}/resources/page?namespace=${encodeURIComponent(namespace)}`
            + `&resourceType=ConfigMap&page=${index % Math.max(1, inventoryPage?.totalPages ?? 1)}&size=100`,
            { timeout: 60_000 }))));
        pageSamples.push(...wave);
        waves.push({ concurrency, requests: wave.length, successes: wave.filter((item) => item.ok).length,
          latency: latency(wave.map((item) => item.durationMs)) });
      }
      const pageSuccessRatio = pageSamples.filter((item) => item.ok).length / pageSamples.length;
      const pageLatency = latency(pageSamples.map((item) => item.durationMs));
      paginationMetric = { waves, successRatio: pageSuccessRatio, latency: pageLatency };
      if (pageSuccessRatio < minimumSuccessRatio) errors.push('pagination-success-ratio');
      if ((pageLatency.p95Ms ?? Number.MAX_SAFE_INTEGER) > pageP95LimitMs) errors.push('pagination-p95');

      const analysisConcurrency = Math.max(...concurrencySteps);
      const analysisStarts = await Promise.all(Array.from({ length: analysisConcurrency }, () =>
        timed<StartJob>(() => page.request.post(
          `${applicationUrl}/api/analysis/namespaces/${encodeURIComponent(namespace)}/jobs?clusterId=${cluster.id}`,
          { headers, timeout: 60_000 }))));
      const analysisJobIds = [...new Set(analysisStarts.filter((item) => item.ok && item.value?.jobId)
        .map((item) => item.value!.jobId))];
      const analysisStartedAt = performance.now();
      const analysisTerminal = await Promise.all(analysisJobIds.map((jobId) => waitForJob(page.request, jobId)));
      const analysisDurationMs = Math.round(performance.now() - analysisStartedAt);
      let fallbacks = 0;
      for (const job of analysisTerminal.filter((item) => item.status === 'SUCCEEDED')) {
        const response = await page.request.get(`${applicationUrl}/api/analysis/jobs/${job.id}/result`);
        if (response.ok()) {
          const result = await response.json() as Analysis;
          const parsed = JSON.parse(result.resultJson) as { analysisRuntime?: { sections?: Array<{ status?: string }> } };
          if (parsed.analysisRuntime?.sections?.some((section) => section.status === 'FALLBACK')) fallbacks += 1;
        }
      }
      analysisMetric = {
        concurrency: analysisConcurrency,
        requests: analysisStarts.length,
        successfulSubmissions: analysisStarts.filter((item) => item.ok).length,
        uniqueJobIds: analysisJobIds.length,
        deduplicated: analysisJobIds.length === 1,
        terminalStatuses: analysisTerminal.map((item) => item.status),
        fallbackCount: fallbacks,
        submissionLatency: latency(analysisStarts.map((item) => item.durationMs)),
        completionDurationMs: analysisDurationMs,
      };
      if (analysisStarts.some((item) => !item.ok)) errors.push('analysis-submission-failed');
      if (analysisJobIds.length !== 1) errors.push(`analysis-dedup-unique-jobs:${analysisJobIds.length}`);
      if (analysisTerminal.some((item) => item.status !== 'SUCCEEDED')) errors.push('analysis-job-failed');
      if (analysisDurationMs > syncP95LimitMs) errors.push('analysis-completion-slo');

      const cancelStart = await timed<StartJob>(() => page.request.post(
        `${applicationUrl}/api/analysis/namespaces/${encodeURIComponent(namespace)}/jobs?clusterId=${cluster.id}`,
        { headers: { ...headers, 'Accept-Language': 'ko' }, timeout: 60_000 }));
      if (cancelStart.ok && cancelStart.value?.jobId) {
        const canceled = await timed<Job>(() => page.request.post(
          `${applicationUrl}/api/jobs/${cancelStart.value!.jobId}/cancel`, { headers, timeout: 60_000 }));
        cancellationMetric = { jobId: cancelStart.value.jobId, requestStatus: canceled.status,
          finalStatus: canceled.value?.status ?? 'UNKNOWN', durationMs: canceled.durationMs };
        if (!canceled.ok || canceled.value?.status !== 'CANCELED') errors.push('job-cancel-not-observed');
      } else {
        cancellationMetric = { finalStatus: 'NOT_STARTED', error: cancelStart.error };
        errors.push('job-cancel-start-failed');
      }

      const disconnectConcurrency = Math.max(...concurrencySteps);
      const streamPath = `/api/clusters/${cluster.id}/resources/Deployment/log-source/logs/stream`
        + `?namespace=${encodeURIComponent(namespace)}&podName=${encodeURIComponent(podName)}`
        + '&containerName=logger&tailLines=1';
      const disconnectResult = await page.evaluate(async ({ streamPath, count }) => {
        const controllers = Array.from({ length: count }, () => new AbortController());
        const started = performance.now();
        const requests = controllers.map((controller) => fetch(streamPath, {
          credentials: 'same-origin', headers: { Accept: 'text/event-stream' }, signal: controller.signal,
        }).then((response) => ({ ok: response.ok, status: response.status }))
          .catch((error) => ({ ok: error?.name === 'AbortError', status: 0 })));
        await new Promise((resolve) => setTimeout(resolve, 750));
        controllers.forEach((controller) => controller.abort());
        const results = await Promise.all(requests);
        return { durationMs: Math.round(performance.now() - started), opened: results.filter((item) => item.status === 200).length,
          cleanAbortOrOpen: results.filter((item) => item.ok).length };
      }, { streamPath, count: disconnectConcurrency });
      disconnectMetric = { concurrency: disconnectConcurrency, ...disconnectResult };
      if (disconnectResult.cleanAbortOrOpen < disconnectConcurrency) errors.push('sse-disconnect-errors');
    } catch (error) {
      errors.push(`unexpected:${error instanceof Error ? error.message : String(error)}`);
    } finally {
      await page.request.delete(`${applicationUrl}/api/clusters/${cluster.id}`, { headers, timeout: 60_000 });
    }

    const report = {
      reportVersion: '1.0',
      status: errors.length === 0 ? 'PASSED' : 'FAILED',
      profile: process.env.AIOPS_SOAK_PROFILE ?? 'smoke',
      environment: 'local-kubernetes-synthetic',
      sourceVersion: process.env.AIOPS_SOAK_SOURCE_VERSION ?? 'local-workspace',
      executedAt: new Date().toISOString(),
      fixture: { clusterName, namespace, expectedResources, concurrencySteps },
      thresholds: { minimumSuccessRatio, pageP95LimitMs, syncP95LimitMs },
      metrics: { registration, sync: syncMetric, inventory: inventoryMetric, pagination: paginationMetric,
        analysis: analysisMetric, cancellation: cancellationMetric, sseDisconnect: disconnectMetric },
      database: { statementCountAvailable: false, queryCount: null, transactionCount: null },
      limitations: [
        'Synthetic ConfigMaps exercise inventory size without scheduling one Pod per resource.',
        'Database counters are enriched by the host runner when PostgreSQL exposes them.',
        'Backend/Runner restart and terminal quota probes are reserved for the disruptive full profile.',
      ],
      errors,
    };
    await mkdir(dirname(reportPath), { recursive: true });
    await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
    expect(errors, JSON.stringify(report, null, 2)).toEqual([]);
  });
});
