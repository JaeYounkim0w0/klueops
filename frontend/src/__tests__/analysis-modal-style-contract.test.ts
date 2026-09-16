import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/** source 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function source(path: string) {
  return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('AI Analysis modal visual contract', () => {
  it('keeps shared modal backdrops above the fixed product shell', () => {
    const mainStyle = source('src/styles/main.css');

    expect(mainStyle).toMatch(/\.modal-backdrop\s*\{[\s\S]*?z-index:\s*1000/);
  });

  it('does not override primary button labels inside Command Safety cards', () => {
    const analysisStyle = source('src/styles/components/analysis.css');

    expect(analysisStyle).toContain('.analysis-command-safety-item > span:not(.analysis-chip)');
    expect(analysisStyle).not.toMatch(/\.analysis-command-safety-item span:not\(\.analysis-chip\)/);
  });
});
