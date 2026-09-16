import { displayText } from '@/utils/text';

export type AnalysisResult = Record<string, unknown>;

/** stripMarkdownFence 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function stripMarkdownFence(value: string) {
  return value
    .trim()
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/\s*```$/i, '')
    .trim();
}

/** parseAnalysisResult 처리 데이터를 화면 또는 API 표현으로 변환한다. */
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

/** formattedAnalysisJson 처리 데이터를 화면 또는 API 표현으로 변환한다. */
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

/** arrayValue 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function arrayValue(value: unknown): AnalysisResult[] {
  return Array.isArray(value)
    ? value.filter((item): item is AnalysisResult => typeof item === 'object' && item !== null && !Array.isArray(item))
    : [];
}

/** objectValue 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function objectValue(value: unknown): AnalysisResult {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? value as AnalysisResult : {};
}

/** stringArray 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.map((item) => displayText(String(item), '')).filter(Boolean) : [];
}
