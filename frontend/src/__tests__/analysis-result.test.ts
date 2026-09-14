import { describe, expect, it } from 'vitest';
import {
  arrayValue,
  formattedAnalysisJson,
  objectValue,
  parseAnalysisResult,
  stripMarkdownFence
} from '@/utils/analysisResult';

describe('analysis result helpers', () => {
  it('parses fenced object JSON and rejects arrays', () => {
    expect(parseAnalysisResult('```json\n{"risk": 2}\n```')).toEqual({ risk: 2 });
    expect(parseAnalysisResult('[1, 2]')).toBeNull();
  });

  it('normalizes object arrays without leaking primitives', () => {
    expect(arrayValue([{ id: 1 }, null, 'unsafe', [1]])).toEqual([{ id: 1 }]);
    expect(objectValue(null)).toEqual({});
  });

  it('formats valid JSON and preserves invalid model output', () => {
    expect(formattedAnalysisJson('{"risk":2}')).toContain('  "risk": 2');
    expect(formattedAnalysisJson('not-json')).toBe('not-json');
    expect(stripMarkdownFence('```json\n{}\n```')).toBe('{}');
  });
});
