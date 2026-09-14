<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  AiChatStreamError, api, streamAiChatMessage,
  type AiChatContextReferenceResponse, type AiChatConversationResponse, type AiChatMessageResponse, type ClusterResponse,
  type KubernetesNamespaceResponse, type SendMessageRequest
} from '@/api/client';

type ChatMode = 'GENERAL' | 'CLUSTER';
type ConversationDialog = 'rename' | 'delete';
interface UiMessage { id: string; role: 'USER' | 'ASSISTANT' | 'SYSTEM'; content: string; model?: string; latencyMs?: number; firstTokenLatencyMs?: number; totalLatencyMs?: number; contextChars?: number; errorCode?: string; errorMessage?: string; streaming?: boolean; references?: AiChatContextReferenceResponse[]; }

const { t } = useI18n();
const conversations = ref<AiChatConversationResponse[]>([]);
const activeConversationId = ref('');
const messages = ref<UiMessage[]>([]);
const clusters = ref<ClusterResponse[]>([]);
const availableNamespaces = ref<KubernetesNamespaceResponse[]>([]);
const chatMode = ref<ChatMode>('GENERAL');
const showingArchived = ref(false);
const selectedClusterId = ref('');
const selectedNamespace = ref('');
const resourceType = ref('Pod');
const resourceName = ref('');
const includeRecentEvents = ref(true);
const includeLogs = ref(true);
const logLineLimit = ref(80);
const draft = ref('');
const loading = ref(false);
const listLoading = ref(false);
const streaming = ref(false);
const streamingConversationId = ref('');
const errorMessage = ref('');
const chatLog = ref<HTMLElement | null>(null);
const abortController = ref<AbortController | null>(null);
const dialog = ref<ConversationDialog | null>(null);
const dialogConversation = ref<AiChatConversationResponse | null>(null);
const renameTitle = ref('');
const dialogBusy = ref(false);

const activeConversation = computed(() => conversations.value.find((item) => item.id === activeConversationId.value));
const canSend = computed(() => draft.value.trim().length > 0 && !streaming.value
  && (chatMode.value === 'GENERAL' || Boolean(selectedClusterId.value)));
const selectedClusterName = computed(() => clusters.value.find((item) => item.id === selectedClusterId.value)?.name ?? '');

onMounted(async () => {
  await Promise.all([loadConversations(), loadClusters()]);
  if (conversations.value.length === 0) await createConversation('GENERAL');
  else await selectConversation(conversations.value[0].id);
});

onUnmounted(() => abortController.value?.abort());

watch(selectedClusterId, async (clusterId) => {
  availableNamespaces.value = [];
  if (!clusterId) { selectedNamespace.value = ''; return; }
  try {
    availableNamespaces.value = await api.listNamespaces(clusterId);
    if (selectedNamespace.value && !availableNamespaces.value.some((item) => item.name === selectedNamespace.value)) selectedNamespace.value = '';
  } catch (error) { errorMessage.value = errorToMessage(error); }
});

async function loadConversations() {
  listLoading.value = true;
  try { conversations.value = await api.listConversations(showingArchived.value); }
  finally { listLoading.value = false; }
}

async function loadClusters() {
  try { clusters.value = await api.listClusters(); } catch { clusters.value = []; }
}

async function setConversationList(archived: boolean) {
  if (streaming.value) return;
  showingArchived.value = archived;
  activeConversationId.value = '';
  messages.value = [];
  await loadConversations();
  if (conversations.value[0]) await selectConversation(conversations.value[0].id);
}

async function createConversation(mode: ChatMode = chatMode.value) {
  if (streaming.value) return;
  if (mode === 'CLUSTER' && !selectedClusterId.value) {
    selectedClusterId.value = clusters.value[0]?.id ?? '';
    if (!selectedClusterId.value) {
      chatMode.value = activeConversation.value?.mode ?? 'GENERAL';
      errorMessage.value = t('chat.clusterRequired');
      return;
    }
  }
  showingArchived.value = false;
  const created = await api.createConversation({
    title: mode === 'GENERAL' ? t('chat.generalNewTitle') : t('chat.clusterNewTitle'), mode,
    clusterId: mode === 'CLUSTER' ? selectedClusterId.value : undefined,
    namespace: mode === 'CLUSTER' ? selectedNamespace.value || undefined : undefined
  });
  await loadConversations();
  if (!conversations.value.some((item) => item.id === created.id)) conversations.value.unshift(created);
  await selectConversation(created.id);
}

async function changeMode(mode: ChatMode) {
  if (streaming.value) return;
  if (chatMode.value === mode && activeConversation.value?.mode === mode) return;
  const previousMode = chatMode.value;
  chatMode.value = mode;
  errorMessage.value = '';
  try {
    await createConversation(mode);
  } catch (error) {
    chatMode.value = activeConversation.value?.mode ?? previousMode;
    errorMessage.value = errorToMessage(error);
  }
}

async function handleClusterChange() {
  if (streaming.value) return;
  selectedNamespace.value = '';
  resourceName.value = '';
}

async function handleNamespaceChange() {
  if (streaming.value || chatMode.value !== 'CLUSTER') return;
  resourceName.value = '';
}

async function selectConversation(conversationId: string) {
  const selected = conversations.value.find((item) => item.id === conversationId);
  activeConversationId.value = conversationId;
  if (selected) {
    chatMode.value = selected.mode;
    selectedClusterId.value = selected.clusterId ?? '';
    selectedNamespace.value = selected.namespace ?? '';
  }
  loading.value = true;
  errorMessage.value = '';
  try { await refreshMessagesWithLatestEvidence(conversationId); await scrollToBottom(); }
  catch (error) { errorMessage.value = errorToMessage(error); }
  finally { loading.value = false; }
}

async function sendMessage() {
  const text = draft.value.trim();
  if (!text || !activeConversationId.value || !canSend.value) return;
  errorMessage.value = '';
  draft.value = '';
  const userMessage: UiMessage = { id: crypto.randomUUID(), role: 'USER', content: text };
  const assistantMessage: UiMessage = { id: crypto.randomUUID(), role: 'ASSISTANT', content: '', streaming: true };
  messages.value.push(userMessage, assistantMessage);
  await scrollToBottom();

  const request: SendMessageRequest = { message: text, contextSelection: chatMode.value === 'CLUSTER' ? {
    clusterId: selectedClusterId.value, namespace: selectedNamespace.value || undefined,
    includeRecentEvents: includeRecentEvents.value,
    resourceType: resourceName.value.trim() ? resourceType.value : undefined,
    resourceName: resourceName.value.trim() || undefined,
    includeLogs: includeLogs.value, logLineLimit: logLineLimit.value
  } : undefined };
  const conversationId = activeConversationId.value;

  streaming.value = true;
  streamingConversationId.value = conversationId;
  abortController.value = new AbortController();
  try {
    await streamAiChatMessage(conversationId, request, async (delta) => { assistantMessage.content += delta; await scrollToBottom(); }, abortController.value.signal);
  } catch (error) {
    if (abortController.value?.signal.aborted) { assistantMessage.content += `\n\n${t('chat.stopped')}`; return; }
    if (error instanceof AiChatStreamError) {
      errorMessage.value = error.message;
      assistantMessage.errorCode = 'STREAM_INTERRUPTED';
      assistantMessage.errorMessage = error.message;
      if (error.partial) assistantMessage.content += `\n\n${t('chat.stopped')}`;
      else assistantMessage.content = t('chat.responseFailed');
    } else {
      await sendMessageWithoutStreaming(conversationId, request, assistantMessage);
    }
  } finally {
    const wasAborted = abortController.value?.signal.aborted;
    assistantMessage.streaming = false;
    streaming.value = false;
    abortController.value = null;
    streamingConversationId.value = '';
    await loadConversations();
    if (!wasAborted && activeConversationId.value === conversationId) await refreshMessagesWithLatestEvidence(conversationId);
    await scrollToBottom();
  }
}

async function refreshMessagesWithLatestEvidence(conversationId: string) {
  if (!conversationId) return;
  const persisted = (await api.listMessages(conversationId)).map(toUiMessage);
  try {
    const references = await api.listConversationContextReferences(conversationId);
    const referencesByMessage = new Map<string, AiChatContextReferenceResponse[]>();
    references.forEach((reference) => referencesByMessage.set(reference.messageId,
      [...(referencesByMessage.get(reference.messageId) ?? []), reference]));
    persisted.forEach((message) => { if (message.role === 'ASSISTANT') message.references = referencesByMessage.get(message.id) ?? []; });
  } catch { persisted.forEach((message) => { if (message.role === 'ASSISTANT') message.references = []; }); }
  messages.value = persisted;
}

async function sendMessageWithoutStreaming(conversationId: string, request: SendMessageRequest, assistantMessage: UiMessage) {
  try {
    const response = await api.sendMessage(conversationId, request);
    assistantMessage.content = response.assistantMessage.content;
    assistantMessage.model = response.assistantMessage.model;
    assistantMessage.latencyMs = response.assistantMessage.latencyMs;
    assistantMessage.firstTokenLatencyMs = response.assistantMessage.firstTokenLatencyMs;
    assistantMessage.totalLatencyMs = response.assistantMessage.totalLatencyMs;
    assistantMessage.contextChars = response.assistantMessage.contextChars;
  } catch (error) { assistantMessage.content = t('chat.responseFailed'); errorMessage.value = errorToMessage(error); }
}

async function toggleFavorite(conversation: AiChatConversationResponse) {
  if (streaming.value || !await updateConversation(conversation, { favorite: !conversation.favorite })) return;
  conversations.value = sortConversations(conversations.value);
}
async function toggleArchive(conversation: AiChatConversationResponse, event?: Event) {
  if (streaming.value) return;
  closeConversationMenu(event);
  if (!await updateConversation(conversation, { archived: !conversation.archivedAt })) return;
  if (conversation.id === activeConversationId.value) { activeConversationId.value = ''; messages.value = []; }
  await loadConversations();
  if (conversations.value[0]) await selectConversation(conversations.value[0].id);
}

async function updateConversation(conversation: AiChatConversationResponse, changes: { title?: string; favorite?: boolean; archived?: boolean }): Promise<boolean> {
  try {
    const updated = await api.updateConversation(conversation.id, changes);
    conversations.value = conversations.value.map((item) => item.id === updated.id ? updated : item);
    return true;
  } catch (error) { errorMessage.value = errorToMessage(error); return false; }
}

function sortConversations(items: AiChatConversationResponse[]) {
  return [...items].sort((left, right) => Number(right.favorite) - Number(left.favorite)
    || String(right.updatedAt ?? '').localeCompare(String(left.updatedAt ?? '')));
}

function openRenameDialog(conversation: AiChatConversationResponse, event?: Event) {
  if (!streaming.value) { closeConversationMenu(event); dialog.value = 'rename'; dialogConversation.value = conversation; renameTitle.value = conversation.title; }
}
function openDeleteDialog(conversation: AiChatConversationResponse, event?: Event) {
  if (!streaming.value) { closeConversationMenu(event); dialog.value = 'delete'; dialogConversation.value = conversation; }
}
function closeConversationMenu(event?: Event) {
  (event?.currentTarget as HTMLElement | null)?.closest('details')?.removeAttribute('open');
}
function closeDialog() { if (!dialogBusy.value) { dialog.value = null; dialogConversation.value = null; } }
async function confirmDialog() {
  if (!dialogConversation.value || !dialog.value) return;
  dialogBusy.value = true;
  try {
    let succeeded = false;
    if (dialog.value === 'rename') { const title = renameTitle.value.trim(); if (!title) return; succeeded = await updateConversation(dialogConversation.value, { title }); }
    else succeeded = await deleteConversation(dialogConversation.value);
    if (!succeeded) return;
    dialog.value = null; dialogConversation.value = null;
  } finally { dialogBusy.value = false; }
}
async function deleteConversation(conversation: AiChatConversationResponse): Promise<boolean> {
  try {
    await api.deleteConversation(conversation.id);
    conversations.value = conversations.value.filter((item) => item.id !== conversation.id);
    if (activeConversationId.value === conversation.id) {
      activeConversationId.value = ''; messages.value = [];
      if (conversations.value[0]) await selectConversation(conversations.value[0].id);
    }
    return true;
  } catch (error) { errorMessage.value = errorToMessage(error); return false; }
}

function stopStreaming() { abortController.value?.abort(); }
function toUiMessage(message: AiChatMessageResponse): UiMessage { return { id: message.id, role: message.role, content: message.content, model: message.model, latencyMs: message.latencyMs, firstTokenLatencyMs: message.firstTokenLatencyMs, totalLatencyMs: message.totalLatencyMs, contextChars: message.contextChars, errorCode: message.errorCode, errorMessage: message.errorMessage }; }
function formatDuration(milliseconds?: number): string { if (milliseconds == null) return '-'; return milliseconds < 1000 ? `${milliseconds}ms` : `${(milliseconds / 1000).toFixed(milliseconds < 10000 ? 1 : 0)}s`; }
function formatCount(value?: number): string { return value == null ? '-' : new Intl.NumberFormat().format(value); }
async function scrollToBottom() { await nextTick(); if (chatLog.value) chatLog.value.scrollTop = chatLog.value.scrollHeight; }
function errorToMessage(error: unknown): string { return error instanceof Error ? error.message : t('chat.requestFailed'); }
</script>

<template>
  <section class="chat-page" :class="{ 'is-streaming': streaming }">
    <aside class="chat-sidebar">
      <div class="panel-header">
        <div><h2>AI Chat</h2><p>{{ $t('chat.sidebarDescription') }}</p></div>
        <button class="icon-button" :title="$t('chat.newConversation')" type="button" :disabled="streaming" @click="createConversation()"><i class="pi pi-plus"></i></button>
      </div>
      <div class="conversation-tabs" role="tablist" :aria-label="$t('chat.conversationList')">
        <button type="button" role="tab" :disabled="streaming" :aria-selected="!showingArchived" @click="setConversationList(false)">{{ $t('chat.active') }}</button>
        <button type="button" role="tab" :disabled="streaming" :aria-selected="showingArchived" @click="setConversationList(true)">{{ $t('chat.archived') }}</button>
      </div>
      <div class="conversation-list">
        <div v-if="listLoading" class="conversation-list-state"><i class="pi pi-spin pi-spinner"></i></div>
        <div v-else-if="conversations.length === 0" class="conversation-list-state">{{ showingArchived ? $t('chat.noArchived') : $t('chat.noConversations') }}</div>
        <div v-for="conversation in conversations" v-else :key="conversation.id" class="conversation-row" :class="{ active: conversation.id === activeConversationId }">
          <button class="conversation-item" type="button" @click="selectConversation(conversation.id)">
            <span>{{ conversation.title }}<i v-if="streamingConversationId === conversation.id" class="pi pi-spin pi-spinner conversation-streaming" :title="$t('chat.generating')"></i></span><small><i :class="conversation.mode === 'CLUSTER' ? 'pi pi-server' : 'pi pi-comments'"></i>{{ conversation.mode === 'CLUSTER' ? conversation.namespace || $t('chat.allNamespaces') : $t('chat.general') }}</small>
          </button>
          <button class="conversation-icon-button" :disabled="streaming" :class="{ selected: conversation.favorite }" :title="conversation.favorite ? $t('chat.unfavorite') : $t('chat.favorite')" type="button" @click="toggleFavorite(conversation)"><i :class="conversation.favorite ? 'pi pi-star-fill' : 'pi pi-star'"></i></button>
          <details class="conversation-actions"><summary :title="$t('chat.moreActions')"><i class="pi pi-ellipsis-v"></i></summary><div class="conversation-action-menu">
            <button type="button" @click="openRenameDialog(conversation, $event)"><i class="pi pi-pencil"></i>{{ $t('chat.rename') }}</button>
            <button type="button" @click="toggleArchive(conversation, $event)"><i :class="conversation.archivedAt ? 'pi pi-inbox' : 'pi pi-box'"></i>{{ conversation.archivedAt ? $t('chat.restore') : $t('chat.archive') }}</button>
            <button class="danger" type="button" @click="openDeleteDialog(conversation, $event)"><i class="pi pi-trash"></i>{{ $t('chat.delete') }}</button>
          </div></details>
        </div>
      </div>
    </aside>

    <main class="chat-main">
      <header class="chat-toolbar">
        <div class="chat-heading"><h1>{{ activeConversation?.title || $t('pages.chatTitle') }}</h1><p>{{ $t('chat.description') }}</p></div>
        <div class="chat-toolbar-controls">
          <div class="chat-mode-switch" role="group" :aria-label="$t('chat.mode')">
            <button type="button" :disabled="streaming" :aria-pressed="chatMode === 'GENERAL'" @click="changeMode('GENERAL')"><i class="pi pi-comments"></i><span>{{ $t('chat.general') }}</span></button>
            <button type="button" :disabled="streaming" :aria-pressed="chatMode === 'CLUSTER'" @click="changeMode('CLUSTER')"><i class="pi pi-server"></i><span>{{ $t('chat.cluster') }}</span></button>
          </div>
          <div v-if="activeConversation" class="chat-conversation-actions" :aria-label="$t('chat.moreActions')">
            <button class="icon-button" :class="{ selected: activeConversation.favorite }" :disabled="streaming" :title="activeConversation.favorite ? $t('chat.unfavorite') : $t('chat.favorite')" type="button" @click="toggleFavorite(activeConversation)"><i :class="activeConversation.favorite ? 'pi pi-star-fill' : 'pi pi-star'"></i></button>
            <button class="icon-button" :disabled="streaming" :title="$t('chat.rename')" type="button" @click="openRenameDialog(activeConversation)"><i class="pi pi-pencil"></i></button>
            <button class="icon-button" :disabled="streaming" :title="activeConversation.archivedAt ? $t('chat.restore') : $t('chat.archive')" type="button" @click="toggleArchive(activeConversation)"><i :class="activeConversation.archivedAt ? 'pi pi-inbox' : 'pi pi-box'"></i></button>
            <button class="icon-button danger" :disabled="streaming" :title="$t('chat.delete')" type="button" @click="openDeleteDialog(activeConversation)"><i class="pi pi-trash"></i></button>
          </div>
        </div>
        <div v-if="chatMode === 'CLUSTER'" class="chat-context">
          <label><span>{{ $t('chat.clusterLabel') }}</span><select v-model="selectedClusterId" :disabled="streaming" @change="handleClusterChange"><option disabled value="">{{ $t('chat.selectCluster') }}</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label>
          <label><span>Namespace</span><select v-model="selectedNamespace" :disabled="!selectedClusterId || streaming" @change="handleNamespaceChange"><option value="">{{ $t('chat.allNamespaces') }}</option><option v-for="namespace in availableNamespaces" :key="namespace.name" :value="namespace.name">{{ namespace.name }}</option></select></label>
          <details class="chat-context-advanced" :class="{ disabled: streaming }"><summary><i class="pi pi-sliders-h"></i>{{ $t('chat.targetResource') }}</summary><div class="chat-context-advanced-fields">
            <label><span>{{ $t('chat.resourceType') }}</span><select v-model="resourceType"><option>Pod</option><option>Deployment</option><option>StatefulSet</option><option>DaemonSet</option><option>Service</option><option>Ingress</option><option>ConfigMap</option><option>Secret</option><option>PersistentVolumeClaim</option></select></label>
            <label><span>{{ $t('chat.resourceName') }}</span><input v-model="resourceName" :placeholder="$t('chat.resourceNamePlaceholder')" /></label>
            <label class="toggle-row"><input v-model="includeRecentEvents" type="checkbox" /><span>{{ $t('chat.includeEvents') }}</span></label>
            <label class="toggle-row"><input v-model="includeLogs" type="checkbox" /><span>{{ $t('chat.includeLogs') }}</span></label>
            <label><span>{{ $t('chat.logLines') }}</span><select v-model.number="logLineLimit" :disabled="!includeLogs"><option :value="40">40</option><option :value="80">80</option><option :value="120">120</option><option :value="200">200</option></select></label>
          </div></details>
        </div>
        <div v-else class="chat-mode-notice"><i class="pi pi-shield"></i><span>{{ $t('chat.generalPrivacy') }}</span></div>
      </header>

      <div v-if="errorMessage" class="inline-error"><i class="pi pi-exclamation-triangle"></i><span>{{ errorMessage }}</span></div>
      <div ref="chatLog" class="chat-log" aria-live="polite">
        <div v-if="loading" class="empty-state compact"><i class="pi pi-spin pi-spinner"></i><span>{{ $t('chat.loadingMessages') }}</span></div>
        <div v-else-if="!activeConversationId" class="chat-empty"><i class="pi pi-comments"></i><strong>{{ $t('chat.selectOrCreate') }}</strong></div>
        <div v-else-if="messages.length === 0" class="chat-empty"><i :class="chatMode === 'CLUSTER' ? 'pi pi-server' : 'pi pi-comments'"></i><strong>{{ chatMode === 'CLUSTER' ? $t('chat.clusterEmptyTitle') : $t('chat.generalEmptyTitle') }}</strong><span>{{ chatMode === 'CLUSTER' ? $t('chat.clusterExample', { cluster: selectedClusterName || $t('chat.selectedCluster') }) : $t('chat.generalExample') }}</span></div>
        <article v-for="message in messages" v-else :key="message.id" class="chat-message" :class="message.role.toLowerCase()">
          <div class="message-avatar"><i :class="message.role === 'USER' ? 'pi pi-user' : 'pi pi-sparkles'"></i></div>
          <div class="message-body"><div class="message-meta"><strong>{{ message.role === 'USER' ? $t('chat.user') : 'AI Assistant' }}</strong><span v-if="message.model">{{ message.model }}</span><span v-if="message.streaming" class="streaming-dot">streaming</span><span v-if="message.errorCode" class="message-error-state"><i class="pi pi-exclamation-circle"></i>{{ $t('chat.interrupted') }}</span></div><p>{{ message.content || (message.errorCode ? $t('chat.responseFailed') : $t('chat.generating')) }}</p><div v-if="message.role === 'ASSISTANT' && (message.latencyMs != null || message.totalLatencyMs != null || message.contextChars != null)" class="message-runtime" :aria-label="$t('chat.runtime')"><span v-if="message.firstTokenLatencyMs != null" :title="$t('chat.firstTokenHelp')"><i class="pi pi-bolt"></i>{{ $t('chat.firstToken') }} {{ formatDuration(message.firstTokenLatencyMs) }}</span><span v-if="message.latencyMs != null" :title="$t('chat.llmTimeHelp')"><i class="pi pi-sparkles"></i>{{ $t('chat.llmTime') }} {{ formatDuration(message.latencyMs) }}</span><span v-if="message.totalLatencyMs != null" :title="$t('chat.totalTimeHelp')"><i class="pi pi-clock"></i>{{ $t('chat.totalTime') }} {{ formatDuration(message.totalLatencyMs) }}</span><span v-if="message.contextChars != null" :title="$t('chat.contextSizeHelp')"><i class="pi pi-database"></i>{{ $t('chat.contextSize') }} {{ formatCount(message.contextChars) }}</span></div><div v-if="message.references?.length" class="message-evidence"><span><i class="pi pi-link"></i>{{ $t('chat.usedEvidence') }}</span><small v-for="reference in message.references" :key="reference.id" :title="`${reference.referenceType} · ${reference.createdAt ?? ''}`">{{ reference.label }}</small></div></div>
        </article>
      </div>
      <form class="chat-composer" @submit.prevent="sendMessage"><textarea v-model="draft" :placeholder="chatMode === 'CLUSTER' ? $t('chat.clusterPlaceholder') : $t('chat.generalPlaceholder')" rows="3" @keydown.meta.enter.prevent="sendMessage" @keydown.ctrl.enter.prevent="sendMessage" /><div class="composer-actions"><button v-if="streaming && streamingConversationId === activeConversationId" class="secondary-button" type="button" @click="stopStreaming"><i class="pi pi-stop-circle"></i><span>{{ $t('chat.stop') }}</span></button><button class="primary-button" :disabled="!canSend" type="submit"><i class="pi pi-send"></i><span>{{ $t('chat.send') }}</span></button></div></form>
    </main>
  </section>

  <div v-if="dialog" class="modal-backdrop" @click.self="closeDialog">
    <section class="modal-panel chat-dialog" role="dialog" aria-modal="true" aria-labelledby="chat-dialog-title">
      <header><div><h2 id="chat-dialog-title">{{ dialog === 'rename' ? $t('chat.renameTitle') : $t('chat.deleteTitle') }}</h2><p>{{ dialog === 'rename' ? $t('chat.renameDescription') : $t('chat.deleteDescription') }}</p></div><button class="icon-button" :title="$t('common.close')" type="button" @click="closeDialog"><i class="pi pi-times"></i></button></header>
      <label v-if="dialog === 'rename'" class="form-field"><span>{{ $t('chat.title') }}</span><input v-model="renameTitle" maxlength="255" autofocus @keydown.enter.prevent="confirmDialog" /></label>
      <div class="modal-actions"><button class="secondary-button" type="button" :disabled="dialogBusy" @click="closeDialog">{{ $t('common.close') }}</button><button :class="dialog === 'delete' ? 'danger-button' : 'primary-button'" type="button" :disabled="dialogBusy || (dialog === 'rename' && !renameTitle.trim())" @click="confirmDialog"><i :class="dialog === 'delete' ? 'pi pi-trash' : 'pi pi-check'"></i>{{ dialog === 'delete' ? $t('chat.delete') : $t('common.save') }}</button></div>
    </section>
  </div>
</template>
