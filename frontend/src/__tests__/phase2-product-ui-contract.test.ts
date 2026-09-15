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

    expect(source).toContain('class="application-summary"');
    expect(source).toContain('v-model="applicationSearch"');
    expect(source).toContain('class="application-table-row"');
    expect(source).toContain('class="application-inspector"');
    expect(source).toContain("['INSTALLING', 'UPGRADING', 'ROLLING_BACK', 'UNINSTALLING', 'DEPLOY_REQUESTED']");
  });

  it('keeps the Discover search field readable in OS dark form themes', () => {
    const delivery = readFileSync(new URL('../styles/components/application-delivery.css', import.meta.url), 'utf8');

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
    expect(fields).toContain('kubectl get gateway -A');
  });
});
