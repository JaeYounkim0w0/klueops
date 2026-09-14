<script setup lang="ts">
import { displayText } from '@/utils/text';

type CommandItem = Record<string, unknown>;

defineProps<{
  safety: Record<string, unknown>;
  commands: CommandItem[];
  totalCount: number;
  hiddenCount: number;
  expanded: boolean;
  beginner: boolean;
}>();

const emit = defineEmits<{
  copy: [command: string];
  openConsole: [command: string];
  toggle: [];
}>();

function text(value: unknown, fallback = '-') {
  return displayText(value, fallback);
}

function count(value: unknown) {
  return typeof value === 'number' ? String(value) : '0';
}

function safetyClass(value: unknown) {
  const level = text(value, '').toUpperCase();
  return {
    success: level === 'READ_ONLY',
    warning: level === 'RISKY_CHANGE',
    critical: level === 'DESTRUCTIVE',
    info: !['READ_ONLY', 'RISKY_CHANGE', 'DESTRUCTIVE'].includes(level)
  };
}

function safetyLabel(value: unknown) {
  return ({
    READ_ONLY: '읽기 전용',
    RISKY_CHANGE: '변경 가능',
    DESTRUCTIVE: '위험 조치'
  } as Record<string, string>)[text(value, '').toUpperCase()] ?? '검토 필요';
}
</script>

<template>
  <section class="analysis-command-safety-panel" aria-label="명령 안전도">
    <header>
      <div>
        <span class="label">Command Safety</span>
        <h3>{{ beginner ? '어떤 명령부터 실행해야 하나요?' : '명령 안전도' }}</h3>
        <p>{{ text(beginner ? safety.beginnerSummary || safety.summary : safety.summary || safety.beginnerSummary) }}</p>
      </div>
      <div class="analysis-remediation-meta">
        <span class="analysis-chip success">읽기 {{ count(safety.readOnlyCount) }}</span>
        <span class="analysis-chip warning">변경 {{ count(safety.changeCount) }}</span>
        <span class="analysis-chip critical">위험 {{ count(safety.destructiveCount) }}</span>
      </div>
    </header>
    <div class="analysis-command-safety-list">
      <article
        v-for="command in commands"
        :key="`${text(command.source)}-${text(command.command)}`"
        class="analysis-command-safety-item"
      >
        <div class="analysis-command-safety-heading">
          <span class="analysis-chip" :class="safetyClass(command.safetyLevel)">
            {{ safetyLabel(command.safetyLevel) }}
          </span>
          <strong>{{ text(command.label, '검증 명령') }}</strong>
        </div>
        <code>{{ text(command.command, '명령 정보 없음') }}</code>
        <span>{{ text(command.why, '분석 결과를 확인하기 위한 명령입니다.') }}</span>
        <small v-if="beginner">{{ text(command.beginnerExplanation) }}</small>
        <div class="analysis-command-item-actions">
          <button class="secondary-button compact-button" type="button" @click="emit('copy', text(command.command, ''))"><i class="pi pi-copy"></i><span>복사</span></button>
          <button class="primary-button compact-button" type="button" @click="emit('openConsole', text(command.command, ''))"><i class="pi pi-external-link"></i><span>콘솔에서 검증</span></button>
        </div>
      </article>
    </div>
    <button
      v-if="totalCount > 8"
      class="secondary-button analysis-command-safety-toggle"
      type="button"
      @click="emit('toggle')"
    >
      <i :class="expanded ? 'pi pi-angle-up' : 'pi pi-angle-down'"></i>
      {{ expanded ? '접기' : `전체 ${totalCount}개 보기` }}
      <span v-if="hiddenCount">숨김 {{ hiddenCount }}개</span>
    </button>
  </section>
</template>
