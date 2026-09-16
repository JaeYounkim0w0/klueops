import type { Ref } from 'vue';
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router';
import type { AnalysisResponse } from '@/api/client';

type RunFeedback = { tone: 'success' | 'error' | 'info'; message: string; detail?: string } | null;

/** useAnalysisCommandNavigation 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function useAnalysisCommandNavigation(options: {
  router: Router;
  route: RouteLocationNormalizedLoaded;
  selectedAnalysis: Ref<AnalysisResponse | null>;
  selectedClusterId: Ref<string>;
  selectedNamespace: Ref<string>;
  runFeedback: Ref<RunFeedback>;
}) {
  /** normalizeCommand 처리 데이터를 화면 또는 API 표현으로 변환한다. */
  function normalizeCommand(command: string, namespace?: string) {
    return command.split('${namespace}').join(namespace?.trim() || 'default');
  }

  /** copyCommand 처리에 필요한 화면 또는 업무 로직을 수행한다. */
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

  /** openCommandConsole 처리에 필요한 화면 또는 업무 로직을 수행한다. */
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
