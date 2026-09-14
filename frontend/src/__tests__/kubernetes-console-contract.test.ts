import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string) {
  return readFileSync(resolve(process.cwd(), path), 'utf8');
}

const consoleView = source('src/views/KubernetesConsoleView.vue');
const clusterDetail = source('src/views/ClusterDetailView.vue');
const client = source('src/api/client.ts');
const router = source('src/router/index.ts');
const styles = source('src/styles/kubernetes-console.css');
const cookbook = source('src/components/CommandCookBook.vue');
const cookbookCommands = source('src/utils/commandCookbook.ts');

describe('Kubernetes Console UX contract', () => {
  it('is reachable from cluster and resource detail without changing context', () => {
    expect(router).toContain("path: '/clusters/:clusterId/console'");
    expect(clusterDetail).toContain('openKubernetesConsole');
    expect(clusterDetail).toContain('openSelectedResourceInConsole');
    expect(consoleView).toContain('contextLocked');
  });

  it('validates before executing and confirms mutations', () => {
    expect(consoleView).toContain('await validate()');
    expect(consoleView).toContain('validation.value.requiresConfirmation');
    expect(consoleView).toContain('showConfirmation');
    expect(consoleView).toContain('run(true)');
  });

  it('streams, cancels and preserves reusable operator workflows', () => {
    expect(consoleView).toContain('streamCommandExecution');
    expect(consoleView).toContain('createTerminalSession');
    expect(consoleView).toContain("new Terminal(");
    expect(consoleView).toContain("new WebSocket(");
    expect(consoleView).toContain("execution?.exitCode != null");
    expect(consoleView).toContain("execution?.durationMs != null");
    expect(consoleView).toContain('terminalClosed');
    expect(consoleView).toContain('cancelCommandExecution');
    expect(consoleView).toContain('listCommandExecutions');
    expect(consoleView).toContain('createCommandFavorite');
    expect(client).toContain('/command-executions');
    expect(client).toContain('/command-favorites');
    expect(consoleView).toContain('sourceAnalysisId');
    expect(consoleView).toContain("t('console.analysisVerification')");
    expect(consoleView).toContain('maximumUserCommands');
    expect(consoleView).toContain("capability.executionBoundary === 'ISOLATED_RUNNER'");
    expect(consoleView).toContain("t('console.isolatedRunner')");
  });

  it('builds resource commands and exposes a closed-loop verification result', () => {
    expect(consoleView).toContain('buildTemplate');
    expect(consoleView).toContain("buildTemplate('describe')");
    expect(consoleView).toContain("buildTemplate('logs')");
    expect(consoleView).toContain('execution.verificationStatus');
    expect(consoleView).toContain('execution.beforeSnapshot');
    expect(consoleView).toContain('execution.afterSnapshot');
    expect(consoleView).toContain('execution.rollbackCommand');
    expect(consoleView).toContain("setFollowup('events')");
    expect(styles).toContain('.command-verification-result');
  });

  it('provides a searchable, read-first health check cookbook', () => {
    expect(consoleView).toContain('CommandCookBook');
    expect(consoleView).toContain('applyCookbookItem');
    expect(cookbook).toContain('cookbook-search');
    expect(cookbook).toContain('cookbook-categories');
    expect(cookbookCommands).toContain("id: 'serviceEndpoints'");
    expect(cookbookCommands).toContain("id: 'nodeConditions'");
    expect(cookbookCommands).toContain("id: 'canIListPods'");
    expect(cookbookCommands).not.toContain('base64 -d');
    expect(styles).toContain('.cookbook-panel');
  });

  it('keeps long output readable on desktop and mobile', () => {
    expect(styles).toContain('.terminal-output');
    expect(styles).toContain('overflow: auto');
    expect(styles).toContain('height: clamp(300px, 42dvh, 420px)');
    expect(consoleView).toContain('terminal?.scrollToBottom()');
    expect(consoleView).toContain('new ResizeObserver');
    expect(consoleView).toContain("t('console.focusPrompt')");
    expect(consoleView).not.toContain('displayMode');
    expect(styles).not.toContain('.kubernetes-console-page.dense');
    expect(styles).toContain('@media (max-width: 700px)');
  });
});
