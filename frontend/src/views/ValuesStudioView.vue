<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { dump, load } from "js-yaml";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import ApplicationDeliveryNav from "@/components/application/ApplicationDeliveryNav.vue";
import {
  api,
  type ValuesProfileResponse,
  type ValuesSuggestionResponse,
} from "@/api/client";
import { useTenancyStore } from "@/stores/tenancy";
import ValuesSchemaForm from "@/components/application/ValuesSchemaForm.vue";
import { flattenValuesSchema } from "@/utils/valuesSchema";
import { reviewNodePortValues } from "@/utils/helmValues";
import { valuesDigest, previewValuesChanges } from "@/utils/valuesAssistance";
import { useJobCenterStore } from "@/stores/jobCenter";

const route = useRoute();
const router = useRouter();
const tenancy = useTenancyStore();
const jobCenter = useJobCenterStore();
const chartVersionId = computed(() => String(route.params.chartVersionId));
const chartName = computed(() => String(route.query.chart || "Helm Chart"));
const profiles = ref<ValuesProfileResponse[]>([]);
const selectedProfileId = ref("");
const profileName = ref("default");
const EMPTY_CUSTOM_VALUES = '{}\n';
const valuesYaml = ref(EMPTY_CUSTOM_VALUES);
const saving = ref(false);
const message = ref("");
const chartEligibilityMessage = ref("");
const assistantOpen = ref(false);
const aiInstruction = ref("");
const aiSuggestion = ref("");
const aiResult = ref<ValuesSuggestionResponse>();
const proposalChanges = computed(() => previewValuesChanges(valuesYaml.value, aiSuggestion.value));
const assistantMessage = ref("");
const suggesting = ref(false);
const suggestionContext = ref("");
const editorMode = ref<"FORM" | "YAML">("YAML");
const schemaFields = ref<ReturnType<typeof flattenValuesSchema>>([]);
const formValues = ref<Record<string, unknown>>({});
const yamlError = ref("");
const valuesErrors = ref<string[]>([]);
const valuesWarnings = ref<string[]>([]);
let profileLoadSequence = 0;

/** 페이지 재진입 시 이전 도우미 결과와 오류가 새 작업에 섞이지 않도록 초기화한다. */
function resetAssistantState(clearInstruction = true): void {
  assistantOpen.value = false;
  assistantMessage.value = "";
  aiSuggestion.value = "";
  aiResult.value = undefined;
  suggestionContext.value = "";
  suggesting.value = false;
  if (clearInstruction) aiInstruction.value = "";
}

onMounted(async () => {
  // 라우터가 같은 컴포넌트를 재사용하는 경우에도 직전 실패 상태를 남기지 않는다.
  resetAssistantState();
  await tenancy.load();
  const [profileList, contract] = await Promise.all([
    api.listValuesProfiles(tenancy.currentTenantId, chartVersionId.value),
    api.getValuesContract(tenancy.currentTenantId, chartVersionId.value),
  ]);
  profiles.value = profileList;
  chartEligibilityMessage.value = contract.eligibilityMessage || "";
  schemaFields.value = flattenValuesSchema(contract.valuesSchemaJson);
  if (schemaFields.value.length) {
    editorMode.value = "FORM";
    syncForm(contract.defaultValuesYaml || EMPTY_CUSTOM_VALUES);
  }
  selectedProfileId.value = profiles.value[0]?.id || "";
  if (typeof route.query.valuesAssistantJobId === "string") {
    assistantOpen.value = true;
    await resumeSuggestion(route.query.valuesAssistantJobId);
  }
});

watch(chartVersionId, (next, previous) => {
  if (next !== previous) resetAssistantState();
});

onBeforeRouteLeave(() => {
  // 뒤로가기·다른 메뉴 이동 후 다시 진입할 때 이전 오류/요청 분석을 복원하지 않는다.
  resetAssistantState();
});

watch(selectedProfileId, async (profileId) => {
  const sequence = ++profileLoadSequence;
  message.value = "";
  if (!profileId) {
    valuesYaml.value = EMPTY_CUSTOM_VALUES;
    return;
  }
  try {
    const revisions = await api.listValuesRevisions(
      tenancy.currentTenantId,
      profileId,
    );
    if (sequence !== profileLoadSequence || revisions.length === 0) return;
    const payload = await api.getValuesRevisionValues(
      tenancy.currentTenantId,
      revisions[0].id,
    );
    // 비동기 선택 변경이 뒤늦게 도착해 현재 편집 내용을 덮어쓰지 않도록 요청 순서를 확인한다.
    if (sequence === profileLoadSequence) {
      valuesYaml.value = payload.valuesYaml;
      syncForm(payload.valuesYaml);
    }
  } catch (error) {
    if (sequence === profileLoadSequence)
      message.value =
        error instanceof Error
          ? error.message
          : "Values revision을 불러오지 못했습니다.";
  }
});

/** YAML을 object로 검증해 Form과 동기화한다. */
function syncForm(yaml = valuesYaml.value): void {
  try {
    const parsed = load(yaml);
    if (parsed != null && (typeof parsed !== "object" || Array.isArray(parsed)))
      throw new Error("최상위 Values는 object여야 합니다.");
    formValues.value = (parsed || {}) as Record<string, unknown>;
    const review = reviewNodePortValues(formValues.value);
    valuesErrors.value = review.errors;
    valuesWarnings.value = review.warnings;
    yamlError.value = "";
  } catch (error) {
    valuesErrors.value = [];
    valuesWarnings.value = [];
    yamlError.value =
      error instanceof Error ? error.message : "YAML 문법을 확인하세요.";
  }
}

/** Form 변경 내용을 안정적인 YAML로 직렬화한다. */
function updateFromForm(value: Record<string, unknown>): void {
  formValues.value = value;
  valuesYaml.value = dump(value, {
    noRefs: true,
    lineWidth: 120,
    sortKeys: false,
  });
  const review = reviewNodePortValues(value);
  valuesErrors.value = review.errors;
  valuesWarnings.value = review.warnings;
  yamlError.value = "";
}

/** 편집 모드 전환 전에 YAML 유효성을 확인한다. */
function switchMode(mode: "FORM" | "YAML"): void {
  if (mode === "FORM") syncForm();
  if (!yamlError.value || mode === "YAML") editorMode.value = mode;
}

/** saveAndContinue 처리에 필요한 데이터를 생성하거나 저장한다. */
async function saveAndContinue(): Promise<void> {
  saving.value = true;
  try {
    let profileId = selectedProfileId.value;
    if (!profileId) {
      const profile = await api.createValuesProfile({
        tenantId: tenancy.currentTenantId,
        chartVersionId: chartVersionId.value,
        name: profileName.value,
        description: `${chartName.value} custom values`,
      });
      profileId = profile.id;
    }
    const revision = await api.createValuesRevision(
      tenancy.currentTenantId,
      profileId,
      valuesYaml.value,
    );
    await router.push({
      path: `/applications/deploy/${chartVersionId.value}`,
      query: {
        chart: chartName.value,
        valuesRevisionId: revision.id,
        upgradeApplicationId: route.query.upgradeApplicationId,
        clusterId: route.query.clusterId,
        namespace: route.query.namespace,
        releaseName: route.query.releaseName,
      },
    });
  } catch (error) {
    message.value =
      error instanceof Error ? error.message : "Values를 저장하지 못했습니다.";
  } finally {
    saving.value = false;
  }
}

/** 요청과 편집 대상이 바뀐 제안을 현재 Values에 적용하지 않도록 비교 기준을 만든다. */
function currentSuggestionContext(): string {
  return JSON.stringify([tenancy.currentTenantId, chartVersionId.value, selectedProfileId.value,
    valuesYaml.value, aiInstruction.value.trim()]);
}

/** 자연어를 해석하고 정확한 Chart 매핑 또는 입력 보완 안내를 가져온다. */
async function suggestValues(): Promise<void> {
  if (suggesting.value || !aiInstruction.value.trim()) return;
  suggesting.value = true;
  assistantMessage.value = "";
  aiResult.value = undefined;
  aiSuggestion.value = "";
  const requestContext = currentSuggestionContext();
  try {
    const started = await api.startValuesAssistance({
      tenantId: tenancy.currentTenantId,
      chartVersionId: chartVersionId.value,
      // 빈 override도 Helm에서는 유효하므로 API 경계에서 명시적인 빈 mapping으로 전달한다.
      currentValuesYaml: valuesYaml.value.trim()
        ? valuesYaml.value
        : "{}",
      instruction: aiInstruction.value.trim(),
    });
    await router.replace({ query: { ...route.query, valuesAssistantJobId: started.jobId } });
    const result = await awaitSuggestion(started.jobId);
    if (requestContext !== currentSuggestionContext()) {
      assistantMessage.value = "생성 중 요청이나 편집 대상이 변경되었습니다. 현재 내용으로 다시 생성해 주세요.";
      return;
    }
    suggestionContext.value = requestContext;
    aiSuggestion.value = result.valuesYaml;
    aiResult.value = result;
  } catch (error) {
    aiSuggestion.value = "";
    assistantMessage.value =
      error instanceof Error
        ? error.message
        : "AI Values 제안을 생성하지 못했습니다.";
  } finally {
    suggesting.value = false;
  }
}

/** 전역 Job Center에서 생성 상태를 추적하고 짧은 결과 조회 요청으로 완료 내용을 가져온다. */
async function awaitSuggestion(jobId: string): Promise<ValuesSuggestionResponse> {
  const tenantId = tenancy.currentTenantId;
  const job = await jobCenter.trackJob({ jobId, title: "AI Custom Values 생성", detail: chartName.value, type: "HELM_VALUES" },
    () => api.getValuesAssistanceJob(tenantId, jobId));
  // 기술 오류로 실패한 Job은 결과 payload가 없으므로 digest 검증 경로로 진입하지 않는다.
  if (job.status !== "SUCCEEDED" && job.errorCode !== "VALUES_GENERATION_FAILED")
    throw new Error(job.errorMessage || "Values 생성 작업이 종료되었습니다.");
  const outcome = await api.getValuesAssistanceResult(tenantId, jobId);
  // 검증 실패 결과와 기술 실패 결과를 구분해 원인 없이 undefined를 읽지 않도록 한다.
  if (!outcome?.proposal) {
    throw new Error(job.errorMessage || "Values 생성 결과가 없어 적용할 수 없습니다.");
  }
  // 생성 검증 실패는 제안 상세와 질문을 보여주기만 하므로 원본 digest 검증을 건너뛴다.
  // 실패 결과에서 브라우저 Web Crypto를 호출하면 실제 원인 대신 digest 오류가 덮어써질 수 있다.
  if (outcome.proposal.validationStatus === "GENERATION_FAILED") return outcome.proposal;
  const currentDigest = await valuesDigest(valuesYaml.value);
  if (currentDigest && outcome.baseValuesDigest && currentDigest !== outcome.baseValuesDigest)
    throw new Error("생성 시작 시점과 현재 Values가 다릅니다. 원본 Profile/Revision을 선택하거나 현재 내용으로 다시 생성해 주세요.");
  if (!currentDigest)
    assistantMessage.value = "현재 브라우저 보안 컨텍스트에서는 원본 Values 지문 검사를 사용할 수 없어 결과를 적용할 때 수동 검토가 필요합니다.";
  return outcome.proposal;
}

/** URL에 남긴 Job ID로 새로고침 이후에도 안전하게 완료 결과를 다시 조회한다. */
async function resumeSuggestion(jobId: string): Promise<void> {
  suggesting.value = true;
  try {
    const result = await awaitSuggestion(jobId);
    aiResult.value = result;
    aiSuggestion.value = result.valuesYaml;
    suggestionContext.value = currentSuggestionContext();
  } catch (error) {
    assistantMessage.value = error instanceof Error ? error.message : "이전 생성 작업을 확인하지 못했습니다.";
  } finally { suggesting.value = false; }
}

/** applySuggestion 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function applySuggestion(): void {
  if (suggesting.value || aiResult.value?.validationStatus !== "HELM_TEMPLATE_VALIDATED") return;
  if (suggestionContext.value !== currentSuggestionContext()) {
    assistantMessage.value = "요청이나 원본 Values가 변경되었습니다. 현재 내용으로 다시 생성해 주세요.";
    return;
  }
  valuesYaml.value = aiSuggestion.value;
  syncForm(aiSuggestion.value);
  aiSuggestion.value = "";
  assistantOpen.value = false;
  message.value =
    "AI 제안을 편집기에 적용했습니다. 저장 전 내용을 직접 검토하세요.";
}
</script>

<template>
  <section class="page delivery-page">
    <header class="delivery-hero compact">
      <div>
        <span class="delivery-eyebrow">VALUES STUDIO</span>
        <h1>{{ chartName }}</h1>
        <p>
          원본 Chart는 그대로 유지하고 Custom Values revision만 암호화해
          저장합니다.
        </p>
      </div>
      <RouterLink to="/applications/library" class="secondary-button"
        ><i class="pi pi-arrow-left"></i> Library</RouterLink
      >
    </header>
    <ApplicationDeliveryNav />
    <div class="wizard-steps">
      <span class="done">1 Chart</span><span class="active">2 Values</span
      ><span>3 Target</span><span>4 Preview</span>
    </div>
    <div v-if="message" class="delivery-notice error">{{ message }}</div>
    <div class="values-workspace">
      <aside class="values-sidebar">
        <h2>Values Profile</h2>
        <label
          >기존 Profile<select v-model="selectedProfileId">
            <option value="">새 Profile</option>
            <option
              v-for="profile in profiles"
              :key="profile.id"
              :value="profile.id"
            >
              {{ profile.name }}
            </option>
          </select></label
        ><label v-if="!selectedProfileId"
          >새 이름<input v-model="profileName" maxlength="255"
        /></label>
        <div class="values-hint">
          <i class="pi pi-lock"></i>
          <p>
            <strong>Secret 보호</strong><br />Values 원문은 암호화되며
            목록·Audit에는 digest만 노출됩니다.
          </p>
        </div>
      </aside>
      <section class="yaml-editor-panel">
        <header>
          <div class="values-editor-heading">
            <strong>Custom values</strong
            ><small>Schema Form ↔ YAML 양방향 동기화 · 최대 1 MiB</small>
          </div>
          <div class="header-actions values-editor-actions">
            <div
              v-if="schemaFields.length"
              class="values-mode-switch"
              role="group"
              aria-label="Values 편집 방식"
            >
              <button
                type="button"
                :class="{ active: editorMode === 'FORM' }"
                :aria-pressed="editorMode === 'FORM'"
                @click="switchMode('FORM')"
              >
                Form</button
              ><button
                type="button"
                :class="{ active: editorMode === 'YAML' }"
                :aria-pressed="editorMode === 'YAML'"
                @click="switchMode('YAML')"
              >
                YAML
              </button>
            </div>
            <button
              class="secondary-button"
              type="button"
              @click="assistantOpen = true"
            >
              <i class="pi pi-sparkles"></i> AI로 Values 제안</button
            ><span class="delivery-badge">Revision 생성</span>
          </div>
        </header>
        <ValuesSchemaForm
          v-if="editorMode === 'FORM' && schemaFields.length"
          :fields="schemaFields"
          :model-value="formValues"
          @update:model-value="updateFromForm"
        /><textarea
          v-else
          v-model="valuesYaml"
          spellcheck="false"
          aria-label="Custom Values YAML"
          @blur="syncForm()"
        ></textarea>
        <div v-if="yamlError" class="delivery-notice error" role="alert">
          {{ yamlError }}
        </div>
        <div v-if="valuesErrors.length" class="delivery-notice error" role="alert">
          <strong>Custom Values를 수정해야 합니다.</strong>
          <span v-for="error in valuesErrors" :key="error">{{ error }}</span>
        </div>
        <div v-if="valuesWarnings.length" class="delivery-notice" role="status">
          <strong>Service 노출 설정을 확인하세요.</strong>
          <span v-for="warning in valuesWarnings" :key="warning">{{ warning }}</span>
        </div>
        <footer>
          <span
            >{{ valuesYaml.length.toLocaleString() }} characters ·
            {{
              schemaFields.length
                ? `${schemaFields.length} schema fields`
                : "Schema 미제공: YAML 편집"
            }}</span
          ><button
            class="primary-button"
            type="button"
            :disabled="saving || Boolean(yamlError) || Boolean(valuesErrors.length)"
            @click="saveAndContinue"
          >
            {{ saving ? "검증 및 저장 중…" : "Revision 저장 후 Target 선택" }}
            <i class="pi pi-arrow-right"></i>
          </button>
        </footer>
      </section>
    </div>
    <div
      v-if="assistantOpen"
      class="delivery-modal-backdrop"
      @click.self="assistantOpen = false"
    >
      <section class="delivery-modal" role="dialog" aria-modal="true">
        <header>
          <div>
            <span class="delivery-eyebrow">CHART-AWARE AI SUGGESTION</span>
            <h2>Custom Values 도우미</h2>
            <p>
              선택한 Chart의 기본 Values와 설명을 읽어 요청한 설정을 제안합니다.
              YAML·Helm 검증 후 변경 내용과 확인 범위를 함께 표시합니다.
              Secret 값은 AI에 보내지 않습니다.
            </p>
          </div>
          <button
            class="icon-button"
            type="button"
            @click="assistantOpen = false"
          >
            <i class="pi pi-times"></i>
          </button>
        </header>
        <p class="delivery-inline-notice warning values-ai-caution" role="note">
          <i class="pi pi-exclamation-triangle"></i>
          AI Values 제안은 해당 Helm Chart의 Values와 Schema를 기반으로 생성됩니다.
          Chart 버전·제공사에 따라 지원 경로와 기본값이 다를 수 있으므로, 적용 전 YAML과 Helm 검증 결과를 반드시 확인하세요.
          제안 성공은 실제 Cluster 배포 성공을 보장하지 않습니다.
        </p>
        <div
          v-if="assistantMessage"
          class="delivery-notice error assistant-notice"
          role="alert"
        >
          {{ assistantMessage }}
        </div>
        <label class="delivery-field"
          >요청 내용<textarea
            v-model="aiInstruction"
            maxlength="2000"
            placeholder="예: replica를 3개로 늘리고 Service는 ClusterIP로 유지하며 HTTP 포트를 80으로 설정해줘"
          ></textarea>
        </label>
        <p v-if="chartEligibilityMessage" class="delivery-inline-notice warning" role="alert">{{ chartEligibilityMessage }}</p>
        <div class="delivery-assistant-actions">
          <button
            class="secondary-button"
            type="button"
            :disabled="suggesting || !aiInstruction.trim() || !!chartEligibilityMessage"
            @click="suggestValues"
          >
            {{ suggesting ? "Chart 자료 확인·생성·검증 중…" : "요청 분석 및 제안 생성" }}
          </button>
        </div>
        <div v-if="aiResult" class="assistant-validation-summary" :class="{ 'is-warning': aiResult.validationStatus === 'NEEDS_INPUT', 'is-error': aiResult.validationStatus === 'GENERATION_FAILED' }">
          <span class="status-dot" :class="aiResult.validationStatus === 'HELM_TEMPLATE_VALIDATED' ? 'success' : aiResult.validationStatus === 'NEEDS_INPUT' ? 'warning' : 'danger'">{{ aiResult.validationStatus === 'HELM_TEMPLATE_VALIDATED' ? 'Chart 렌더링·Kubernetes 기본 규칙 검증 완료' : aiResult.validationStatus === 'NEEDS_INPUT' ? '추가 입력이 필요합니다' : 'AI 제안을 완료하지 못했습니다' }}</span
          ><span
            >{{ aiResult.chartName }} ·
            {{ aiResult.providerName || "직접 등록" }} · Chart
            {{ aiResult.chartVersion
            }}<template v-if="aiResult.applicationVersion">
              · App {{ aiResult.applicationVersion }}</template
            ></span
          ><small
            >{{ aiResult.attempts }}회 AI 처리 ·
            Schema {{ aiResult.schemaIncluded ? "참조" : "미제공" }}</small
          >
        </div>
        <section v-if="aiResult?.requirements?.length" class="delivery-panel">
          <h3>이해한 요청</h3>
          <ul><li v-for="item in aiResult.requirements" :key="item.id">{{ item.request }}</li></ul>
        </section>
        <div v-for="question in aiResult?.questions || []" :key="question" class="delivery-inline-notice warning" role="status">
          {{ question }}
        </div>
        <p v-if="aiResult?.validationStatus === 'NEEDS_INPUT'">위 내용을 참고하여 요청 내용을 수정한 뒤 다시 생성해 주세요. 현재 편집 중인 Values는 유지됩니다.</p>
        <section v-if="aiResult?.changes?.length" class="delivery-panel">
          <h3>요청과 Chart 설정 연결</h3>
          <ul><li v-for="change in aiResult.changes" :key="change.path"><code>{{ change.path }}</code> — {{ change.explanation }}</li></ul>
        </section>
        <section v-if="proposalChanges.length" class="delivery-panel">
          <h3>현재 Values와 비교</h3>
          <div class="table-panel table-panel--compact">
            <table>
              <thead><tr><th>설정</th><th>현재 override</th><th>제안 override</th></tr></thead>
              <tbody><tr v-for="change in proposalChanges" :key="change.path">
                <td><code>{{ change.path }}</code></td><td>{{ change.before }}</td><td>{{ change.after }}</td>
              </tr></tbody>
            </table>
          </div>
        </section>
        <div
          v-for="warning in aiResult?.warnings || []"
          :key="warning"
          class="delivery-inline-notice warning"
        >
          <i class="pi pi-shield"></i><span>{{ warning }}</span>
        </div>
        <label v-if="aiSuggestion" class="delivery-field"
          >검토할 제안<textarea
            v-model="aiSuggestion"
            class="suggestion-preview"
            readonly
            spellcheck="false"
          ></textarea>
        </label>
        <footer>
          <button
            class="secondary-button"
            type="button"
            @click="assistantOpen = false"
          >
            취소</button
          ><button
            class="primary-button"
            type="button"
            :disabled="suggesting || !aiSuggestion || aiResult?.validationStatus !== 'HELM_TEMPLATE_VALIDATED'"
            @click="applySuggestion"
          >
            검토한 제안을 편집기에 적용
          </button>
        </footer>
      </section>
    </div>
  </section>
</template>
