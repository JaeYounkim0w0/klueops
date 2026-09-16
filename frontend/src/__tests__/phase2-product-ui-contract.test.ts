import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('Phase 2 product UI contract', () => {
  it('uses the shared dark navigation shell and top context bar', () => {
    const app = readFileSync(new URL('../App.vue', import.meta.url), 'utf8');
    const base = readFileSync(new URL('../styles/base.css', import.meta.url), 'utf8');
    const shell = readFileSync(new URL('../styles/product-shell.css', import.meta.url), 'utf8');

    expect(app).toContain('class="workspace-topbar"');
    expect(app).toContain('<TenantWorkspaceSelector />');
    expect(base).toContain('color-scheme: light');
    expect(base).toContain("input[type='search']");
    expect(base).toContain('background-color: var(--surface-card)');
    expect(shell).toContain('background: var(--shell-navy)');
    expect(shell).toContain('.sidebar.nav-open');
    expect(app).toContain("t('shell.incidentResponse')");
    expect(app).toContain("t('shell.infrastructure')");
    expect(app).toContain("t('shell.applicationDelivery')");
    expect(app).toContain("t('shell.aiOperations')");
    expect(app).toContain("t('shell.governance')");
    expect(app).toContain("t('shell.platformSettings')");
    expect(app).toContain("t('shell.personal')");
    expect(app.match(/t\('shell\.userAccess'\)/g)).toHaveLength(1);
  });

  it('presents Applications as summary, searchable list and selected detail', () => {
    const source = readFileSync(new URL('../views/ApplicationsView.vue', import.meta.url), 'utf8');
    const polling = readFileSync(new URL('../composables/useApplicationStatusPolling.ts', import.meta.url), 'utf8');

    expect(source).toContain('class="application-summary"');
    expect(source).toContain('v-model="applicationSearch"');
    expect(source).toContain('class="application-table-row"');
    expect(source).toContain('class="application-inspector"');
    expect(source).toContain('isApplicationProgressing(item.status)');
    expect(source).toContain('refreshApplicationStatuses');
    expect(polling).toContain("'DEPLOYING'");
    expect(polling).toContain('if (stopped || !shouldContinue()) return;');
  });

  it('keeps the Discover search field readable in OS dark form themes', () => {
    const discover = readFileSync(new URL('../views/ApplicationDiscoverView.vue', import.meta.url), 'utf8');
    const delivery = readFileSync(new URL('../styles/components/application-delivery.css', import.meta.url), 'utf8');

    expect(discover).toContain("const query = ref('');");
    expect(discover).not.toContain("const query = ref('nginx');");
    expect(discover).toContain('찾을 Chart를 검색하세요');
    expect(delivery).toContain('.delivery-search input {');
    expect(delivery).toContain('background: #fff');
    expect(delivery).toContain('color: #17212f');
    expect(delivery).toContain('.delivery-search input::placeholder');
  });

  it('discovers rendered Service ports and ready Gateways for HTTPRoute targets', () => {
    const wizard = readFileSync(new URL('../views/DeploymentWizardView.vue', import.meta.url), 'utf8');
    const fields = readFileSync(new URL('../components/application/HttpRouteTargetFields.vue', import.meta.url), 'utf8');

    expect(wizard).toContain('api.getDeploymentTargetOptions');
    expect(wizard).toContain('<HttpRouteTargetFields');
    expect(fields).toContain('Gateway (Namespace / Name)');
    expect(fields).toContain('spec.ports[].port');
    expect(fields).toContain('nodePort');
    expect(fields).toContain("service.httpRouteCompatibility === 'NON_HTTP'");
    expect(fields).toContain('PostgreSQL·Redis 같은 TCP 서비스');
    expect(fields).toContain('kubectl get gateway -A');
  });

  it('tracks lifecycle jobs and removes stale application detail after uninstall', () => {
    const applications = readFileSync(new URL('../views/ApplicationsView.vue', import.meta.url), 'utf8');
    const jobCenter = readFileSync(new URL('../stores/jobCenter.ts', import.meta.url), 'utf8');
    const consoleStyle = readFileSync(new URL('../styles/kubernetes-console.css', import.meta.url), 'utf8');

    expect(applications).toContain('jobs.trackJob(job)');
    expect(applications).toContain('Application을 제거했습니다.');
    expect(jobCenter).toContain('function trackJob(options: RegisterJobOptions)');
    expect(jobCenter).toContain('isRetryableJobPollError');
    expect(jobCenter).toContain('서버 상태 조회가 지연되어 자동으로 다시 확인 중입니다.');
    expect(consoleStyle).toContain('.console-status-dot');
    expect(consoleStyle).not.toContain('\n.status-dot {');
    expect(applications).toContain('배포 Helm 정보');
    expect(applications).toContain('Chart Version');
    expect(applications).toContain('IP / Host');
    expect(applications).toContain('Service Port');
    expect(applications).toContain('Node Port');
    expect(applications).toContain('isBrowsableEndpoint(endpoint)');
    expect(applications).toContain('전용 Client 또는 port-forward로 접속');
  });

  it('right-aligns the Custom Values suggestion action', () => {
    const valuesStudio = readFileSync(new URL('../views/ValuesStudioView.vue', import.meta.url), 'utf8');
    const deliveryStyle = readFileSync(new URL('../styles/components/application-delivery.css', import.meta.url), 'utf8');

    expect(valuesStudio).toContain('class="delivery-assistant-actions"');
    expect(deliveryStyle).toContain('.delivery-assistant-actions { display: flex; justify-content: flex-end;');
    expect(valuesStudio).toContain("const EMPTY_CUSTOM_VALUES = '{}\\n';");
    expect(valuesStudio).toContain('valuesYaml.value.trim()');
    expect(valuesStudio).toContain(': EMPTY_CUSTOM_VALUES');
    expect(valuesStudio).toContain('실제 Helm 렌더링을 통과한 결과만 표시합니다');
    expect(valuesStudio).toContain('assistantMessage');
    expect(valuesStudio).toContain('Helm 검증 완료');
  });

  it('uses the shared product form-control system for plain application inputs', () => {
    const formControls = readFileSync(new URL('../styles/form-controls.css', import.meta.url), 'utf8');
    const mainStyle = readFileSync(new URL('../styles/main.css', import.meta.url), 'utf8');
    const valuesForm = readFileSync(new URL('../components/application/ValuesSchemaForm.vue', import.meta.url), 'utf8');
    const tenancy = readFileSync(new URL('../views/TenantManagementView.vue', import.meta.url), 'utf8');

    expect(formControls).toContain('--control-focus: #1768e5;');
    expect(formControls).toContain('):focus-visible {');
    expect(formControls).toContain("input[type='checkbox'], input[type='radio']");
    expect(formControls).toContain('.ui-surface-card');
    expect(formControls).toContain('.ui-form-grid');
    expect(valuesForm).toContain('schema-field ui-surface-card ui-form-field');
    expect(tenancy).toContain('wide ui-form-field ui-form-field-wide');
    expect(mainStyle).toContain('.schema-field:focus-within');
    expect(mainStyle).toContain('.tenancy-form-band label:focus-within > span');
  });

  it('removes tenant charts through an exact-confirmation safety dialog', () => {
    const library = readFileSync(new URL('../views/ChartLibraryView.vue', import.meta.url), 'utf8');

    expect(library).toContain("auth.hasCapability('chart:manage')");
    expect(library).toContain('Chart를 Tenant Library에서 제거했습니다');
    expect(library).toContain('기존 배포 · Release 이력 · Values revision');
    expect(library).toContain('removalConfirmation !== removalKey(chartPendingRemoval)');
    expect(library).toContain('api.removeLibraryChart');
  });

  it('shows the chart provider separately from the acquisition source', () => {
    const library = readFileSync(new URL('../views/ChartLibraryView.vue', import.meta.url), 'utf8');

    expect(library).toContain("chart.sourceType === 'ARTIFACT_HUB'");
    expect(library).toContain("return '제공사 미확인'");
    expect(library).toContain('제공사');
    expect(library).toContain('소스');
    expect(library).toContain('Artifact Hub');
  });
});
