import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const componentSource = readFileSync(resolve(process.cwd(), 'src/components/NotificationCenter.vue'), 'utf8');
const styleSource = readFileSync(resolve(process.cwd(), 'src/styles/components/operator-workspace.css'), 'utf8');

describe('operations notification visual contract', () => {
  it('attaches the shared readable-title contract to notification headings', () => {
    expect(componentSource.match(/class="operator-content-title"/g)).toHaveLength(2);
    expect(styleSource).toMatch(/\.notification-popover\s+\.operator-content-title,[\s\S]*?\.notification-row\s+\.operator-content-title,[\s\S]*?color:\s*#182230/);
    expect(styleSource).toMatch(/-webkit-text-fill-color:\s*#182230/);
  });
});
