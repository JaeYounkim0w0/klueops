import { displayText } from '@/utils/text';

export type AnalysisResult = Record<string, unknown>;

export function stripMarkdownFence(value: string) {
  return value
    .trim()
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/\s*```$/i, '')
    .trim();
}

export function parseAnalysisResult(value?: string): AnalysisResult | null {
  if (!value) {
    return null;
  }
  try {
    const parsed = JSON.parse(stripMarkdownFence(value));
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? parsed as AnalysisResult
      : null;
  } catch {
    return null;
  }
}

export function formattedAnalysisJson(value?: string) {
  if (!value) {
    return '';
  }
  const normalized = stripMarkdownFence(value);
  try {
    return JSON.stringify(JSON.parse(normalized), null, 2);
  } catch {
    return normalized;
  }
}

export function arrayValue(value: unknown): AnalysisResult[] {
  return Array.isArray(value)
    ? value.filter((item): item is AnalysisResult => typeof item === 'object' && item !== null && !Array.isArray(item))
    : [];
}

export function objectValue(value: unknown): AnalysisResult {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? value as AnalysisResult : {};
}

export function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.map((item) => displayText(String(item), '')).filter(Boolean) : [];
}
