import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('commercial readiness UI contract', () => {
  it('exposes persistent evidence and bounded operational telemetry', () => {
    const view = readFileSync('src/views/OperationsReliabilityView.vue', 'utf8');
    const client = readFileSync('src/api/client.ts', 'utf8');

    expect(view).toContain('ProductionEvidencePanel');
    expect(view).toContain('OperationalTelemetryPanel');
    expect(view).toContain('RuntimeReadinessPanel');
    expect(view).not.toContain('class="runtime-readiness-checks"');
    expect(client).toContain('/api/operations/production-evidence/runs');
    expect(client).toContain('/api/operations/telemetry');
  });

  it('shows distinct corpus and contract evaluation in the trust center', () => {
    const trust = readFileSync('src/views/AiTrustCenterView.vue', 'utf8');

    expect(trust).toContain('contractEvaluation');
    expect(trust).toContain('generatedVariantsRemoved');
  });
});
