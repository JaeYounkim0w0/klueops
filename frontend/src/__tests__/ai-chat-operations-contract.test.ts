import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const viewSource = readFileSync(new URL('../views/AiChatView.vue', import.meta.url), 'utf8');
const apiSource = readFileSync(new URL('../api/client.ts', import.meta.url), 'utf8');

describe('AI chat operations contract', () => {
  it('provides explicit general and live-cluster consultation modes', () => {
    expect(viewSource).toContain("chatMode === 'GENERAL'");
    expect(viewSource).toContain("chatMode === 'CLUSTER'");
    expect(viewSource).toContain('resourceType');
    expect(viewSource).toContain('includeLogs');
    expect(apiSource).toContain("mode: 'GENERAL' | 'CLUSTER'");
  });

  it('supports archive, favorite, rename, and permanent deletion', () => {
    expect(viewSource).toContain('toggleFavorite');
    expect(viewSource).toContain('toggleArchive');
    expect(viewSource).toContain('openRenameDialog');
    expect(viewSource).toContain('deleteConversation');
    expect(apiSource).toContain('updateConversation:');
    expect(apiSource).toContain('deleteConversation:');
  });

  it('keeps selected-conversation actions visible without opening the row menu', () => {
    expect(viewSource).toContain('class="chat-conversation-actions"');
    expect(viewSource).toContain('@click="toggleFavorite(activeConversation)"');
    expect(viewSource).toContain('@click="openRenameDialog(activeConversation)"');
    expect(viewSource).toContain('@click="toggleArchive(activeConversation)"');
    expect(viewSource).toContain('@click="openDeleteDialog(activeConversation)"');
  });

  it('loads cluster namespaces instead of relying on an unbounded text field', () => {
    expect(viewSource).toContain('api.listNamespaces');
    expect(viewSource).toContain('availableNamespaces');
    expect(viewSource).toContain('handleNamespaceChange');
  });

  it('treats cluster and namespace selectors as message context without creating duplicate conversations', () => {
    const namespaceHandler = viewSource.match(/async function handleNamespaceChange\(\) \{([\s\S]*?)\n\}/)?.[1] ?? '';
    const clusterHandler = viewSource.match(/async function handleClusterChange\(\) \{([\s\S]*?)\n\}/)?.[1] ?? '';

    expect(namespaceHandler).not.toContain('createConversation');
    expect(clusterHandler).not.toContain('createConversation');
  });

  it('allows another conversation to be inspected while one response is streaming', () => {
    expect(viewSource).toContain("const streamingConversationId = ref('')");
    expect(viewSource).not.toContain(':disabled="streaming && conversation.id !== activeConversationId"');
    expect(viewSource).not.toContain("if (streaming.value && conversationId !== activeConversationId.value) return;");
  });

  it('keeps SSE streaming inside the authenticated BFF session', () => {
    expect(apiSource).toContain("credentials: 'same-origin'");
    expect(apiSource).toContain("readCookie('XSRF-TOKEN')");
    expect(apiSource).toContain("'X-XSRF-TOKEN'");
    expect(apiSource).toContain("if (!completed) throw new AiChatStreamError");
    expect(viewSource).toContain('error instanceof AiChatStreamError');
    expect(viewSource).toContain('onUnmounted(() => abortController.value?.abort())');
    expect(viewSource).toContain('const conversationId = activeConversationId.value');
  });

  it('renders persisted interrupted responses without discarding the user question', () => {
    expect(viewSource).toContain('errorCode?: string');
    expect(viewSource).toContain('message.errorCode');
    expect(viewSource).toContain('streamingConversationId === activeConversationId');
  });

  it('restores evidence for every assistant answer with one conversation-scoped request', () => {
    expect(apiSource).toContain('listConversationContextReferences');
    expect(viewSource).toContain('referencesByMessage');
  });
});
