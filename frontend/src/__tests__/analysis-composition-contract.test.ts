import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/** source 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function source(path: string) {
  return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('AI Analysis view composition', () => {
  const view = source('src/views/AnalysisView.vue');

  it('delegates bounded run, runtime and evidence panels', () => {
    expect(view).toContain("import AnalysisRunPanel from '@/components/analysis/AnalysisRunPanel.vue'");
    expect(view).toContain("import AnalysisRuntimePanel from '@/components/analysis/AnalysisRuntimePanel.vue'");
    expect(view).toContain("import AnalysisEvidencePanel from '@/components/analysis/AnalysisEvidencePanel.vue'");
    expect(view).toContain("import AnalysisCommandSafetyPanel from '@/components/analysis/AnalysisCommandSafetyPanel.vue'");
    expect(view).toContain("import AnalysisLogIntelligencePanel from '@/components/analysis/AnalysisLogIntelligencePanel.vue'");
    expect(view).toContain("import { useAnalysisOperations } from '@/composables/useAnalysisOperations'");
    expect(view).toContain('<AnalysisRunPanel');
    expect(view).toContain('<AnalysisRuntimePanel');
    expect(view).toContain('<AnalysisEvidencePanel');
    expect(view).toContain('<AnalysisCommandSafetyPanel');
    expect(view).toContain('<AnalysisLogIntelligencePanel');
  });

  it('keeps panel markup out of the route orchestration view', () => {
    expect(view).not.toContain('<section class="analysis-run-panel">');
    expect(view).not.toContain('<h3>Analysis Runtime</h3>');
    expect(view).not.toContain('class="analysis-evidence-ledger-panel"');
    expect(view).not.toContain('class="analysis-command-safety-panel"');
    expect(view).not.toContain('class="analysis-log-intelligence-panel"');
    expect(view).not.toContain('async function waitForAnalysisJob');
  });

  it('shows bounded Kubernetes collection quality inside the runtime component', () => {
    const runtime = source('src/components/analysis/AnalysisRuntimePanel.vue');
    expect(runtime).toContain("t('analysisRuntime.collectionTitle')");
    expect(runtime).toContain('collectionStages');
    expect(runtime).toContain('collectionStatusClass');
  });
});
