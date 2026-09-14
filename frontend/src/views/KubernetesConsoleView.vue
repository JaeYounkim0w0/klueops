<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import type { Terminal as XtermTerminal } from '@xterm/xterm';
import type { FitAddon as XtermFitAddon } from '@xterm/addon-fit';
import CommandCookBook from '@/components/CommandCookBook.vue';
import { commandPlaceholders, resolveCookbookCommand, type CommandCookbookItem } from '@/utils/commandCookbook';
import {
  ApiError,
  api,
  streamCommandExecution,
  type ClusterResponse,
  type CommandCapabilityResponse,
  type CommandExecutionResponse,
  type CommandFavoriteResponse,
  type CommandValidationResponse,
  type KubernetesNamespaceResponse
} from '@/api/client';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const clusterId = computed(() => String(route.params.clusterId || ''));
const sourceAnalysisId = computed(() => uuidQuery(route.query.analysisId));
const analysisReturnPath = computed(() => {
  const value = typeof route.query.returnTo === 'string' ? route.query.returnTo : '';
  return value.startsWith('/analysis') ? value : '/analysis';
});
const cluster = ref<ClusterResponse | null>(null);
const capability = ref<CommandCapabilityResponse | null>(null);
const namespaces = ref<KubernetesNamespaceResponse[]>([]);
const favorites = ref<CommandFavoriteResponse[]>([]);
const history = ref<CommandExecutionResponse[]>([]);
const selectedNamespace = ref(String(route.query.namespace || 'default'));
const command = ref(String(route.query.command || 'kubectl get pods -o wide'));
const manifest = ref('');
const validation = ref<CommandValidationResponse | null>(null);
const validationError = ref('');
const execution = ref<CommandExecutionResponse | null>(null);
const stdout = ref('');
const stderr = ref('');
const outputSearch = ref('');
const outputWrap = ref(false);
const loading = ref(true);
const running = ref(false);
const showConfirmation = ref(false);
const showFavoriteForm = ref(false);
const activeUtility = ref<'cookbook' | 'favorites' | 'history'>('cookbook');
const favoriteName = ref('');
const favoriteDescription = ref('');
const favoriteShared = ref(false);
const templateKind = ref('pod');
const templateName = ref('');
const templateContainer = ref('');
const templateTail = ref(200);
const errorMessage = ref('');
const outputElement = ref<HTMLElement | null>(null);
const terminalElement = ref<HTMLElement | null>(null);
const terminalStarted = ref(false);
let terminal: XtermTerminal | null = null;
let fitAddon: XtermFitAddon | null = null;
let terminalSocket: WebSocket | null = null;
let terminalResizeObserver: ResizeObserver | null = null;
let validationTimer: number | undefined;
let streamController: AbortController | null = null;

const terminalText = computed(() => {
  const value = [stdout.value, stderr.value].filter(Boolean).join(stderr.value ? '\n' : '');
  if (!outputSearch.value.trim()) return value;
  const query = outputSearch.value.toLocaleLowerCase();
  return value.split('\n').filter((line) => line.toLocaleLowerCase().includes(query)).join('\n');
});
const needsManifest = computed(() => /(?:^|\s)(?:-f|--filename)(?:\s+|=)-(?:\s|$)/.test(command.value));
const hasPlaceholder = computed(() => commandPlaceholders(command.value).length > 0);
const canRun = computed(() => Boolean(command.value.trim()) && capability.value?.runnerAvailable && !running.value
  && !validationError.value && !hasPlaceholder.value);
const safetyTone = computed(() => {
  if (!validation.value) return '';
  if (validation.value.safety === 'READ_ONLY') return 'low';
  if (validation.value.safety === 'DIAGNOSE') return 'info';
  if (validation.value.safety === 'CHANGE') return 'warning';
  return 'critical';
});
const verificationTone = computed(() => {
  if (execution.value?.verificationStatus === 'VERIFIED_CHANGED') return 'verified';
  if (execution.value?.verificationStatus === 'VERIFIED_STABLE') return 'stable';
  if (execution.value?.verificationStatus === 'VERIFICATION_FAILED') return 'failed';
  return 'pending';
});

const quickTasks = computed(() => [
  { icon: 'pi pi-server', label: t('console.quickNodes'), command: 'kubectl get nodes -o wide', namespace: '__ALL__' },
  { icon: 'pi pi-box', label: t('console.quickPods'), command: 'kubectl get pods -o wide', namespace: selectedNamespace.value },
  { icon: 'pi pi-exclamation-triangle', label: t('console.quickEvents'), command: 'kubectl get events --field-selector type=Warning --sort-by=.lastTimestamp', namespace: selectedNamespace.value },
  { icon: 'pi pi-file', label: t('console.quickLogs'), command: 'kubectl logs -f POD_NAME --tail=200', namespace: selectedNamespace.value },
  { icon: 'pi pi-desktop', label: t('console.quickTerminal'), command: 'kubectl exec -it POD_NAME -- /bin/sh', namespace: selectedNamespace.value },
  { icon: 'pi pi-code', label: t('console.quickYaml'), command: 'kubectl get deployments -o yaml', namespace: selectedNamespace.value }
]);

watch([command, selectedNamespace], () => scheduleValidation());

onMounted(async () => {
  try {
    const [clusterValue, namespaceValues, capabilityValue, favoriteValues, historyValues] = await Promise.all([
      api.getCluster(clusterId.value),
      api.listNamespaces(clusterId.value),
      api.getCommandCapabilities(clusterId.value, selectedNamespace.value),
      api.listCommandFavorites(clusterId.value),
      api.listCommandExecutions(clusterId.value, undefined, 30)
    ]);
    cluster.value = clusterValue;
    namespaces.value = namespaceValues;
    capability.value = capabilityValue;
    favorites.value = favoriteValues;
    history.value = historyValues;
    await validate();
  } catch (cause) {
    errorMessage.value = message(cause, t('console.loadFailed'));
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => {
  if (validationTimer) window.clearTimeout(validationTimer);
  streamController?.abort();
  terminalSocket?.close();
  terminalResizeObserver?.disconnect();
  terminal?.dispose();
  window.removeEventListener('resize', fitTerminal);
});

function scheduleValidation() {
  validation.value = null;
  validationError.value = '';
  if (validationTimer) window.clearTimeout(validationTimer);
  validationTimer = window.setTimeout(validate, 350);
}

async function validate() {
  if (!command.value.trim()) return;
  try {
    validation.value = await api.validateCommand(clusterId.value, {
      namespace: selectedNamespace.value === '__ALL__' ? undefined : selectedNamespace.value,
      command: command.value
    });
    validationError.value = '';
  } catch (cause) {
    validation.value = null;
    validationError.value = message(cause, t('console.validationFailed'));
  }
}

async function requestRun() {
  await validate();
  if (!validation.value) return;
  if (needsManifest.value && !manifest.value.trim()) {
    validationError.value = t('console.manifestRequired');
    return;
  }
  if (validation.value.requiresConfirmation) {
    showConfirmation.value = true;
    return;
  }
  await run(false);
}

async function run(confirmed: boolean) {
  if (validation.value?.interactive) {
    await runTerminal(confirmed);
    return;
  }
  terminalSocket?.close();
  terminalSocket = null;
  terminal?.dispose();
  terminal = null;
  terminalStarted.value = false;
  showConfirmation.value = false;
  running.value = true;
  errorMessage.value = '';
  stdout.value = '';
  stderr.value = '';
  streamController?.abort();
  streamController = new AbortController();
  try {
    execution.value = await api.executeCommand(clusterId.value, {
      sourceAnalysisId: sourceAnalysisId.value || undefined,
      namespace: selectedNamespace.value === '__ALL__' ? undefined : selectedNamespace.value,
      command: command.value,
      manifest: needsManifest.value ? manifest.value : undefined,
      confirmed
    });
    await streamCommandExecution(clusterId.value, execution.value.id, handleStreamEvent, streamController.signal);
    execution.value = await api.getCommandExecution(clusterId.value, execution.value.id);
    stdout.value = execution.value.stdoutText || stdout.value;
    stderr.value = execution.value.stderrText || stderr.value;
  } catch (cause) {
    if ((cause as Error)?.name !== 'AbortError') errorMessage.value = message(cause, t('console.executionFailed'));
  } finally {
    running.value = false;
    await refreshHistory();
  }
}

async function runTerminal(confirmed: boolean) {
  showConfirmation.value = false;
  running.value = true;
  errorMessage.value = '';
  stdout.value = '';
  stderr.value = '';
  try {
    const ticket = await api.createTerminalSession(clusterId.value, {
      sourceAnalysisId: sourceAnalysisId.value || undefined,
      namespace: selectedNamespace.value === '__ALL__' ? undefined : selectedNamespace.value,
      command: command.value,
      confirmed
    });
    execution.value = { id: ticket.executionId, clusterId: clusterId.value, sourceAnalysisId: sourceAnalysisId.value || undefined,
      namespace: selectedNamespace.value === '__ALL__' ? undefined : selectedNamespace.value,
      command: command.value, safety: 'PRIVILEGED_INTERACTIVE', status: 'QUEUED', truncated: false,
      createdBy: '', createdAt: new Date().toISOString() };
    terminalStarted.value = true;
    await nextTick();
    await openTerminal(ticket.websocketPath);
  } catch (cause) {
    running.value = false;
    errorMessage.value = message(cause, t('console.terminalFailed'));
  }
}

async function openTerminal(path: string) {
  const [{ Terminal }, { FitAddon }] = await Promise.all([
    import('@xterm/xterm'),
    import('@xterm/addon-fit')
  ]);
  terminal?.dispose();
  terminal = new Terminal({ cursorBlink: true, convertEol: true, scrollback: 5000,
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontSize: 12,
    theme: { background: '#0c121a', foreground: '#dce6ef', cursor: '#78a9d8', selectionBackground: '#315f9f88' } });
  fitAddon = new FitAddon();
  terminal.loadAddon(fitAddon);
  if (terminalElement.value) terminal.open(terminalElement.value);
  terminalResizeObserver?.disconnect();
  if (terminalElement.value) {
    terminalResizeObserver = new ResizeObserver(() => fitTerminal());
    terminalResizeObserver.observe(terminalElement.value);
  }
  fitTerminal();
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const base = import.meta.env.VITE_API_BASE_URL
    ? new URL(import.meta.env.VITE_API_BASE_URL, window.location.href).host : window.location.host;
  terminalSocket = new WebSocket(`${protocol}//${base}${path}`);
  terminal.writeln(t('console.terminalConnecting'));
  terminal.onData((data) => {
    terminal?.scrollToBottom();
    if (terminalSocket?.readyState === WebSocket.OPEN) terminalSocket.send(JSON.stringify({ type: 'input', data }));
  });
  terminal.onResize(({ cols, rows }) => sendTerminalResize(cols, rows));
  terminalSocket.onopen = () => {
    terminal?.clear();
    fitTerminal();
    if (terminal) sendTerminalResize(terminal.cols, terminal.rows);
  };
  terminalSocket.onmessage = (event) => {
    const message = JSON.parse(String(event.data)) as { type: string; data: Record<string, unknown> };
    if (message.type === 'output') {
      const text = String(message.data.text || '');
      terminal?.write(text);
      if (message.data.channel === 'stderr') stderr.value += text; else stdout.value += text;
    }
    if (message.type === 'error') {
      const detail = String(message.data.message || t('console.terminalConnectionFailed'));
      errorMessage.value = detail;
      terminal?.writeln(`\r\n[error] ${detail}`);
    }
    if (message.type === 'status') updateTerminalStatus(message.data);
  };
  terminalSocket.onerror = () => {
    if (!errorMessage.value) errorMessage.value = t('console.terminalConnectionFailed');
  };
  terminalSocket.onclose = (event) => {
    const active = execution.value && ['QUEUED', 'RUNNING'].includes(execution.value.status);
    if (active && !errorMessage.value && event.code !== 1000) {
      errorMessage.value = t('console.terminalClosed', { code: event.code, reason: event.reason || '-' });
    }
    running.value = false;
    void refreshHistory();
  };
  window.addEventListener('resize', fitTerminal);
  terminal.focus();
}

function updateTerminalStatus(data: Record<string, unknown>) {
  if (!execution.value) return;
  const status = String(data.status || execution.value.status) as CommandExecutionResponse['status'];
  execution.value = { ...execution.value, status, exitCode: data.exitCode == null ? undefined : Number(data.exitCode) };
  if (!['QUEUED', 'RUNNING'].includes(status)) {
    running.value = false;
    terminal?.writeln(`\r\n[session ${status.toLowerCase()}]`);
  }
}

function fitTerminal() {
  if (!terminal || !fitAddon || !terminalElement.value) return;
  try { fitAddon.fit(); } catch { /* The terminal may be transitioning between responsive layouts. */ }
}

function focusTerminalPrompt() {
  terminal?.scrollToBottom();
  terminal?.focus();
}

function sendTerminalResize(columns: number, rows: number) {
  if (terminalSocket?.readyState === WebSocket.OPEN) {
    terminalSocket.send(JSON.stringify({ type: 'resize', columns, rows }));
  }
}

function handleStreamEvent(event: { type: string; data: unknown }) {
  const data = event.data as Record<string, unknown>;
  if (event.type === 'stdout') stdout.value += String(data.text || '');
  if (event.type === 'stderr') stderr.value += String(data.text || '');
  if ((event.type === 'status' || event.type === 'snapshot') && data.id) {
    execution.value = data as unknown as CommandExecutionResponse;
    if (data.stdoutText) stdout.value = String(data.stdoutText);
    if (data.stderrText) stderr.value = String(data.stderrText);
  }
  nextTick(() => {
    if (outputElement.value) outputElement.value.scrollTop = outputElement.value.scrollHeight;
  });
}

async function stop() {
  if (!execution.value) return;
  if (terminalStarted.value && terminalSocket) {
    terminalSocket.close(1000, 'operator stopped terminal');
    running.value = false;
    return;
  }
  try {
    execution.value = await api.cancelCommandExecution(clusterId.value, execution.value.id);
    streamController?.abort();
  } catch (cause) {
    errorMessage.value = message(cause, t('console.cancelFailed'));
  } finally {
    running.value = false;
    await refreshHistory();
  }
}

function applyTask(task: { command: string; namespace: string }) {
  command.value = task.command;
  if (task.namespace) selectedNamespace.value = task.namespace;
}

function applyCookbookItem(item: CommandCookbookItem) {
  command.value = resolveCookbookCommand(item.command, {
    POD_NAME: templateKind.value === 'pod' ? templateName.value : '',
    CONTAINER_NAME: templateContainer.value,
    SERVICE_NAME: templateKind.value === 'service' ? templateName.value : '',
    DEPLOYMENT_NAME: templateKind.value === 'deployment' ? templateName.value : '',
    PVC_NAME: templateKind.value === 'pvc' ? templateName.value : '',
    INGRESS_NAME: templateKind.value === 'ingress' ? templateName.value : ''
  });
  if (item.scope === 'cluster') selectedNamespace.value = '__ALL__';
  else if (selectedNamespace.value === '__ALL__') selectedNamespace.value = namespaces.value[0]?.name || 'default';
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function buildTemplate(mode: 'get' | 'describe' | 'logs' | 'yaml') {
  const kind = templateKind.value.trim() || 'pod';
  const name = templateName.value.trim();
  const target = name ? `${kind}/${name}` : kind;
  if (mode === 'logs') {
    if (!name) return;
    command.value = `kubectl logs ${name}${templateContainer.value.trim() ? ` -c ${templateContainer.value.trim()}` : ''} --tail=${Math.max(1, Math.min(5000, templateTail.value || 200))}`;
    return;
  }
  command.value = mode === 'yaml' ? `kubectl get ${target} -o yaml` : `kubectl ${mode} ${target}`;
}

function setFollowup(mode: 'events' | 'logs' | 'yaml' | 'rollback') {
  if (mode === 'events') command.value = 'kubectl get events --field-selector type=Warning --sort-by=.lastTimestamp';
  if (mode === 'logs') buildTemplate('logs');
  if (mode === 'yaml') buildTemplate('yaml');
  if (mode === 'rollback' && execution.value?.rollbackCommand) command.value = execution.value.rollbackCommand;
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function loadFavorite(item: CommandFavoriteResponse) {
  command.value = item.command;
  selectedNamespace.value = item.namespace || 'default';
}

async function createFavorite() {
  if (!favoriteName.value.trim() || !command.value.trim()) return;
  try {
    await api.createCommandFavorite(clusterId.value, {
      name: favoriteName.value.trim(), description: favoriteDescription.value.trim() || undefined,
      command: command.value, namespace: selectedNamespace.value === '__ALL__' ? undefined : selectedNamespace.value,
      shared: favoriteShared.value, sortOrder: favorites.value.length
    });
    favorites.value = await api.listCommandFavorites(clusterId.value);
    favoriteName.value = '';
    favoriteDescription.value = '';
    favoriteShared.value = false;
    showFavoriteForm.value = false;
  } catch (cause) {
    errorMessage.value = message(cause, t('console.favoriteSaveFailed'));
  }
}

async function removeFavorite(item: CommandFavoriteResponse) {
  try {
    await api.deleteCommandFavorite(clusterId.value, item.id);
    favorites.value = favorites.value.filter((favorite) => favorite.id !== item.id);
  } catch (cause) {
    errorMessage.value = message(cause, t('console.favoriteDeleteFailed'));
  }
}

async function refreshHistory() {
  try {
    history.value = await api.listCommandExecutions(clusterId.value, undefined, 30);
  } catch {
    // The current result remains usable when the history refresh is unavailable.
  }
}

function loadHistory(item: CommandExecutionResponse) {
  execution.value = item;
  command.value = item.command;
  selectedNamespace.value = item.namespace || '__ALL__';
  stdout.value = item.stdoutText || '';
  stderr.value = item.stderrText || '';
}

async function copyOutput() {
  if (terminalText.value) await navigator.clipboard.writeText(terminalText.value);
}

function downloadOutput() {
  const blob = new Blob([terminalText.value], { type: 'text/plain;charset=utf-8' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `kubectl-${cluster.value?.name || clusterId.value}-${Date.now()}.log`;
  link.click();
  URL.revokeObjectURL(link.href);
}

function duration(value?: number) {
  if (value == null) return '-';
  return value < 1000 ? `${value}ms` : `${(value / 1000).toFixed(1)}s`;
}

function returnToAnalysis() {
  void router.push(analysisReturnPath.value);
}

function reanalyzeTarget() {
  void router.push({
    name: 'analysis',
    query: {
      clusterId: clusterId.value,
      namespace: selectedNamespace.value === '__ALL__' ? undefined : selectedNamespace.value,
      mode: selectedNamespace.value === '__ALL__' ? 'cluster' : 'namespace'
    }
  });
}

function uuidQuery(value: unknown) {
  const text = typeof value === 'string' ? value : '';
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(text) ? text : '';
}

function message(cause: unknown, fallback: string) {
  return cause instanceof ApiError || cause instanceof Error ? cause.message : fallback;
}
</script>

<template>
  <section class="page kubernetes-console-page">
    <header class="page-header console-page-header">
      <div>
        <button class="text-button" type="button" @click="router.push({ name: 'cluster-detail', params: { clusterId } })">
          <i class="pi pi-arrow-left"></i><span>{{ cluster?.name || t('pages.clusterDetailTitle') }}</span>
        </button>
        <h1>{{ t('console.title') }}</h1>
        <p>{{ t('console.description') }}</p>
      </div>
      <div class="console-header-tools">
        <div class="console-runtime-status"><span class="status-dot" :class="capability?.runnerAvailable ? 'ready' : 'failed'"></span><div><strong>{{ capability?.runnerAvailable ? t('console.runnerReady') : t('console.runnerUnavailable') }}</strong><small>{{ capability?.kubectlVersion || '-' }}<template v-if="capability"> · {{ capability.executionBoundary === 'ISOLATED_RUNNER' ? t('console.isolatedRunner') : t('console.localRunner') }} · {{ t('console.executionLimits', { commands: capability.maximumUserCommands, terminals: capability.maximumUserTerminals }) }}</template></small></div></div>
      </div>
    </header>

    <div v-if="errorMessage" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><div><strong>{{ t('console.error') }}</strong><pre>{{ errorMessage }}</pre></div></div>
    <section v-if="sourceAnalysisId" class="console-analysis-context" aria-label="AI Analysis verification context">
      <div><i class="pi pi-sparkles"></i><div><strong>{{ t('console.analysisVerification') }}</strong><span>{{ t('console.analysisVerificationDescription') }}</span></div></div>
      <div class="inline-actions"><button class="secondary-button compact-button" type="button" @click="returnToAnalysis"><i class="pi pi-arrow-left"></i>{{ t('console.backToAnalysis') }}</button><button class="primary-button compact-button" type="button" @click="reanalyzeTarget"><i class="pi pi-refresh"></i>{{ t('console.reanalyze') }}</button></div>
    </section>
    <div v-if="loading" class="empty-state"><i class="pi pi-spin pi-spinner"></i><span>{{ t('common.loading') }}</span></div>

    <div v-else class="console-workspace">
      <main class="console-primary">
        <section class="console-context-band">
          <label><span>{{ t('console.cluster') }}</span><strong>{{ cluster?.name || clusterId }}</strong></label>
          <label><span>{{ t('console.namespace') }}</span><select v-model="selectedNamespace"><option value="__ALL__">{{ t('console.allNamespaces') }}</option><option value="default">default</option><option v-for="item in namespaces" :key="item.name" :value="item.name">{{ item.name }}</option></select></label>
          <span class="context-lock"><i class="pi pi-lock"></i>{{ t('console.contextLocked') }}</span>
        </section>

        <section class="console-command-section">
          <div class="console-section-heading"><div><span>KUBECTL COMMAND</span><h2>{{ t('console.commandTitle') }}</h2></div><button class="icon-button" type="button" :title="t('console.saveFavorite')" @click="showFavoriteForm = !showFavoriteForm"><i class="pi pi-star"></i></button></div>
          <div class="quick-command-list"><button v-for="task in quickTasks" :key="task.label" type="button" @click="applyTask(task)"><i :class="task.icon"></i><span>{{ task.label }}</span></button></div>
          <details class="console-command-builder">
            <summary><i class="pi pi-sliders-h"></i>{{ t('console.commandBuilder') }}</summary>
            <div class="command-builder-fields">
              <label><span>{{ t('console.resourceKind') }}</span><select v-model="templateKind"><option>pod</option><option>deployment</option><option>statefulset</option><option>daemonset</option><option>service</option><option>configmap</option><option>secret</option><option>pvc</option><option>job</option></select></label>
              <label><span>{{ t('console.resourceName') }}</span><input v-model="templateName" :placeholder="t('console.optionalName')" /></label>
              <label><span>{{ t('console.container') }}</span><input v-model="templateContainer" :placeholder="t('console.optionalContainer')" /></label>
              <label><span>{{ t('console.tailRows') }}</span><input v-model.number="templateTail" type="number" min="1" max="5000" /></label>
            </div>
            <div class="inline-actions"><button class="secondary-button compact-button" type="button" @click="buildTemplate('get')">Get</button><button class="secondary-button compact-button" type="button" @click="buildTemplate('describe')">Describe</button><button class="secondary-button compact-button" type="button" :disabled="!templateName" @click="buildTemplate('logs')">Logs</button><button class="secondary-button compact-button" type="button" @click="buildTemplate('yaml')">YAML</button></div>
          </details>
          <textarea v-model="command" class="command-input" rows="3" spellcheck="false" :placeholder="t('console.commandPlaceholder')"></textarea>
          <div v-if="needsManifest" class="manifest-editor"><label for="manifest-input">{{ t('console.manifestTitle') }}</label><textarea id="manifest-input" v-model="manifest" rows="12" spellcheck="false" placeholder="apiVersion: apps/v1&#10;kind: Deployment"></textarea></div>

          <div v-if="showFavoriteForm" class="favorite-form">
            <input v-model="favoriteName" maxlength="80" :placeholder="t('console.favoriteName')" />
            <input v-model="favoriteDescription" maxlength="300" :placeholder="t('console.favoriteDescription')" />
            <label><input v-model="favoriteShared" type="checkbox" /> {{ t('console.sharedFavorite') }}</label>
            <div class="inline-actions"><button class="secondary-button compact-button" type="button" @click="showFavoriteForm = false">{{ t('common.close') }}</button><button class="primary-button compact-button" type="button" :disabled="!favoriteName.trim()" @click="createFavorite">{{ t('common.save') }}</button></div>
          </div>

          <div class="command-validation" :class="validationError ? 'invalid' : safetyTone">
            <i :class="validationError ? 'pi pi-times-circle' : validation ? 'pi pi-shield' : 'pi pi-spin pi-spinner'"></i>
            <div v-if="validationError"><strong>{{ t('console.invalidCommand') }}</strong><span>{{ validationError }}</span></div>
            <div v-else-if="validation"><strong>{{ validation.safety }} · {{ validation.targetSummary }}</strong><span>{{ hasPlaceholder ? t('console.replacePlaceholder') : validation.warnings.join(' · ') || t('console.validationReady') }}</span></div>
            <div v-else><strong>{{ t('console.validating') }}</strong></div>
          </div>
          <div class="command-actions"><small>{{ t('console.executionHint') }}</small><button v-if="running" class="danger-button" type="button" @click="stop"><i class="pi pi-stop"></i>{{ t('console.stop') }}</button><button v-else class="primary-button" type="button" :disabled="!canRun" @click="requestRun"><i class="pi pi-play"></i>{{ t('console.run') }}</button></div>
        </section>

        <section class="console-output-section">
          <header><div><span>OUTPUT</span><h2>{{ t('console.outputTitle') }}</h2></div><div class="console-output-tools"><input v-model="outputSearch" type="search" :placeholder="t('console.searchOutput')" /><button v-if="terminalStarted" class="icon-button" type="button" :title="t('console.focusPrompt')" @click="focusTerminalPrompt"><i class="pi pi-arrow-down"></i></button><button class="icon-button" type="button" :class="{ active: outputWrap }" :title="t('console.wrap')" @click="outputWrap = !outputWrap"><i class="pi pi-align-left"></i></button><button class="icon-button" type="button" :title="t('console.copy')" @click="copyOutput"><i class="pi pi-copy"></i></button><button class="icon-button" type="button" :title="t('console.download')" @click="downloadOutput"><i class="pi pi-download"></i></button></div></header>
          <div class="execution-meta"><span v-if="execution" class="status-pill" :class="execution.status.toLowerCase()">{{ execution.status }}</span><span v-if="execution?.exitCode != null">{{ t('console.exitCode', { code: execution.exitCode }) }}</span><span v-if="execution?.durationMs != null">{{ t('console.duration', { duration: duration(execution.durationMs) }) }}</span><span v-if="execution?.truncated" class="status-pill warning">{{ t('console.truncated') }}</span></div>
          <section v-if="execution?.verificationStatus && execution.verificationStatus !== 'NOT_REQUIRED'" class="command-verification-result" :class="verificationTone">
            <header><div><span>{{ t('console.operationVerification') }}</span><strong>{{ execution.verificationStatus }}</strong></div><time v-if="execution.verifiedAt">{{ new Date(execution.verifiedAt).toLocaleString() }}</time></header>
            <p>{{ execution.verificationSummary || t('console.verificationPending') }}</p>
            <div class="verification-actions"><button type="button" @click="setFollowup('events')"><i class="pi pi-exclamation-triangle"></i>{{ t('console.checkEvents') }}</button><button type="button" :disabled="!templateName" @click="setFollowup('logs')"><i class="pi pi-file"></i>{{ t('console.checkLogs') }}</button><button type="button" @click="setFollowup('yaml')"><i class="pi pi-code"></i>{{ t('console.checkYaml') }}</button><button v-if="sourceAnalysisId" type="button" @click="reanalyzeTarget"><i class="pi pi-sparkles"></i>{{ t('console.reanalyze') }}</button></div>
            <details v-if="execution.beforeSnapshot || execution.afterSnapshot"><summary>{{ t('console.compareSnapshots') }}</summary><div class="snapshot-compare"><article><strong>{{ t('console.before') }}</strong><pre>{{ execution.beforeSnapshot || '-' }}</pre></article><article><strong>{{ t('console.after') }}</strong><pre>{{ execution.afterSnapshot || '-' }}</pre></article></div></details>
            <details v-if="execution.rollbackCommand" class="rollback-candidate"><summary><i class="pi pi-exclamation-triangle"></i>{{ t('console.rollbackCandidate') }}</summary><p>{{ t('console.rollbackWarning') }}</p><code>{{ execution.rollbackCommand }}</code><button class="secondary-button compact-button" type="button" @click="setFollowup('rollback')">{{ t('console.reviewInWorkspace') }}</button></details>
          </section>
          <div v-if="terminalStarted" ref="terminalElement" class="terminal-xterm-host" aria-label="Interactive Kubernetes terminal"></div>
          <pre v-else ref="outputElement" class="terminal-output" :class="{ wrap: outputWrap }">{{ terminalText || t('console.emptyOutput') }}</pre>
        </section>
      </main>

      <aside class="console-utility">
        <div class="console-tabs"><button type="button" :class="{ active: activeUtility === 'cookbook' }" @click="activeUtility = 'cookbook'"><i class="pi pi-book"></i>{{ t('console.cookbook') }}</button><button type="button" :class="{ active: activeUtility === 'favorites' }" @click="activeUtility = 'favorites'"><i class="pi pi-star"></i>{{ t('console.favorites') }}</button><button type="button" :class="{ active: activeUtility === 'history' }" @click="activeUtility = 'history'"><i class="pi pi-history"></i>{{ t('console.history') }}</button></div>
        <CommandCookBook v-if="activeUtility === 'cookbook'" :unavailable-requirements="capability?.metricsApiAvailable ? [] : ['metricsServer']" @select="applyCookbookItem" />
        <div v-if="activeUtility === 'favorites'" class="utility-list"><div v-if="!favorites.length" class="utility-empty">{{ t('console.noFavorites') }}</div><article v-for="item in favorites" :key="item.id"><button class="utility-main" type="button" @click="loadFavorite(item)"><strong>{{ item.name }}</strong><code>{{ item.command }}</code><small>{{ item.namespace || t('console.clusterScope') }}<span v-if="item.shared"> · {{ t('console.shared') }}</span></small></button><button class="icon-button" type="button" :title="t('console.deleteFavorite')" @click="removeFavorite(item)"><i class="pi pi-trash"></i></button></article></div>
        <div v-if="activeUtility === 'history'" class="utility-list"><div v-if="!history.length" class="utility-empty">{{ t('console.noHistory') }}</div><button v-for="item in history" :key="item.id" class="history-item" type="button" @click="loadHistory(item)"><span><strong>{{ item.status }}</strong><small>{{ new Date(item.createdAt).toLocaleString() }}</small></span><code>{{ item.command }}</code></button></div>
      </aside>
    </div>

    <div v-if="showConfirmation && validation" class="modal-backdrop" @click.self="showConfirmation = false">
      <section class="modal-panel command-confirm-modal" role="dialog" aria-modal="true">
        <header class="modal-header"><div><h2>{{ t('console.confirmTitle') }}</h2><p>{{ t('console.confirmDescription') }}</p></div><button class="icon-button" type="button" :title="t('common.close')" @click="showConfirmation = false"><i class="pi pi-times"></i></button></header>
        <div class="command-confirm-body"><span class="status-pill" :class="safetyTone">{{ validation.safety }}</span><dl><div><dt>{{ t('console.target') }}</dt><dd>{{ validation.targetSummary }}</dd></div><div><dt>{{ t('console.namespace') }}</dt><dd>{{ validation.namespace || t('console.clusterScope') }}</dd></div></dl><pre>{{ validation.normalizedCommand }}</pre><p>{{ t('console.confirmWarning') }}</p><div class="modal-actions"><button class="secondary-button" type="button" @click="showConfirmation = false">{{ t('common.close') }}</button><button class="danger-button" type="button" @click="run(true)"><i class="pi pi-play"></i>{{ t('console.confirmRun') }}</button></div></div>
      </section>
    </div>
  </section>
</template>
