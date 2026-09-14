import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const clientSource = readFileSync(new URL('../api/client.ts', import.meta.url), 'utf8');
const viewSource = readFileSync(new URL('../views/AnalysisView.vue', import.meta.url), 'utf8');

describe('analysis locale contract', () => {
  it('keeps the generated locale on analysis responses', () => {
    expect(clientSource).toContain("locale?: 'ko-KR' | 'en-US'");
  });

  it('warns when historical analysis language differs from the active language', () => {
    expect(viewSource).toContain('analysisLocaleMismatch');
    expect(viewSource).toContain("t('analysis.localeMismatch')");
  });
});
