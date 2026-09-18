<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useJobCenterStore, type JobCenterItem } from '@/stores/jobCenter';
import { formatElapsedDuration } from '@/utils/time';

const jobCenter = useJobCenterStore();
const { locale, t } = useI18n();

const runningCount = computed(() => jobCenter.runningJobs.length);
const diagnosticsLabel = computed(() => {
  const diagnostics = jobCenter.diagnostics;
  const parts: string[] = [];
  if (diagnostics.completedCount > 0) {
    parts.push(t('jobs.average', { duration: durationMsLabel(diagnostics.averageDurationMs) }));
  }
  if (diagnostics.failedCount > 0) {
    parts.push(t('jobs.failedCount', { count: diagnostics.failedCount }));
  }
  if (diagnostics.slowRunningCount > 0) {
    parts.push(t('jobs.slowCount', { count: diagnostics.slowRunningCount }));
  }
  return parts.join(' · ');
});

/** statusIcon 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function statusIcon(job: JobCenterItem) {
  if (job.status === 'SUCCEEDED') {
    return 'pi pi-check-circle';
  }
  if (['FAILED', 'CANCELED', 'TIMEOUT'].includes(job.status)) {
    return 'pi pi-exclamation-triangle';
  }
  return 'pi pi-spin pi-spinner';
}

/** statusLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function statusLabel(job: JobCenterItem) {
  if (job.status === 'PENDING') {
    return t('jobs.statusPending');
  }
  if (job.status === 'RUNNING') {
    return t('jobs.statusRunning');
  }
  if (job.status === 'SUCCEEDED') {
    return t('jobs.statusSucceeded');
  }
  if (job.status === 'CANCELED') {
    return t('jobs.statusCanceled');
  }
  if (job.status === 'TIMEOUT') {
    return t('jobs.statusTimeout');
  }
  return t('jobs.statusFailed');
}

/** shortId 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function shortId(value?: string) {
  return value ? value.slice(0, 8) : '-';
}

/** timeLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function timeLabel(value?: string) {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleTimeString(locale.value, { hour: '2-digit', minute: '2-digit' });
}

/** durationLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function durationLabel(job: JobCenterItem) {
  // 상태가 RUNNING으로 유지돼도 폴링 시각을 반영해 경과 시간을 다시 표시한다.
  const elapsed = formatElapsedDuration(job.startedAt, job.completedAt, new Date(job.updatedAt));
  if (elapsed === '-') {
    return t('jobs.elapsed', { duration: '-' });
  }
  return job.completedAt ? t('jobs.elapsed', { duration: elapsed }) : `${t('jobs.statusRunning')} ${elapsed}`;
}

/** canCancel 처리 조건의 충족 여부를 판단한다. */
function canCancel(job: JobCenterItem) {
  return !['SUCCEEDED', 'FAILED', 'CANCELED', 'TIMEOUT'].includes(job.status);
}

/** canRetry 처리 조건의 충족 여부를 판단한다. */
function canRetry(job: JobCenterItem) {
  return Boolean(job.analysisId && ['FAILED', 'CANCELED', 'TIMEOUT'].includes(job.status));
}

/** retryHint 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function retryHint(job: JobCenterItem) {
  if (job.status === 'TIMEOUT') {
    return t('jobs.timeoutHint');
  }
  if (job.status === 'CANCELED') {
    return t('jobs.canceledHint');
  }
  if (job.status === 'FAILED') {
    return t('jobs.failedHint');
  }
  return '';
}

/** retryJob 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function retryJob(job: JobCenterItem) {
  await jobCenter.retryAnalysisJob(job.jobId);
}

/** durationMsLabel 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function durationMsLabel(value: number) {
  if (!value) {
    return '-';
  }
  if (value < 1000) {
    return `${value}ms`;
  }
  const seconds = Math.round(value / 1000);
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return `${minutes}m ${remainder}s`;
}
</script>

<template>
  <aside v-if="jobCenter.hasJobs" class="job-dock" :class="{ collapsed: jobCenter.collapsed }" :aria-label="t('jobs.title')">
    <header class="job-dock-header">
      <div>
        <strong>{{ t('jobs.title') }}</strong>
        <span>{{ runningCount > 0 ? t('jobs.runningCount', { count: runningCount }) : t('jobs.recent') }}</span>
        <small v-if="diagnosticsLabel">{{ diagnosticsLabel }}</small>
      </div>
      <div class="job-dock-actions">
        <button
          v-if="runningCount === 0"
          class="icon-button table-icon-button"
          type="button"
          :title="t('jobs.clearCompleted')"
          @click="jobCenter.clearCompleted"
        >
          <i class="pi pi-trash"></i>
        </button>
        <button
          class="icon-button table-icon-button"
          type="button"
          :title="jobCenter.collapsed ? t('jobs.expand') : t('jobs.collapse')"
          @click="jobCenter.toggleCollapsed"
        >
          <i :class="jobCenter.collapsed ? 'pi pi-angle-up' : 'pi pi-angle-down'"></i>
        </button>
      </div>
    </header>

    <div v-if="!jobCenter.collapsed" class="job-dock-list">
      <article v-for="job in jobCenter.visibleJobs" :key="job.jobId" class="job-dock-item" :class="job.tone">
        <i :class="statusIcon(job)"></i>
        <div class="job-dock-copy">
          <strong>{{ job.title }}</strong>
          <span>{{ job.errorMessage || job.detail || job.type || t('jobs.checking') }}</span>
          <span v-if="retryHint(job)" class="job-dock-hint">{{ retryHint(job) }}</span>
          <small>
            job {{ shortId(job.jobId) }}
            · {{ timeLabel(job.completedAt || job.startedAt || job.createdAt || job.updatedAt) }}
            · {{ durationLabel(job) }}
          </small>
        </div>
        <span class="status-pill" :class="{ info: job.tone === 'info', error: job.tone === 'error' }">
          {{ statusLabel(job) }}
        </span>
        <button
          v-if="canCancel(job)"
          class="icon-button table-icon-button"
          type="button"
          :title="t('jobs.cancel')"
          @click="jobCenter.cancelJob(job.jobId)"
        >
          <i class="pi pi-times"></i>
        </button>
        <button
          v-else-if="canRetry(job)"
          class="icon-button table-icon-button"
          type="button"
          :title="t('jobs.retryAnalysis')"
          @click="retryJob(job)"
        >
          <i class="pi pi-refresh"></i>
        </button>
      </article>
    </div>
  </aside>
</template>
