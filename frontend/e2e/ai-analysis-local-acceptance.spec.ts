import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { waitForAuthenticatedPortal } from './helpers/live-auth';

const enabled = process.env.AIOPS_AI_E2E_ACCEPTANCE === 'true';
const applicationUrl = process.env.AIOPS_LIVE_APPLICATION_URL ?? 'http://127.0.0.1:30081';
const clusterName = process.env.AIOPS_AI_E2E_CLUSTER_NAME ?? '';
const namespace = process.env.AIOPS_AI_E2E_NAMESPACE ?? '';
const reportPath = process.env.AIOPS_AI_E2E_REPORT ?? '';

interface Cluster { id: string; name: string; description?: string }
interface Job { id: string; status: string; errorCode?: string; errorMessage?: string }
interface Analysis { id: string; asyncJobId?: string; status: string; schemaVersion: string; resultJson: string }
interface AnalysisStart { jobId: string; analysisId: string }
interface CommandPreview {
  executable: boolean;
  requiresConfirmation: boolean;
  confirmationText: string;
  rbacAllowed: boolean;
  dryRunPassed: boolean;
  rollbackGuardPassed: boolean;
  reason: string;
}
interface CommandExecution { id: string; status: string; exitCode: number | null; command: string; reason: string }

async function login(page: Page): Promise<void> {
  const username = process.env.AIOPS_AI_E2E_USERNAME ?? '';
  const password = process.env.AIOPS_AI_E2E_PASSWORD ?? '';
  expect(username, 'AIOPS_AI_E2E_USERNAME must be configured').not.toBe('');
  expect(password, 'AIOPS_AI_E2E_PASSWORD must be configured').not.toBe('');
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

async function get<T>(request: APIRequestContext, path: string): Promise<T> {
  const response = await request.get(`${applicationUrl}${path}`);
  expect(response.ok(), `${path} returned ${response.status()}: ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<T>;
}

async function post<T>(request: APIRequestContext, path: string, data: unknown = {}): Promise<T> {
  const response = await request.post(`${applicationUrl}${path}`, {
    data,
    headers: { 'X-XSRF-TOKEN': await csrf(request), 'Accept-Language': 'en' },
    timeout: 300_000,
  });
  expect(response.ok(), `${path} returned ${response.status()}: ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<T>;
}

async function remove(request: APIRequestContext, path: string): Promise<void> {
  const response = await request.delete(`${applicationUrl}${path}`, {
    headers: { 'X-XSRF-TOKEN': await csrf(request) },
  });
  expect(response.ok(), `${path} returned ${response.status()}: ${await response.text()}`).toBeTruthy();
}

async function waitForJob(request: APIRequestContext, jobId: string): Promise<Job> {
  let latest: Job | undefined;
  await expect.poll(async () => {
    latest = await get<Job>(request, `/api/jobs/${jobId}`);
    return latest.status;
  }, { timeout: 300_000, intervals: [500, 1000, 2000, 5000] }).toBe('SUCCEEDED');
  return latest as Job;
}

function parsedResult(analysis: Analysis): Record<string, unknown> {
  expect(analysis.status).toBe('SUCCEEDED');
  expect(analysis.schemaVersion).toBe('analysis-result.v1');
  return JSON.parse(analysis.resultJson) as Record<string, unknown>;
}

test.describe('local synthetic AI Analysis A-1 to A-6 acceptance', () => {
  test.skip(!enabled, 'Explicit local AI Analysis acceptance is disabled.');

  test('collects deterministic evidence, guards mutations, and closes the reanalysis loop', async ({ page }) => {
    test.setTimeout(600_000);
    expect(clusterName).not.toBe('');
    expect(namespace).toMatch(/^aiops-e2e-[a-zA-Z0-9-]+$/);
    expect(reportPath).not.toBe('');
    await login(page);

    const credentialFile = process.env.AIOPS_AI_E2E_CREDENTIAL_FILE ?? '';
    expect(credentialFile).not.toBe('');
    const credential = JSON.parse(await readFile(credentialFile, 'utf8')) as {
      apiServerUrl: string;
      caCertificate: string;
      token: string;
    };
    const existingClusters = await get<Cluster[]>(page.request, '/api/clusters');
    for (const stale of existingClusters.filter((candidate) =>
      candidate.name.startsWith('aiops-e2e-') && candidate.description === 'Ephemeral local AI Analysis acceptance fixture')) {
      await remove(page.request, `/api/clusters/${stale.id}`);
    }
    const cluster = await post<Cluster>(page.request, '/api/clusters', {
      name: clusterName,
      description: 'Ephemeral local AI Analysis acceptance fixture',
      environment: 'DEV',
      provider: 'KIND',
      region: 'local-acceptance',
      credentialType: 'SERVICE_ACCOUNT_TOKEN',
      serviceAccount: credential,
      namespaceAccess: { clusterWide: false, allowedNamespaces: [namespace], defaultNamespace: namespace },
      syncSettings: { autoSyncEnabled: false, syncIntervalSeconds: 300 },
    });

    try {
      const baseline = await post<Analysis>(page.request,
        `/api/analysis/namespaces/${encodeURIComponent(namespace)}?clusterId=${cluster.id}`);
      const baselineResult = parsedResult(baseline);
      const serializedBaseline = JSON.stringify(baselineResult).toLowerCase();
      expect(serializedBaseline).toMatch(/permission denied|privileged port|port-startup/);
      expect(serializedBaseline).toMatch(/port-mismatch|targetport=9090|targetport.*containerport/);
      for (const requiredSection of [
        'analysisQuality', 'logIntelligence', 'actionRecommendations', 'issueGroups',
        'evidenceLedger', 'commandSafety', 'analysisDiagnostics',
      ]) {
        expect(baselineResult).toHaveProperty(requiredSection);
      }

      const restartCommand = `kubectl rollout restart deployment/rollout-target -n ${namespace}`;
      const restartPreview = await post<CommandPreview>(page.request,
        `/api/analysis/${baseline.id}/commands/preview`, { command: restartCommand });
      expect(restartPreview).toMatchObject({
        executable: true, requiresConfirmation: true, rbacAllowed: true, dryRunPassed: true,
      });
      expect(restartPreview.confirmationText).toBe(`APPLY ${namespace}/rollout-target`);

      const unconfirmed = await page.request.post(`${applicationUrl}/api/analysis/${baseline.id}/commands`, {
        data: { command: restartCommand, confirmText: '' },
        headers: { 'X-XSRF-TOKEN': await csrf(page.request), 'Accept-Language': 'en' },
      });
      expect(unconfirmed.status()).toBe(201);
      const blockedRestart = await unconfirmed.json() as CommandExecution;
      expect(blockedRestart.status).toBe('BLOCKED');
      expect(blockedRestart.reason.toLowerCase()).toMatch(/confirmation|확인 문구/);

      const restart = await post<CommandExecution>(page.request, `/api/analysis/${baseline.id}/commands`, {
        command: restartCommand,
        confirmText: restartPreview.confirmationText,
      });
      expect(restart).toMatchObject({ status: 'SUCCEEDED', exitCode: 0, command: restartCommand });

      const rollbackCommand = `kubectl rollout undo deployment/rollout-target -n ${namespace} --to-revision=1`;
      const rollbackPreview = await post<CommandPreview>(page.request,
        `/api/analysis/${baseline.id}/commands/preview`, { command: rollbackCommand });
      expect(rollbackPreview).toMatchObject({
        executable: true,
        requiresConfirmation: true,
        rbacAllowed: true,
        dryRunPassed: true,
        rollbackGuardPassed: true,
      });
      expect(rollbackPreview.confirmationText).toBe(`ROLLBACK ${namespace}/rollout-target TO REVISION 1`);
      const rollback = await post<CommandExecution>(page.request, `/api/analysis/${baseline.id}/commands`, {
        command: rollbackCommand,
        confirmText: rollbackPreview.confirmationText,
      });
      expect(rollback).toMatchObject({ status: 'SUCCEEDED', exitCode: 0, command: rollbackCommand });

      const unsafeRollback = await post<CommandPreview>(page.request,
        `/api/analysis/${baseline.id}/commands/preview`, {
          command: `kubectl rollout undo deployment/rollout-target -n ${namespace}`,
        });
      expect(unsafeRollback.executable).toBe(false);
      expect(unsafeRollback.reason.toLowerCase()).toMatch(/revision|unsupported|명시/);

      const retry = await post<AnalysisStart>(page.request, `/api/analysis/${baseline.id}/retry`);
      await waitForJob(page.request, retry.jobId);
      const followUp = await get<Analysis>(page.request, `/api/analysis/jobs/${retry.jobId}/result`);
      const followUpResult = parsedResult(followUp);
      expect(followUpResult).toHaveProperty('analysisComparison');
      expect(followUpResult).toHaveProperty('commandVerification');
      const comparison = followUpResult.analysisComparison as Record<string, unknown>;
      const commandVerification = followUpResult.commandVerification as Record<string, unknown>;

      const report = {
        reportVersion: '1.0',
        environment: 'local-kubernetes-synthetic',
        sourceVersion: process.env.AIOPS_AI_E2E_SOURCE_VERSION ?? 'local-workspace',
        executedAt: new Date().toISOString(),
        tester: 'local-platform-admin',
        scenarios: [
          { scenarioId: 'A-1', cluster: clusterName, namespace, status: 'PASSED', analysisId: baseline.id,
            jobId: baseline.asyncJobId ?? '', evidence: [`analysis:${baseline.id}`, 'log:permission-denied', 'schema:analysis-result.v1'],
            details: { summary: baselineResult.summary, signalDetected: true, schemaVersion: baseline.schemaVersion },
            notes: 'Synthetic privileged-port startup failure.' },
          { scenarioId: 'A-2', cluster: clusterName, namespace, status: 'PASSED', analysisId: baseline.id,
            jobId: baseline.asyncJobId ?? '', evidence: [`analysis:${baseline.id}`, 'service:service-backend', 'targetPort:9090/containerPort:8080'],
            details: { service: 'service-backend', targetPort: 9090, containerPort: 8080, signalDetected: true },
            notes: 'Synthetic Service port mismatch.' },
          { scenarioId: 'A-3', cluster: clusterName, namespace, status: 'PASSED', analysisId: baseline.id,
            jobId: '', evidence: [`blockedExecution:${blockedRestart.id}`, `commandExecution:${restart.id}`, `confirmation:${restartPreview.confirmationText}`],
            details: { blockedStatus: blockedRestart.status, executedStatus: restart.status, exitCode: restart.exitCode },
            notes: 'Rollout restart exact-confirmation guard.' },
          { scenarioId: 'A-4', cluster: clusterName, namespace, status: 'PASSED', analysisId: baseline.id,
            jobId: '', evidence: [`commandExecution:${rollback.id}`, 'targetRevision:1', `confirmation:${rollbackPreview.confirmationText}`],
            details: { status: rollback.status, exitCode: rollback.exitCode, rollbackGuardPassed: rollbackPreview.rollbackGuardPassed },
            notes: 'Explicit revision rollback.' },
          { scenarioId: 'A-5', cluster: clusterName, namespace, status: 'PASSED', analysisId: baseline.id,
            jobId: '', evidence: ['preview:executable=false', `reason:${unsafeRollback.reason}`],
            details: { executable: unsafeRollback.executable, reason: unsafeRollback.reason },
            notes: 'Rollback without revision was blocked.' },
          { scenarioId: 'A-6', cluster: clusterName, namespace, status: 'PASSED', analysisId: followUp.id,
            jobId: retry.jobId,
            evidence: [`previousAnalysis:${baseline.id}`, `analysis:${followUp.id}`, `job:${retry.jobId}`,
              'analysisComparison:present', 'commandVerification:present'],
            details: {
              previousAnalysisId: comparison.previousAnalysisId,
              trend: comparison.trend,
              carriedFromAnalysisId: commandVerification.carriedFromAnalysisId,
              evidenceScope: commandVerification.evidenceScope,
              lastStatus: commandVerification.lastStatus,
            },
            notes: 'Closed-loop reanalysis.' },
        ],
      };
      await mkdir(dirname(reportPath), { recursive: true });
      await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
    } finally {
      await remove(page.request, `/api/clusters/${cluster.id}`);
    }
  });
});
