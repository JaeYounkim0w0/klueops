import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/** source 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function source(path: string) {
  return readFileSync(resolve(process.cwd(), path), 'utf8');
}

const dashboard = source('src/views/DashboardView.vue');
const incidentDetail = source('src/views/IncidentDetailView.vue');
const policies = source('src/views/PoliciesView.vue');
const settings = source('src/views/OperationsSettingsView.vue');
const app = source('src/App.vue');
const analysis = source('src/views/AnalysisView.vue');
const analysisRuntime = source('src/components/analysis/AnalysisRuntimePanel.vue');
const analysisSummary = source('src/components/analysis/AnalysisSummaryOverview.vue');
const triage = source('src/views/TriageWorkbenchView.vue');
const feedback = source('src/components/AnalysisFeedbackPanel.vue');
const client = source('src/api/client.ts');
const reliability = source('src/views/OperationsReliabilityView.vue');
const fleet = source('src/views/FleetCommandCenterView.vue');
const router = source('src/router/index.ts');
const operationsFeed = source('src/stores/operationsFeed.ts');

describe('AIOps control plane UX contract', () => {
  it('starts with priority and trust instead of raw Kubernetes data', () => {
    expect(dashboard).toContain('지금 확인할 항목');
    expect(dashboard).toContain('CLUSTER HEALTH');
    expect(dashboard).toContain('AI TRUST');
    expect(dashboard).toContain('CONFIGURATION POSTURE');
  });

  it('supports beginner and expert incident investigation flows', () => {
    expect(incidentDetail).toContain("mode = ref<'beginner' | 'expert'>");
    expect(incidentDetail).toContain("'evidence'");
    expect(incidentDetail).toContain("'timeline'");
    expect(incidentDetail).toContain("'actions'");
    expect(incidentDetail).toContain('verificationCommand');
    expect(incidentDetail).toContain('영향·변경');
    expect(incidentDetail).toContain('intelligence.confidence');
    expect(incidentDetail).toContain('intelligence.correlation');
    expect(incidentDetail).toContain('changeCandidates');
    expect(incidentDetail).toContain('verificationPlan');
  });

  it('keeps evidence gaps distinct from passing policy results', () => {
    expect(policies).toContain("value=\"NOT_APPLICABLE\">근거 부족");
    expect(policies).toContain("item.result === 'NOT_APPLICABLE' ? '근거 부족'");
    expect(policies).toContain('전체 YAML이나 Secret 값이 아닌 안전 summary');
  });

  it('previews retention cleanup and preserves the latest cluster snapshot boundary', () => {
    expect(settings).toContain('정리 대상 미리보기');
    expect(settings).toContain('최신 동기화 스냅샷');
    expect(settings).toContain('preview.eventSnapshots');
    expect(settings).toContain('preview.jobs');
  });

  it('exposes watch health and repeatable analysis certification to operators', () => {
    expect(settings).toContain('Kubernetes Watch');
    expect(settings).toContain('restartWatch');
    expect(settings).toContain('AI Analysis 회귀 인증');
    expect(settings).toContain('runAnalysisRegression');
    expect(settings).toContain('item.failures');
  });

  it('exposes notifications globally and analysis feedback at the result boundary', () => {
    expect(app).toContain('<NotificationCenter />');
    expect(analysis).toContain('<AnalysisFeedbackPanel');
  });

  it('provides persona-aware real-time triage and direct investigation paths', () => {
    expect(app).toContain('to="/triage"');
    expect(triage).toContain("mode = ref<'BEGINNER' | 'EXPERT'>");
    expect(triage).toContain('<div class="triage-header-actions">');
    expect(triage).toContain('<div class="triage-mode-switch" role="group"');
    expect(triage).toContain('getTriageQueue');
    expect(triage).toContain('updateTriageState');
    expect(triage).toContain("path: '/analysis'");
    expect(triage).toContain("router.push(`/incidents/");
  });

  it('records ground truth and exposes calibration and scorecard contracts', () => {
    expect(feedback).toContain('actualRootCause');
    expect(feedback).toContain('actualResolution');
    expect(feedback).toContain('confidenceExpectation');
    expect(client).toContain('/api/operations/ai-calibration');
    expect(client).toContain('/api/operations/scorecard');
  });

  it('allows operators to pause and resume resilient watches', () => {
    expect(settings).toContain('toggleWatch');
    expect(settings).toContain('watch.nextRetryAt');
    expect(settings).toContain('watch.lastHeartbeatAt');
    expect(client).toContain('/pause');
    expect(client).toContain('/resume');
  });

  it('closes the signal-to-action loop without hiding evidence limits', () => {
    expect(app).toContain('to="/settings/reliability"');
    expect(reliability).toContain('Watch 연속성');
    expect(reliability).toContain('유지보수·노이즈 정책');
    expect(reliability).toContain('AI 품질 승격 기준');
    expect(reliability).toContain('minimumGroundTruthSamples');
    expect(operationsFeed).toContain("new EventSource('/api/operations/events')");
    expect(incidentDetail).toContain('CLOSED-LOOP VERIFICATION');
    expect(incidentDetail).toContain('generateIncidentPostmortem');
    expect(client).toContain('/remediation-observations');
    expect(client).toContain('/api/operations/ai-release-gates');
  });

  it('falls back to operator-controlled polling without hiding the collection mode', () => {
    expect(reliability).toContain("state === 'POLLING'");
    expect(reliability).toContain("state === 'RECOVERING'");
    expect(reliability).toContain('restartCollector');
    expect(reliability).toContain('toggleCollector');
    expect(client).toContain('/api/operations/watch/clusters/');
  });

  it('provides a cross-cluster command center, handoff and safe validation lab', () => {
    expect(app).toContain('to="/operations/fleet"');
    expect(router).toContain("path: '/operations/fleet'");
    expect(fleet).toContain("type Persona = 'beginner' | 'expert'");
    expect(fleet).toContain('SHIFT HANDOFF');
    expect(fleet).toContain('전체 클러스터 우선순위');
    expect(fleet).toContain('분석 품질 Release Gate');
    expect(fleet).toContain('SAFE LIVE VALIDATION');
    expect(fleet).toContain('신뢰도 추세');
    expect(fleet).toContain('savedViews');
    expect(fleet).toContain('validation.mode');
    expect(client).toContain('/api/operations/fleet-queue');
    expect(client).toContain('/api/operations/shift-briefing');
    expect(client).toContain('/api/operations/validation-lab/runs');
    expect(client).toContain('/api/operations/validation-lab/live/preview');
    expect(client).toContain('/api/operations/validation-lab/benchmarks');
    expect(client).toContain('/api/operations/reliability-trend');
    expect(incidentDetail).toContain('OUTCOME LEARNING');
    expect(client).toContain('/remediation-learning');
  });

  it('shows incremental AI reuse as an explicit runtime fact', () => {
    expect(analysis).toContain('selectedIncrementalAnalysis');
    expect(analysisSummary).toContain('reusedSections');
    expect(analysisRuntime).toContain('cache hit');
  });
});
