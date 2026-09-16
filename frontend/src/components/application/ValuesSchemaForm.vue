<script setup lang="ts">
import { computed } from "vue";

export interface ValuesSchemaField {
  path: string;
  title: string;
  description?: string;
  type: "string" | "number" | "integer" | "boolean";
  enumValues?: unknown[];
}

const props = defineProps<{
  fields: ValuesSchemaField[];
  modelValue: Record<string, unknown>;
}>();
const emit = defineEmits<{
  "update:modelValue": [value: Record<string, unknown>];
}>();
const values = computed(() => props.modelValue);

/** 점 경로를 따라 현재 Form 값을 안전하게 읽는다. */
function read(path: string): unknown {
  return path
    .split(".")
    .reduce<unknown>(
      (current, key) =>
        current && typeof current === "object"
          ? (current as Record<string, unknown>)[key]
          : undefined,
      values.value,
    );
}

/** 원본 객체를 직접 변경하지 않고 점 경로의 값을 갱신한다. */
function write(field: ValuesSchemaField, event: Event): void {
  const input = event.target as HTMLInputElement | HTMLSelectElement;
  let next: unknown = input.value;
  if (field.type === "boolean") next = (input as HTMLInputElement).checked;
  if (field.type === "number" || field.type === "integer")
    next = input.value === "" ? undefined : Number(input.value);
  const root = structuredClone(props.modelValue || {});
  const keys = field.path.split(".");
  let cursor = root;
  keys.slice(0, -1).forEach((key) => {
    if (!cursor[key] || typeof cursor[key] !== "object") cursor[key] = {};
    cursor = cursor[key] as Record<string, unknown>;
  });
  cursor[keys[keys.length - 1]] = next;
  emit("update:modelValue", root);
}
</script>

<template>
  <div class="values-schema-form ui-form-grid">
    <label v-for="field in fields" :key="field.path" class="schema-field ui-surface-card ui-form-field">
      <span
        ><strong>{{ field.title }}</strong
        ><code>{{ field.path }}</code></span
      >
      <small v-if="field.description">{{ field.description }}</small>
      <select
        v-if="field.enumValues"
        :value="read(field.path)"
        @change="write(field, $event)"
      >
        <option
          v-for="choice in field.enumValues"
          :key="String(choice)"
          :value="choice"
        >
          {{ choice }}
        </option>
      </select>
      <input
        v-else-if="field.type === 'boolean'"
        type="checkbox"
        :checked="Boolean(read(field.path))"
        @change="write(field, $event)"
      />
      <input
        v-else
        :type="field.type === 'string' ? 'text' : 'number'"
        :step="field.type === 'integer' ? 1 : 'any'"
        :value="read(field.path) ?? ''"
        @input="write(field, $event)"
      />
    </label>
  </div>
</template>
