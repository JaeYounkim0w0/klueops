<script setup lang="ts">
import type { AnalysisCommandExecutionResponse, AnalysisCommandPreviewResponse } from '@/api/client';

defineProps<{
  pending: AnalysisCommandPreviewResponse | null;
  execution: AnalysisCommandExecutionResponse | null;
  confirmationInput: string;
  executing: boolean;
  selectedNamespace?: string;
  guardStateClass: (value: unknown) => string;
  guardStateLabel: (value: unknown, neutralLabel?: string) => string;
  isRollbackCommand: (command?: string) => boolean;
  numberValue: (value: unknown, fallback?: string) => string;
}>();

const emit = defineEmits<{
  closePending: [];
  closeExecution: [];
  execute: [];
  copy: [command: string];
  retry: [];
  'update:confirmationInput': [value: string];
}>();
</script>

<template>
  <div v-if="pending" class="modal-backdrop analysis-command-modal" @click.self="emit('closePending')">
    <section class="modal-panel feedback-detail-modal" aria-modal="true" role="dialog" aria-label="변경 조치 확인">
      <header class="modal-header"><div><h2>변경 조치 확인</h2><p>{{ pending.safety }} · {{ pending.normalizedNamespace || selectedNamespace || 'default' }}</p></div><button class="icon-button" title="닫기" type="button" @click="emit('closePending')"><i class="pi pi-times"></i></button></header>
      <div class="feedback-detail-body">
        <section class="analysis-change-confirmation">
          <div class="analysis-change-warning"><span class="analysis-chip warning">상태 변경</span><strong>이 작업은 실제 Kubernetes 리소스를 변경합니다.</strong><p>{{ pending.reason || '대상과 영향 범위를 확인한 뒤 실행하세요.' }}</p></div>
          <div><span class="label">Command</span><code>{{ pending.command }}</code></div>
          <div class="analysis-change-guard-grid" aria-label="변경 실행 안전 게이트">
            <article class="analysis-change-guard-card"><div><span class="label">RBAC</span><span class="analysis-chip" :class="guardStateClass(pending.rbacAllowed)">{{ guardStateLabel(pending.rbacAllowed, '확인 안 됨') }}</span></div><strong>현재 계정 권한</strong><p>대상 리소스에 patch/update 권한이 있는지 Kubernetes SelfSubjectAccessReview로 확인합니다.</p></article>
            <article class="analysis-change-guard-card"><div><span class="label">Dry-run</span><span class="analysis-chip" :class="guardStateClass(pending.dryRunPassed)">{{ guardStateLabel(pending.dryRunPassed, '확인 안 됨') }}</span></div><strong>사전 실행 검증</strong><p>대상 Deployment 존재 여부와 요청 파라미터가 안전 범위 안에 있는지 먼저 검증합니다.</p></article>
            <article class="analysis-change-guard-card"><div><span class="label">Rollback Guard</span><span class="analysis-chip" :class="guardStateClass(pending.rollbackGuardPassed)">{{ guardStateLabel(pending.rollbackGuardPassed, isRollbackCommand(pending.command) ? '확인 안 됨' : '대상 아님') }}</span></div><strong>되돌림 조건</strong><p>rollback은 명시 revision과 복구 가능한 ReplicaSet 템플릿이 확인된 경우에만 실행합니다.</p></article>
          </div>
          <div v-if="pending.guardMessage || pending.dryRunSummary" class="analysis-change-guard-summary"><div v-if="pending.guardMessage"><span class="label">Guard Message</span><p>{{ pending.guardMessage }}</p></div><div v-if="pending.dryRunSummary"><span class="label">Dry-run Summary</span><pre>{{ pending.dryRunSummary }}</pre></div></div>
          <label class="form-field"><span>확인 문구 입력</span><input :value="confirmationInput" :placeholder="pending.confirmationText || 'APPLY namespace/resource'" @input="emit('update:confirmationInput', ($event.target as HTMLInputElement).value)" /><small>정확히 입력해야 실행됩니다: {{ pending.confirmationText }}</small></label>
        </section>
      </div>
      <footer class="modal-actions"><button class="secondary-button" type="button" @click="emit('closePending')">취소</button><button class="danger-button" type="button" :disabled="!pending.executable || confirmationInput.trim() !== (pending.confirmationText || '') || executing" @click="emit('execute')"><i :class="executing ? 'pi pi-spin pi-spinner' : 'pi pi-check'"></i><span>변경 실행</span></button></footer>
    </section>
  </div>

  <div v-if="execution" class="modal-backdrop analysis-command-modal" @click.self="emit('closeExecution')">
    <section class="modal-panel feedback-detail-modal" aria-modal="true" role="dialog" aria-label="검증 명령 실행 결과">
      <header class="modal-header"><div><h2>검증 명령 실행 결과</h2><p>{{ execution.status }} · {{ numberValue(execution.durationMs, '0') }}ms</p></div><button class="icon-button" title="닫기" type="button" @click="emit('closeExecution')"><i class="pi pi-times"></i></button></header>
      <div class="feedback-detail-body"><section class="analysis-command-execution-detail"><div><span class="label">Command</span><code>{{ execution.command }}</code></div><div><span class="label">Why</span><p>{{ execution.reason || '분석 결과를 실제 Kubernetes 상태로 검증하기 위한 명령입니다.' }}</p></div><div v-if="execution.stderrText"><span class="label">Error</span><pre>{{ execution.stderrText }}</pre></div><div><span class="label">Output</span><pre>{{ execution.stdoutText || '-' }}</pre></div><div v-if="execution.status === 'SUCCEEDED'" class="analysis-command-next-step"><i class="pi pi-chart-line"></i><div><strong>조치 결과를 다시 확인하세요</strong><p>명령 실행 후에는 같은 scope를 재분석해 이벤트, 리소스 상태, issue group 변화가 실제로 반영됐는지 확인하는 것이 좋습니다.</p></div></div></section></div>
      <footer class="modal-actions"><button class="secondary-button" type="button" @click="emit('copy', execution.command)"><i class="pi pi-copy"></i><span>명령 복사</span></button><button v-if="execution.status === 'SUCCEEDED'" class="primary-button" type="button" @click="emit('retry')"><i class="pi pi-refresh"></i><span>현재 범위 재분석</span></button><button class="secondary-button" type="button" @click="emit('closeExecution')">닫기</button></footer>
    </section>
  </div>
</template>
