<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ApplicationDeliveryNav from '@/components/application/ApplicationDeliveryNav.vue';
import { api, type ValuesProfileResponse, type ValuesSuggestionResponse } from '@/api/client';
import { useTenancyStore } from '@/stores/tenancy';

const route = useRoute();
const router = useRouter();
const tenancy = useTenancyStore();
const chartVersionId = computed(() => String(route.params.chartVersionId));
const chartName = computed(() => String(route.query.chart || 'Helm Chart'));
const profiles = ref<ValuesProfileResponse[]>([]);
const selectedProfileId = ref('');
const profileName = ref('default');
const EMPTY_CUSTOM_VALUES = '{}\n';
const valuesYaml = ref(EMPTY_CUSTOM_VALUES);
const saving = ref(false);
const message = ref('');
const assistantOpen = ref(false);
const aiInstruction = ref('');
const aiSuggestion = ref('');
const aiResult = ref<ValuesSuggestionResponse>();
const assistantMessage = ref('');
const suggesting = ref(false);
let profileLoadSequence = 0;

onMounted(async () => { await tenancy.load(); profiles.value = await api.listValuesProfiles(tenancy.currentTenantId, chartVersionId.value); selectedProfileId.value = profiles.value[0]?.id || ''; });

watch(selectedProfileId, async (profileId) => {
  const sequence = ++profileLoadSequence;
  message.value = '';
  if (!profileId) {
    valuesYaml.value = EMPTY_CUSTOM_VALUES;
    return;
  }
  try {
    const revisions = await api.listValuesRevisions(tenancy.currentTenantId, profileId);
    if (sequence !== profileLoadSequence || revisions.length === 0) return;
    const payload = await api.getValuesRevisionValues(tenancy.currentTenantId, revisions[0].id);
    // 비동기 선택 변경이 뒤늦게 도착해 현재 편집 내용을 덮어쓰지 않도록 요청 순서를 확인한다.
    if (sequence === profileLoadSequence) valuesYaml.value = payload.valuesYaml;
  } catch (error) {
    if (sequence === profileLoadSequence)
      message.value = error instanceof Error ? error.message : 'Values revision을 불러오지 못했습니다.';
  }
});

async function saveAndContinue(): Promise<void> {
  saving.value = true;
  try {
    let profileId = selectedProfileId.value;
    if (!profileId) {
      const profile = await api.createValuesProfile({ tenantId: tenancy.currentTenantId, chartVersionId: chartVersionId.value, name: profileName.value, description: `${chartName.value} custom values` });
      profileId = profile.id;
    }
    const revision = await api.createValuesRevision(tenancy.currentTenantId, profileId, valuesYaml.value);
    await router.push({ path: `/applications/deploy/${chartVersionId.value}`, query: {
      chart: chartName.value, valuesRevisionId: revision.id,
      upgradeApplicationId: route.query.upgradeApplicationId,
      clusterId: route.query.clusterId, namespace: route.query.namespace, releaseName: route.query.releaseName,
    } });
  } catch (error) { message.value = error instanceof Error ? error.message : 'Values를 저장하지 못했습니다.'; }
  finally { saving.value = false; }
}

async function suggestValues(): Promise<void> {
  if (!aiInstruction.value.trim()) return;
  suggesting.value = true;
  assistantMessage.value = '';
  aiResult.value = undefined;
  try {
    const result = await api.suggestValues({ tenantId: tenancy.currentTenantId, chartVersionId: chartVersionId.value,
      currentValuesYaml: valuesYaml.value, instruction: aiInstruction.value });
    aiSuggestion.value = result.valuesYaml;
    aiResult.value = result;
  } catch (error) {
    aiSuggestion.value = '';
    assistantMessage.value = error instanceof Error ? error.message : 'AI Values 제안을 생성하지 못했습니다.';
  }
  finally { suggesting.value = false; }
}

function applySuggestion(): void {
  valuesYaml.value = aiSuggestion.value;
  aiSuggestion.value = '';
  assistantOpen.value = false;
  message.value = 'AI 제안을 편집기에 적용했습니다. 저장 전 내용을 직접 검토하세요.';
}
</script>

<template>
  <section class="page delivery-page">
    <header class="delivery-hero compact"><div><span class="delivery-eyebrow">VALUES STUDIO</span><h1>{{ chartName }}</h1><p>원본 Chart는 그대로 유지하고 Custom Values revision만 암호화해 저장합니다.</p></div><RouterLink to="/applications/library" class="secondary-button"><i class="pi pi-arrow-left"></i> Library</RouterLink></header>
    <ApplicationDeliveryNav />
    <div class="wizard-steps"><span class="done">1 Chart</span><span class="active">2 Values</span><span>3 Target</span><span>4 Preview</span></div>
    <div v-if="message" class="delivery-notice error">{{ message }}</div>
    <div class="values-workspace">
      <aside class="values-sidebar"><h2>Values Profile</h2><label>기존 Profile<select v-model="selectedProfileId"><option value="">새 Profile</option><option v-for="profile in profiles" :key="profile.id" :value="profile.id">{{ profile.name }}</option></select></label><label v-if="!selectedProfileId">새 이름<input v-model="profileName" maxlength="255" /></label><div class="values-hint"><i class="pi pi-lock"></i><p><strong>Secret 보호</strong><br />Values 원문은 암호화되며 목록·Audit에는 digest만 노출됩니다.</p></div></aside>
      <section class="yaml-editor-panel"><header><div><strong>values.yaml</strong><small>YAML object · 최대 1 MiB</small></div><div class="header-actions"><button class="secondary-button" type="button" @click="assistantOpen = true"><i class="pi pi-sparkles"></i> AI로 Values 제안</button><span class="delivery-badge">Revision 생성</span></div></header><textarea v-model="valuesYaml" spellcheck="false" aria-label="Custom Values YAML"></textarea><footer><span>{{ valuesYaml.length.toLocaleString() }} characters</span><button class="primary-button" type="button" :disabled="saving" @click="saveAndContinue">{{ saving ? '검증 및 저장 중…' : 'Revision 저장 후 Target 선택' }} <i class="pi pi-arrow-right"></i></button></footer></section>
    </div>
    <div v-if="assistantOpen" class="delivery-modal-backdrop" @click.self="assistantOpen = false"><section class="delivery-modal" role="dialog" aria-modal="true"><header><div><span class="delivery-eyebrow">CHART-AWARE AI SUGGESTION</span><h2>Custom Values 도우미</h2><p>선택한 Chart의 제공사·Chart/App 버전·기본 Values·Schema를 기준으로 제안하고, 실제 Helm 렌더링을 통과한 결과만 표시합니다. Secret 값은 AI에 보내지 않습니다.</p></div><button class="icon-button" type="button" @click="assistantOpen = false"><i class="pi pi-times"></i></button></header><div v-if="assistantMessage" class="delivery-notice error assistant-notice" role="alert">{{ assistantMessage }}</div><label class="delivery-field">요청 내용<textarea v-model="aiInstruction" maxlength="2000" placeholder="예: replica를 3개로 늘리고 Service는 ClusterIP로 유지하며 HTTP 포트를 80으로 설정해줘"></textarea></label><div class="delivery-assistant-actions"><button class="secondary-button" type="button" :disabled="suggesting || !aiInstruction.trim()" @click="suggestValues">{{ suggesting ? 'Chart 계약 검증 중…' : '제안 생성' }}</button></div><div v-if="aiResult" class="assistant-validation-summary"><span class="status-dot success">Helm 검증 완료</span><span>{{ aiResult.chartName }} · {{ aiResult.providerName || '직접 등록' }} · Chart {{ aiResult.chartVersion }}<template v-if="aiResult.applicationVersion"> · App {{ aiResult.applicationVersion }}</template></span><small>{{ aiResult.promptVersion }} · {{ aiResult.attempts }}회 생성 · Schema {{ aiResult.schemaIncluded ? '참조' : '미제공' }}</small></div><label v-if="aiSuggestion" class="delivery-field">검토할 제안<textarea v-model="aiSuggestion" class="suggestion-preview" spellcheck="false"></textarea></label><footer><button class="secondary-button" type="button" @click="assistantOpen = false">취소</button><button class="primary-button" type="button" :disabled="!aiSuggestion" @click="applySuggestion">검토한 제안을 편집기에 적용</button></footer></section></div>
  </section>
</template>
