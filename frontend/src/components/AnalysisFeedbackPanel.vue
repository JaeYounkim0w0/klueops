<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { ApiError, api, type AnalysisFeedbackResponse } from '@/api/client';

const props = defineProps<{ analysisId: string }>();
const accuracy = ref<AnalysisFeedbackResponse['accuracy']>('PARTIAL');
const outcome = ref<AnalysisFeedbackResponse['outcome']>('NOT_TRIED');
const dangerousSuggestion = ref(false);
const comment = ref('');
const actualRootCause = ref('');
const actualResolution = ref('');
const validatedResourceKind = ref('');
const validatedResourceName = ref('');
const confidenceExpectation = ref<AnalysisFeedbackResponse['confidenceExpectation']>();
const saving = ref(false);
const message = ref('');
const error = ref('');

async function load() {
  message.value = '';
  error.value = '';
  try {
    const feedback = await api.getAnalysisFeedback(props.analysisId);
    if (!feedback) return;
    accuracy.value = feedback.accuracy;
    outcome.value = feedback.outcome;
    dangerousSuggestion.value = feedback.dangerousSuggestion;
    comment.value = feedback.comment ?? '';
    actualRootCause.value = feedback.actualRootCause ?? '';
    actualResolution.value = feedback.actualResolution ?? '';
    validatedResourceKind.value = feedback.validatedResourceKind ?? '';
    validatedResourceName.value = feedback.validatedResourceName ?? '';
    confidenceExpectation.value = feedback.confidenceExpectation;
  } catch {
    // A missing feedback record is the normal initial state.
  }
}

async function save() {
  saving.value = true;
  message.value = '';
  error.value = '';
  try {
    await api.saveAnalysisFeedback(props.analysisId, {
      accuracy: accuracy.value,
      outcome: outcome.value,
      dangerousSuggestion: dangerousSuggestion.value,
      comment: comment.value.trim() || undefined,
      actualRootCause: actualRootCause.value.trim() || undefined,
      actualResolution: actualResolution.value.trim() || undefined,
      validatedResourceKind: validatedResourceKind.value.trim() || undefined,
      validatedResourceName: validatedResourceName.value.trim() || undefined,
      confidenceExpectation: confidenceExpectation.value
    });
    message.value = '검증 결과를 저장했습니다.';
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '피드백을 저장하지 못했습니다.';
  } finally {
    saving.value = false;
  }
}

watch(() => props.analysisId, load);
onMounted(load);
</script>

<template>
  <section class="analysis-feedback-panel" aria-label="AI 분석 신뢰도 피드백">
    <header><div><span class="page-eyebrow">OPERATOR VERIFICATION</span><h3>이 분석은 실제 상황과 맞았나요?</h3><p>운영 결과는 AI 품질 지표와 회귀 검증에만 사용되며 자동으로 prompt를 변경하지 않습니다.</p></div><i class="pi pi-verified"></i></header>
    <div class="analysis-feedback-fields">
      <label><span>원인 정확도</span><select v-model="accuracy"><option value="CORRECT">정확함</option><option value="PARTIAL">일부 정확함</option><option value="INCORRECT">부정확함</option></select></label>
      <label><span>조치 결과</span><select v-model="outcome"><option value="NOT_TRIED">아직 조치하지 않음</option><option value="RESOLVED">해결됨</option><option value="IMPROVED">개선됨</option><option value="NO_CHANGE">변화 없음</option><option value="WORSE">악화됨</option></select></label>
      <label class="feedback-danger-check"><input v-model="dangerousSuggestion" type="checkbox"><span>위험하거나 실행하면 안 되는 제안이 있었음</span></label>
      <label class="wide"><span>검증 메모</span><textarea v-model="comment" maxlength="2000" rows="3" placeholder="맞았던 근거, 누락된 원인, 실제 해결 방법"></textarea></label>
      <details class="wide feedback-ground-truth">
        <summary>실제 원인과 해결 방법 기록</summary>
        <p>확인된 사실을 남기면 모델과 prompt 버전별 정확도를 운영 데이터로 보정할 수 있습니다.</p>
        <div class="analysis-feedback-fields">
          <label class="wide"><span>확인된 실제 원인</span><textarea v-model="actualRootCause" maxlength="2000" rows="2" placeholder="예: Service targetPort와 컨테이너 listen port 불일치"></textarea></label>
          <label class="wide"><span>실제 해결 방법</span><textarea v-model="actualResolution" maxlength="2000" rows="2" placeholder="예: Deployment containerPort와 Service targetPort를 8080으로 통일"></textarea></label>
          <label><span>검증 리소스 종류</span><input v-model="validatedResourceKind" maxlength="100" placeholder="Deployment"></label>
          <label><span>검증 리소스 이름</span><input v-model="validatedResourceName" maxlength="255" placeholder="nginx"></label>
          <label><span>기대 신뢰 수준</span><select v-model="confidenceExpectation"><option :value="undefined">선택 안 함</option><option value="HIGH">높음</option><option value="MEDIUM">보통</option><option value="LOW">낮음</option></select></label>
        </div>
      </details>
    </div>
    <footer><span v-if="message" class="feedback-success"><i class="pi pi-check-circle"></i>{{ message }}</span><span v-if="error" class="feedback-error"><i class="pi pi-exclamation-triangle"></i>{{ error }}</span><button class="secondary-button" type="button" :disabled="saving" @click="save"><i class="pi pi-save"></i><span>{{ saving ? '저장 중' : '검증 결과 저장' }}</span></button></footer>
  </section>
</template>
