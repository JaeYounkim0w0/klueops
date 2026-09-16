import { afterEach, describe, expect, it, vi } from 'vitest';

import { AiChatStreamError, streamAiChatMessage } from '@/api/client';

describe('AI chat SSE transport', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('emits deltas only after receiving an explicit completion event', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      'event: status\ndata: accepted\n\nevent: heartbeat\ndata: keep-alive\n\nevent: delta\ndata: first\n\nevent: heartbeat\ndata: keep-alive\n\nevent: delta\ndata: second\n\nevent: done\ndata: [DONE]\n\n',
      { status: 200, headers: { 'Content-Type': 'text/event-stream' } }
    )));
    const deltas: string[] = [];
    const heartbeats: string[] = [];

    await streamAiChatMessage('conversation-1', { message: 'hello' }, (delta) => deltas.push(delta), undefined,
      () => heartbeats.push('alive'));

    expect(deltas).toEqual(['first', 'second']);
    expect(heartbeats).toEqual(['alive', 'alive', 'alive']);
  });

  it('marks an interrupted response as partial and never treats EOF as success', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      'event: delta\ndata: incomplete answer\n\n',
      { status: 200, headers: { 'Content-Type': 'text/event-stream' } }
    )));
    const deltas: string[] = [];

    const request = streamAiChatMessage('conversation-1', { message: 'hello' }, (delta) => deltas.push(delta));

    await expect(request).rejects.toEqual(expect.objectContaining<Partial<AiChatStreamError>>({
      name: 'AiChatStreamError',
      partial: true
    }));
    expect(deltas).toEqual(['incomplete answer']);
  });
});
