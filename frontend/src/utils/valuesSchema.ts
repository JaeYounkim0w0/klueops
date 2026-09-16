import type { ValuesSchemaField } from "@/components/application/ValuesSchemaForm.vue";

const SENSITIVE_KEY = /(password|secret|token|private.?key|credential)/i;

/** JSON Schema의 object property를 안전한 scalar Form 필드 목록으로 평탄화한다. */
export function flattenValuesSchema(schemaJson?: string): ValuesSchemaField[] {
  if (!schemaJson) return [];
  const schema = JSON.parse(schemaJson) as Record<string, unknown>;
  const result: ValuesSchemaField[] = [];
  walk(schema, "", result);
  return result;
}

/** 지원 가능한 scalar 속성만 순회하고 Secret 가능성이 있는 경로는 YAML 전용으로 남긴다. */
function walk(
  schema: Record<string, unknown>,
  prefix: string,
  result: ValuesSchemaField[],
): void {
  const properties = schema.properties;
  if (!properties || typeof properties !== "object") return;
  Object.entries(properties as Record<string, Record<string, unknown>>).forEach(
    ([key, value]) => {
      const path = prefix ? `${prefix}.${key}` : key;
      if (SENSITIVE_KEY.test(path)) return;
      if (value.type === "object" || value.properties)
        return walk(value, path, result);
      if (
        !["string", "number", "integer", "boolean"].includes(String(value.type))
      )
        return;
      result.push({
        path,
        title: String(value.title || key),
        description: value.description ? String(value.description) : undefined,
        type: value.type as ValuesSchemaField["type"],
        enumValues: Array.isArray(value.enum) ? value.enum : undefined,
      });
    },
  );
}
