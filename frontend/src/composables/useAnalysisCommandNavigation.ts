import type { Ref } from 'vue';
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router';
import type { AnalysisResponse } from '@/api/client';

type RunFeedback = { tone: 'success' | 'error' | 'info'; message: string; detail?: string } | null;

export function useAnalysisCommandNavigation(options: {
  router: Router;
  route: RouteLocationNormalizedLoaded;
  selectedAnalysis: Ref<AnalysisResponse | null>;
  selectedClusterId: Ref<string>;
  selectedNamespace: Ref<string>;
  runFeedback: Ref<RunFeedback>;
}) {
  function normalizeCommand(command: string, namespace?: string) {
    return command.split('${namespace}').join(namespace?.trim() || 'default');
  }

  async function copyCommand(command?: string) {
    if (!command) return;
    const normalized = normalizeCommand(command, options.selectedNamespace.value);
    try {
      await navigator.clipboard.writeText(normalized);
      options.runFeedback.value = { tone: 'success', message: '검증 명령 복사 완료', detail: normalized };
    } catch {
      options.runFeedback.value = { tone: 'error', message: '검증 명령 복사 실패', detail: normalized };
    }
  }

  function openCommandConsole(command?: string) {
    const analysis = options.selectedAnalysis.value;
    const clusterId = analysis?.clusterId || options.selectedClusterId.value;
    if (!clusterId || !command?.trim()) return;
    const namespace = analysis?.namespace || options.selectedNamespace.value.trim();
    void options.router.push({
      name: 'cluster-console',
      params: { clusterId },
      query: {
        namespace: namespace || '__ALL__',
        command: normalizeCommand(command, namespace),
        analysisId: analysis?.id,
        returnTo: options.route.fullPath
      }
    });
  }

  return { copyCommand, openCommandConsole };
}
