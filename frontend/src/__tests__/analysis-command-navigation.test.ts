import { ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { useAnalysisCommandNavigation } from '@/composables/useAnalysisCommandNavigation';

describe('analysis command navigation', () => {
  it('opens the console with a normalized command and source analysis context', () => {
    const push = vi.fn();
    const selectedAnalysis = ref({ id: 'analysis-1', clusterId: 'cluster-1', namespace: 'payments' } as never);
    const { openCommandConsole } = useAnalysisCommandNavigation({
      router: { push } as never,
      route: { fullPath: '/analysis?clusterId=cluster-1' } as never,
      selectedAnalysis,
      selectedClusterId: ref('cluster-2'),
      selectedNamespace: ref('default'),
      runFeedback: ref(null)
    });

    openCommandConsole('kubectl get events -n ${namespace}');

    expect(push).toHaveBeenCalledWith(expect.objectContaining({
      name: 'cluster-console',
      params: { clusterId: 'cluster-1' },
      query: expect.objectContaining({
        namespace: 'payments',
        command: 'kubectl get events -n payments',
        analysisId: 'analysis-1'
      })
    }));
  });
});
