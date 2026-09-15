import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('Phase 2 product UI contract', () => {
  it('uses the shared dark navigation shell and top context bar', () => {
    const app = readFileSync(new URL('../App.vue', import.meta.url), 'utf8');
    const shell = readFileSync(new URL('../styles/product-shell.css', import.meta.url), 'utf8');

    expect(app).toContain('class="workspace-topbar"');
    expect(app).toContain('<TenantWorkspaceSelector />');
    expect(shell).toContain('background: var(--shell-navy)');
    expect(shell).toContain('.sidebar.nav-open');
  });

  it('presents Applications as summary, searchable list and selected detail', () => {
    const source = readFileSync(new URL('../views/ApplicationsView.vue', import.meta.url), 'utf8');

    expect(source).toContain('class="application-summary"');
    expect(source).toContain('v-model="applicationSearch"');
    expect(source).toContain('class="application-table-row"');
    expect(source).toContain('class="application-inspector"');
    expect(source).toContain("['INSTALLING', 'UPGRADING', 'ROLLING_BACK', 'UNINSTALLING', 'DEPLOY_REQUESTED']");
  });
});
