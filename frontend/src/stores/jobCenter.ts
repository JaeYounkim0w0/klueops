import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { api, type JobResponse } from '@/api/client';
import { isRetryableJobPollError } from '@/utils/jobPolling';

export type JobCenterTone = 'info' | 'success' | 'error';

export interface JobCenterItem {
  jobId: string;
  analysisId?: string;
  title: string;
  detail?: string;
  type?: string;
  status: JobResponse['status'];
  tone: JobCenterTone;
  createdAt?: string;
  startedAt?: string;
  completedAt?: string;
  updatedAt: string;
  errorMessage?: string;
}

export interface JobCenterDiagnostics {
  completedCount: number;
  failedCount: number;
  averageDurationMs: number;
  slowRunningCount: number;
}

interface RegisterJobOptions {
  jobId: string;
  analysisId?: string;
  title: string;
  detail?: string;
  type?: string;
}

const TERMINAL_STATUSES = new Set<JobResponse['status']>(['SUCCEEDED', 'FAILED', 'CANCELED', 'TIMEOUT']);
const POLL_INTERVAL_MS = 1000;
const MAX_POLL_ATTEMPTS = 900;

export const useJobCenterStore = defineStore('jobCenter', () => {
  const jobs = ref<JobCenterItem[]>([]);
  const collapsed = ref(false);
  const pollers = new Map<string, Promise<JobResponse>>();

  const visibleJobs = computed(() => jobs.value.slice(0, 5));
  const runningJobs = computed(() => jobs.value.filter((job) => !TERMINAL_STATUSES.has(job.status)));
  const hasJobs = computed(() => jobs.value.length > 0);
  const diagnostics = computed<JobCenterDiagnostics>(() => {
    const completedJobs = jobs.value.filter((job) => job.startedAt && job.completedAt);
    const totalDuration = completedJobs.reduce((sum, job) => sum + durationMs(job.startedAt, job.completedAt), 0);
    const failedCount = jobs.value.filter((job) => ['FAILED', 'CANCELED', 'TIMEOUT'].includes(job.status)).length;
    const now = Date.now();
    const slowRunningCount = jobs.value.filter((job) => {
      if (TERMINAL_STATUSES.has(job.status) || !job.startedAt) {
        return false;
      }
      return now - Date.parse(job.startedAt) > 60_000;
    }).length;
    return {
      completedCount: completedJobs.length,
      failedCount,
      averageDurationMs: completedJobs.length === 0 ? 0 : Math.round(totalDuration / completedJobs.length),
      slowRunningCount
    };
  });

  /** registerJob 처리에 필요한 데이터를 생성하거나 저장한다. */
  function registerJob(options: RegisterJobOptions) {
    const now = new Date().toISOString();
    const existing = jobs.value.find((job) => job.jobId === options.jobId);
    if (existing) {
      Object.assign(existing, {
        analysisId: options.analysisId ?? existing.analysisId,
        title: options.title,
        detail: options.detail ?? existing.detail,
        type: options.type ?? existing.type,
        updatedAt: now
      });
      return existing;
    }

    const item: JobCenterItem = {
      jobId: options.jobId,
      analysisId: options.analysisId,
      title: options.title,
      detail: options.detail,
      type: options.type,
      status: 'PENDING',
      tone: 'info',
      updatedAt: now
    };
    jobs.value = [item, ...jobs.value].slice(0, 12);
    collapsed.value = false;
    return item;
  }

  /** trackJob 처리에 필요한 화면 또는 업무 로직을 수행한다. */
  function trackJob(options: RegisterJobOptions) {
    // 등록과 폴링을 한 진입점으로 묶어 Job Center가 PENDING에 멈추는 호출 누락을 방지한다.
    registerJob(options);
    return waitForJob(options.jobId, options).catch((error: unknown) => {
      const message = error instanceof Error ? error.message : '작업 상태를 확인하지 못했습니다.';
      markJobError(options.jobId, message);
      throw error;
    });
  }

  /** updateJob 처리 대상의 상태를 갱신한다. */
  function updateJob(job: JobResponse, fallback?: Partial<JobCenterItem>) {
    const now = new Date().toISOString();
    const existing = jobs.value.find((item) => item.jobId === job.id);
    const tone: JobCenterTone = job.status === 'SUCCEEDED'
      ? 'success'
      : ['FAILED', 'CANCELED', 'TIMEOUT'].includes(job.status)
      ? 'error'
      : 'info';
    const patch: Partial<JobCenterItem> = {
      ...fallback,
      jobId: job.id,
      type: job.type,
      status: job.status,
      tone,
      createdAt: job.createdAt,
      startedAt: job.startedAt,
      completedAt: job.completedAt,
      updatedAt: now,
      errorMessage: job.errorMessage ?? job.errorCode
    };

    if (existing) {
      Object.assign(existing, patch);
      return existing;
    }

    const item: JobCenterItem = {
      title: fallback?.title ?? job.type,
      detail: fallback?.detail,
      analysisId: fallback?.analysisId,
      jobId: job.id,
      type: job.type,
      status: job.status,
      tone,
      createdAt: job.createdAt,
      startedAt: job.startedAt,
      completedAt: job.completedAt,
      updatedAt: now,
      errorMessage: job.errorMessage ?? job.errorCode
    };
    jobs.value = [item, ...jobs.value].slice(0, 12);
    return item;
  }

  /** markJobError 처리에 필요한 화면 또는 업무 로직을 수행한다. */
  function markJobError(jobId: string, message: string) {
    const existing = jobs.value.find((item) => item.jobId === jobId);
    if (!existing) {
      return;
    }
    existing.status = 'FAILED';
    existing.tone = 'error';
    existing.errorMessage = message;
    existing.completedAt = new Date().toISOString();
    existing.updatedAt = existing.completedAt;
  }

  /** markJobPollDelayed 처리에 필요한 화면 또는 업무 로직을 수행한다. */
  function markJobPollDelayed(jobId: string) {
    const existing = jobs.value.find((item) => item.jobId === jobId);
    if (!existing) return;
    existing.tone = 'info';
    existing.errorMessage = '서버 상태 조회가 지연되어 자동으로 다시 확인 중입니다.';
    existing.updatedAt = new Date().toISOString();
  }

  /** waitForJob 처리에 필요한 화면 또는 업무 로직을 수행한다. */
  async function waitForJob(jobId: string, fallback?: Partial<JobCenterItem>) {
    const existing = jobs.value.find((job) => job.jobId === jobId);
    if (existing && TERMINAL_STATUSES.has(existing.status)) {
      return api.getJob(jobId);
    }
    const activePoller = pollers.get(jobId);
    if (activePoller) {
      return activePoller;
    }

    const poller = pollJob(jobId, fallback);
    pollers.set(jobId, poller);
    try {
      return await poller;
    } finally {
      pollers.delete(jobId);
    }
  }

  /** cancelJob 처리 조건의 충족 여부를 판단한다. */
  async function cancelJob(jobId: string) {
    const job = await api.cancelJob(jobId);
    return updateJob(job);
  }

  /** retryAnalysisJob 처리에 필요한 화면 또는 업무 로직을 수행한다. */
  async function retryAnalysisJob(jobId: string) {
    const source = jobs.value.find((job) => job.jobId === jobId);
    if (!source?.analysisId) {
      throw new Error('재시도할 analysisId가 없습니다.');
    }
    const started = await api.retryAnalysis(source.analysisId);
    registerJob({
      jobId: started.jobId,
      analysisId: started.analysisId,
      title: `${source.title} 재시도`,
      detail: source.detail,
      type: source.type
    });
    return waitForJob(started.jobId, {
      analysisId: started.analysisId,
      title: `${source.title} 재시도`,
      detail: source.detail,
      type: source.type
    });
  }

  /** pollJob 처리에 필요한 화면 또는 업무 로직을 수행한다. */
  async function pollJob(jobId: string, fallback?: Partial<JobCenterItem>) {
    for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt += 1) {
      let job: JobResponse;
      try {
        job = await api.getJob(jobId);
      } catch (error) {
        if (!isRetryableJobPollError(error)) throw error;
        // 상태 조회 timeout은 서버 Job 실패가 아니므로 진행 상태를 유지하고 다음 주기에 재조회한다.
        markJobPollDelayed(jobId);
        await delay(POLL_INTERVAL_MS);
        continue;
      }
      updateJob(job, fallback);
      if (TERMINAL_STATUSES.has(job.status)) {
        return job;
      }
      await delay(POLL_INTERVAL_MS);
    }
    const message = '작업이 오래 실행 중입니다. 서버의 Job 상태를 새로고침해서 확인하세요.';
    markJobError(jobId, message);
    throw new Error(message);
  }

  /** toggleCollapsed 처리 데이터를 화면 또는 API 표현으로 변환한다. */
  function toggleCollapsed() {
    collapsed.value = !collapsed.value;
  }

  /** clearCompleted 처리 대상과 관련 상태를 안전하게 정리한다. */
  function clearCompleted() {
    jobs.value = jobs.value.filter((job) => !TERMINAL_STATUSES.has(job.status));
  }

  return {
    jobs,
    visibleJobs,
    runningJobs,
    hasJobs,
    diagnostics,
    collapsed,
    registerJob,
    trackJob,
    updateJob,
    cancelJob,
    retryAnalysisJob,
    waitForJob,
    toggleCollapsed,
    clearCompleted
  };
});

/** delay 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

/** durationMs 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function durationMs(startedAt?: string, completedAt?: string) {
  if (!startedAt || !completedAt) {
    return 0;
  }
  const duration = Date.parse(completedAt) - Date.parse(startedAt);
  return Number.isFinite(duration) && duration > 0 ? duration : 0;
}
