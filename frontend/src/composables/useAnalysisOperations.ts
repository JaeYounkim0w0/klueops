import { api, type AnalysisResponse } from '@/api/client';
import type { useJobCenterStore } from '@/stores/jobCenter';

type JobCenter = ReturnType<typeof useJobCenterStore>;

export function useAnalysisOperations(jobCenter: JobCenter) {
  async function waitForAnalysisJob(jobId: string, analysisId: string): Promise<AnalysisResponse> {
    const job = await jobCenter.waitForJob(jobId, { analysisId });
    if (job.status === 'SUCCEEDED') {
      return api.getAnalysisByJobId(jobId);
    }
    try {
      const failedAnalysis = await api.getAnalysisByJobId(jobId);
      if (failedAnalysis.status === 'FAILED') {
        return failedAnalysis;
      }
    } catch {
      // Preserve the job's authoritative failure when no result record is available.
    }
    throw new Error(job.errorMessage || job.errorCode || 'AI 분석 작업이 실패했습니다.');
  }

  return { waitForAnalysisJob };
}
