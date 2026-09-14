import { afterEach, describe, expect, it, vi } from 'vitest';

import { ResourceLogStreamError, streamClusterResourceLogs } from '@/api/client';

describe('cluster resource log SSE transport', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ignores heartbeat and emits structured log lines until done', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      'event: heartbeat\ndata: keep-alive\n\n'
      + 'event: log\ndata: {"podName":"api-1","containerName":"app","line":"hello","observedAt":"2026-09-03T00:00:00Z"}\n\n'
      + 'event: done\ndata: {"reason":"COMPLETED","lineCount":1,"durationMs":20}\n\n',
      { status: 200, headers: { 'Content-Type': 'text/event-stream' } }
    )));
    const lines: string[] = [];

    const result = await streamClusterResourceLogs({
      clusterId: 'cluster-1', namespace: 'default', resourceType: 'Deployment', resourceName: 'api',
      podName: 'api-1', containerName: 'app', tailLines: 100
    }, (line) => lines.push(line.line));

    expect(lines).toEqual(['hello']);
    expect(result).toEqual({ reason: 'COMPLETED', lineCount: 1, durationMs: 20 });
  });

  it('reports an interrupted stream after preserving received lines', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      'event: log\ndata: {"podName":"api-1","containerName":"app","line":"partial","observedAt":"2026-09-03T00:00:00Z"}\n\n',
      { status: 200, headers: { 'Content-Type': 'text/event-stream' } }
    )));
    const lines: string[] = [];

    const request = streamClusterResourceLogs({
      clusterId: 'cluster-1', namespace: 'default', resourceType: 'Deployment', resourceName: 'api',
      podName: 'api-1', containerName: 'app', tailLines: 100
    }, (line) => lines.push(line.line));

    await expect(request).rejects.toEqual(expect.objectContaining<Partial<ResourceLogStreamError>>({
      name: 'ResourceLogStreamError', partial: true
    }));
    expect(lines).toEqual(['partial']);
  });
});
