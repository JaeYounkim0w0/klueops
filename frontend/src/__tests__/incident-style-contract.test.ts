import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const componentSource = readFileSync(resolve(process.cwd(), 'src/views/IncidentsView.vue'), 'utf8');
const styleSource = readFileSync(resolve(process.cwd(), 'src/styles/components/operator-workspace.css'), 'utf8');

describe('incident list visual contract', () => {
  it('attaches the shared readable-title contract to incident titles', () => {
    expect(componentSource).toContain('<strong class="operator-content-title">{{ incident.title }}</strong>');
    expect(styleSource).toMatch(/\.incident-list-row\s+\.operator-content-title\s*\{[\s\S]*?color:\s*#182230/);
    expect(styleSource).toMatch(/-webkit-text-fill-color:\s*#182230/);
  });

  it('uses the shared modal surface and header for manual incident creation', () => {
    expect(componentSource).toContain('class="modal-panel incident-create-modal"');
    expect(componentSource).toContain('<header class="modal-header">');
    expect(componentSource).toContain('aria-labelledby="incident-create-title"');
  });
});
