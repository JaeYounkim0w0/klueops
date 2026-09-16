import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const root = new URL('../', import.meta.url);
const read = /** read 처리 결과를 조회해 반환한다. */ (path: string) => readFileSync(new URL(path, root), 'utf8');

describe('operator workspace contracts', () => {
  it('provides keyboard-accessible cross-resource search', () => {
    const app = read('App.vue');
    const search = read('components/GlobalOperatorSearch.vue');
    const styles = read('styles/components/operator-workspace.css');
    expect(app).toContain('openOperatorSearchShortcut');
    expect(app).toContain('GlobalOperatorSearch');
    expect(search).toContain('searchOperatorWorkspace');
    expect(search).toContain('@keydown.down.prevent');
    expect(search).toContain('@keydown.enter.prevent');
    expect(search).toContain('operator-search-close');
    expect(styles).toContain('background: #fff');
    expect(styles).toContain('background: rgb(15 23 42 / 68%)');
  });

  it('provides incident collaboration and evidence split workflows', () => {
    const detail = read('views/IncidentDetailView.vue');
    expect(detail).toContain('updateIncidentCollaboration');
    expect(detail).toContain('mergeIncidents');
    expect(detail).toContain('splitIncident');
    expect(detail).toContain("activityType === 'COMMENT'");
  });

  it('provides managed runbook lifecycle and immutable history', () => {
    const runbooks = read('views/RunbooksView.vue');
    expect(runbooks).toContain('listRunbookLibrary');
    expect(runbooks).toContain('createCustomRunbook');
    expect(runbooks).toContain('updateCustomRunbook');
    expect(runbooks).toContain('restoreCustomRunbookVersion');
    expect(runbooks).toContain('deleteCustomRunbook');
  });
});
