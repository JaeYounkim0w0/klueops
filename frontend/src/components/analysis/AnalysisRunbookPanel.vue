<script setup lang="ts">
import { computed } from 'vue';
import type { AnalysisResult } from '@/utils/analysisResult';

const props = defineProps<{
  actions: AnalysisResult[];
  total: number;
  urgent: number;
  commands: number;
  canOpenLogs: (action: AnalysisResult) => boolean;
}>();

const emit = defineEmits<{
  copy: [command: string];
  openConsole: [command: string];
  logs: [action: AnalysisResult];
}>();

const visibleActions = computed(() => props.actions.filter((action) => !isDestructive(action)));

function isDestructive(action: AnalysisResult) {
  return Boolean(action.destructive) || textValue(action.commandType, '').toLowerCase() === 'destructive';
}

function textValue(value: unknown, fallback = '') {
  return value === null || value === undefined ? fallback : String(value);
}
</script>

<template>
  <section class="runbook-command-panel analysis-runbook-panel" aria-label="Runbook 검증 큐">
    <header>
      <div>
        <span class="label">Verification Runbook</span>
        <h3>먼저 확인할 운영 작업</h3>
        <p>클러스터 변경 명령은 자동 실행하지 않고, 근거 확인용 명령 복사와 관련 로그 조회만 제공합니다.</p>
      </div>
      <div class="runbook-summary">
        <span>{{ total }} total</span>
        <strong>{{ urgent }} priority</strong>
        <span>{{ commands }} commands</span>
      </div>
    </header>

    <ol v-if="visibleActions.length" class="runbook-action-list">
      <li
        v-for="(action, index) in visibleActions"
        :key="`${textValue(action.priority)}-${textValue(action.targetKind)}-${textValue(action.targetName)}-${textValue(action.command)}`"
        class="runbook-action-item"
      >
        <div class="runbook-action-rank">{{ index + 1 }}</div>
        <div class="runbook-action-body">
          <div class="diagnostic-row-heading">
            <strong>{{ textValue(action.priority, 'P3') }} · {{ textValue(action.title, '검증 작업') }}</strong>
            <span class="status-pill info">{{ textValue(action.targetKind, '-') }}/{{ textValue(action.targetName, '-') }}</span>
          </div>
          <p>{{ textValue(action.reason, 'AI가 제안한 확인 작업입니다.') }}</p>
          <code v-if="action.command">{{ textValue(action.command) }}</code>
          <div class="runbook-action-controls">
            <button v-if="action.command" class="secondary-button compact-button" type="button" @click="emit('copy', textValue(action.command))">
              <i class="pi pi-copy"></i>
              <span>명령 복사</span>
            </button>
            <button v-if="action.command" class="primary-button compact-button" type="button" @click="emit('openConsole', textValue(action.command))">
              <i class="pi pi-external-link"></i>
              <span>콘솔에서 검증</span>
            </button>
            <button
              class="secondary-button compact-button"
              :disabled="!props.canOpenLogs(action)"
              type="button"
              @click="emit('logs', action)"
            >
              <i class="pi pi-list"></i>
              <span>관련 로그</span>
            </button>
          </div>
        </div>
      </li>
    </ol>
    <p v-else class="muted-text">선택한 필터에 해당하는 runbook 항목이 없습니다.</p>
  </section>
</template>
