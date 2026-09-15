import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('application exposure ownership UX', () => {
  it('separates chart-managed resources from KlueOps companion HTTPRoute', () => {
    const source = readFileSync(new URL('../views/DeploymentWizardView.vue', import.meta.url), 'utf8');

    expect(source).toContain("value=\"CHART_MANAGED\"");
    expect(source).toContain('Values가 만드는 Ingress/HTTPRoute 사용');
    expect(source).toContain('Preview가 실제 Ingress 또는 HTTPRoute 생성 여부를 확인');
    expect(source).toContain('KlueOps HTTPRoute');
  });
});
