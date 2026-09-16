import { describe, expect, it } from 'vitest';
import { flattenValuesSchema } from './valuesSchema';

describe('flattenValuesSchema', () => {
  it('flattens supported scalar fields and preserves enum metadata', () => {
    const fields = flattenValuesSchema(JSON.stringify({ type: 'object', properties: {
      replicaCount: { type: 'integer', description: 'Replica count' },
      service: { type: 'object', properties: { type: { type: 'string', enum: ['ClusterIP', 'NodePort'] } } },
    } }));
    expect(fields.map((field) => field.path)).toEqual(['replicaCount', 'service.type']);
    expect(fields[1].enumValues).toEqual(['ClusterIP', 'NodePort']);
  });

  it('does not expose likely secret or unsupported array fields in the form', () => {
    const fields = flattenValuesSchema(JSON.stringify({ type: 'object', properties: {
      adminPassword: { type: 'string' }, extraEnv: { type: 'array' }, enabled: { type: 'boolean' },
    } }));
    expect(fields.map((field) => field.path)).toEqual(['enabled']);
  });
});
