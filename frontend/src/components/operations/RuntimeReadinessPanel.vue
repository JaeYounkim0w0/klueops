<script setup lang="ts">
import { computed } from 'vue';
import type { RuntimeReadinessResponse } from '@/api/client';
import { formatTimestamp } from '@/utils/time';

const props = defineProps<{ readiness: RuntimeReadinessResponse }>();

const severity = { BLOCKED: 0, PILOT: 1, READY: 2 } as const;
const orderedChecks = computed(() => [...props.readiness.checks]
  .sort((left, right) => severity[left.status] - severity[right.status]));
const counts = computed(() => props.readiness.checks.reduce((result, check) => {
  result[check.status] += 1;
  return result;
}, { READY: 0, PILOT: 0, BLOCKED: 0 }));
</script>

<template>
  <section class="reliability-band runtime-readiness-detail" :class="readiness.status.toLowerCase()">
    <header>
      <div>
        <span class="page-eyebrow">PRODUCTION READINESS</span>
        <h2>실행 환경 준비 상태</h2>
        <p>운영을 막는 항목부터 정렬했습니다. 각 카드의 필요 조치를 위에서부터 처리하세요.</p>
      </div>
      <span class="status-pill" :class="readiness.status.toLowerCase()">{{ readiness.status }}</span>
    </header>
    <div class="runtime-readiness-summary">
      <div>
        <strong>{{ readiness.status === 'READY' ? '상용 운영 기준 충족' : readiness.status === 'BLOCKED' ? '운영 전 필수 조치 필요' : '단일 운영자 파일럿 범위' }}</strong>
        <span>{{ readiness.mode }} · {{ formatTimestamp(readiness.checkedAt) }}</span>
      </div>
      <div class="analysis-remediation-meta" aria-label="준비 상태 요약">
        <span class="analysis-chip critical">차단 {{ counts.BLOCKED }}</span>
        <span class="analysis-chip warning">점검 {{ counts.PILOT }}</span>
        <span class="analysis-chip success">준비 {{ counts.READY }}</span>
      </div>
    </div>
    <div class="runtime-readiness-checks">
      <article v-for="check in orderedChecks" :key="check.code" :class="check.status.toLowerCase()">
        <div>
          <span class="status-pill" :class="check.status.toLowerCase()">{{ check.status }}</span>
          <code>{{ check.code }}</code>
        </div>
        <strong>{{ check.title }}</strong>
        <p>{{ check.detail }}</p>
        <dl>
          <div><dt>현재 값</dt><dd>{{ check.observedValue }}</dd></div>
          <div><dt>다음 조치</dt><dd>{{ check.action }}</dd></div>
        </dl>
      </article>
    </div>
  </section>
</template>
