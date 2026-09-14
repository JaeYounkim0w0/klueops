import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const viewSource = readFileSync(resolve(process.cwd(), 'src/views/RunbooksView.vue'), 'utf8');
const styleSource = readFileSync(resolve(process.cwd(), 'src/styles/main.css'), 'utf8');

describe('runbook library visual contract', () => {
  it('keeps runbook titles readable on the light summary background', () => {
    expect(viewSource).toContain('class="runbook-library-title"');
    expect(styleSource).toMatch(/\.runbook-library-title\s*\{[\s\S]*?color:\s*#182230/);
    expect(styleSource).toMatch(/\.runbook-library-summary\s*\{[\s\S]*?color:\s*#344054/);
  });
});
