/** 원본이 같은 제안만 적용할 수 있도록 서버와 동일한 UTF-8 SHA-256을 계산한다. */
export async function valuesDigest(values: string): Promise<string | undefined> {
  // HTTP로 열린 로컬 포털 등 Web Crypto 미지원 환경에서는 digest 검사를 건너뛸 수 있게 한다.
  // undefined를 반환해 호출부가 원인 대신 "subtle.digest" 예외를 사용자에게 노출하지 않도록 한다.
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) return undefined;
  const bytes = await subtle.digest('SHA-256', new TextEncoder().encode(values.trim() ? values : '{}'));
  return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('');
}

/** YAML 변경만 비교하며 민감 설정의 원문을 변경 요약에 노출하지 않는다. */
export function previewValuesChanges(current: string, proposed: string): Array<{path: string; before: string; after: string}> {
  if (!proposed.trim()) return [];
  const changes: Array<{path: string; before: string; after: string}> = [];
  try {
    compareValues(load(current) || {}, load(proposed) || {}, '', changes);
  } catch { return []; }
  return changes;
}

/** object는 필드별로 비교하고 배열은 교체 단위로 표시한다. */
function compareValues(before: unknown, after: unknown, path: string, result: Array<{path: string; before: string; after: string}>): void {
  if (JSON.stringify(before) === JSON.stringify(after)) return;
  if ((isMapping(before) || before === undefined) && (isMapping(after) || after === undefined)) {
    const left = isMapping(before) ? before : {};
    const right = isMapping(after) ? after : {};
    for (const key of new Set([...Object.keys(left), ...Object.keys(right)]))
      compareValues(left[key], right[key], path ? `${path}.${key}` : key, result);
  } else result.push({path, before: displayValue(before, path), after: displayValue(after, path)});
}

/** 배열을 제외한 mapping 여부를 확인한다. */
function isMapping(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

/** 새 object나 배열 안의 credential도 요약에서 보호한다. */
function displayValue(value: unknown, path: string): string {
  if (value === undefined) return '미지정 (Chart 기본값)';
  const text = JSON.stringify(value);
  if (/password|passwd|secret|token|credential|api.?key|private.?key/i.test(path + text)) return '보호된 설정';
  return text.length > 240 ? `${text.slice(0, 240)}…` : text;
}
import { load } from 'js-yaml';
