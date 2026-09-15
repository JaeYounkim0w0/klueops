<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { api, type AiProviderProfileResponse, type AiRoutingResponse, type LocalAiModelResponse } from '@/api/client';
import { useJobCenterStore } from '@/stores/jobCenter';
import { useAuthStore } from '@/stores/auth';
import { useTenancyStore } from '@/stores/tenancy';

const auth = useAuthStore();
const tenancy = useTenancyStore();
const jobs = useJobCenterStore();
const providers = ref<AiProviderProfileResponse[]>([]);
const routing = ref<AiRoutingResponse[]>([]);
const profileOpen = ref(false);
const editingProfileId = ref('');
const saving = ref(false);
const message = ref('');
const localModels = ref<Record<string, LocalAiModelResponse[]>>({});
const pullingModel = ref<Record<string, string>>({});
const form = reactive({ name: '', providerType: 'OLLAMA', baseUrl: 'http://host.docker.internal:11434', apiKey: '', defaultModel: 'qwen2.5-coder:7b', allowedModels: 'qwen2.5-coder:7b', enabled: true, externalDataTransfer: false });
const purposes = [
  { id: 'ANALYSIS', title: 'AI Analysis', description: 'Kubernetes 증거 기반 RCA와 structured result' },
  { id: 'CHAT', title: 'AI Chat', description: '운영 대화와 후속 질의' },
  { id: 'HELM_VALUES', title: 'Helm Values', description: '스키마 범위 안의 Values 제안' },
] as const;
const routeForms = reactive<Record<string, { profileId: string; model: string; external: boolean }>>({});
const providerMap = computed(() => Object.fromEntries(providers.value.map((item) => [item.id, item])));

onMounted(async () => { await tenancy.load(); await load(); });

async function load(): Promise<void> {
  [providers.value, routing.value] = await Promise.all([api.listAiProviderProfiles(tenancy.currentTenantId), api.listAiRouting(tenancy.currentTenantId)]);
  for (const purpose of purposes) {
    const current = routing.value.find((item) => item.purpose === purpose.id);
    routeForms[purpose.id] = { profileId: current?.primaryProfileId || providers.value[0]?.id || '', model: current?.model || providers.value[0]?.defaultModel || '', external: current?.externalTransferAllowed || false };
  }
}

function selectProfile(purpose: string): void {
  const selected = providerMap.value[routeForms[purpose].profileId];
  routeForms[purpose].model = selected?.defaultModel || '';
  if (selected?.providerType === 'OLLAMA') routeForms[purpose].external = false;
}

async function saveRouting(purpose: string): Promise<void> {
  const value = routeForms[purpose];
  await api.saveAiRouting(purpose, { tenantId: tenancy.currentTenantId, primaryProfileId: value.profileId,
    model: value.model, externalTransferAllowed: value.external, maximumContextChars: 60000, maximumOutputTokens: 4096 });
  message.value = `${purpose} 라우팅을 저장했습니다.`;
}

async function validateProvider(provider: AiProviderProfileResponse): Promise<void> {
  const result = await api.validateAiProviderProfile(tenancy.currentTenantId, provider.id);
  message.value = result.message;
  await load();
}

async function refreshModels(provider: AiProviderProfileResponse): Promise<void> {
  try {
    localModels.value[provider.id] = await api.refreshLocalAiModels(tenancy.currentTenantId, provider.id);
    message.value = `${provider.name}의 설치 모델을 동기화했습니다.`;
  } catch (error) { message.value = error instanceof Error ? error.message : '모델 목록을 동기화하지 못했습니다.'; }
}

async function pullModel(provider: AiProviderProfileResponse): Promise<void> {
  const model = pullingModel.value[provider.id];
  if (!model) return;
  try {
    const accepted = await api.pullLocalAiModel(tenancy.currentTenantId, provider.id, model);
    void jobs.trackJob({ jobId: accepted.jobId, title: 'Local LLM 다운로드', detail: model, type: 'AI_MODEL_PULL' })
      .then(() => refreshModels(provider))
      .catch(() => undefined);
    message.value = `${model} 다운로드를 시작했습니다. Job Center에서 상태를 확인하세요.`;
    localModels.value[provider.id] = await api.listLocalAiModels(tenancy.currentTenantId, provider.id);
  } catch (error) { message.value = error instanceof Error ? error.message : '모델 다운로드를 시작하지 못했습니다.'; }
}

function providerChanged(): void {
  if (form.providerType === 'OLLAMA') { form.baseUrl = 'http://host.docker.internal:11434'; form.externalDataTransfer = false; }
  else if (form.providerType === 'OPENAI') { form.baseUrl = 'https://api.openai.com'; form.externalDataTransfer = true; }
  else if (form.providerType === 'GOOGLE_GENAI') { form.baseUrl = 'https://generativelanguage.googleapis.com'; form.externalDataTransfer = true; }
}

function openCreate(): void {
  editingProfileId.value = '';
  Object.assign(form, { name: '', providerType: 'OLLAMA', baseUrl: 'http://host.docker.internal:11434', apiKey: '',
    defaultModel: 'qwen2.5-coder:7b', allowedModels: 'qwen2.5-coder:7b', enabled: true, externalDataTransfer: false });
  profileOpen.value = true;
}

function editProvider(provider: AiProviderProfileResponse): void {
  editingProfileId.value = provider.id;
  Object.assign(form, { name: provider.name, providerType: provider.providerType, baseUrl: provider.baseUrl, apiKey: '',
    defaultModel: provider.defaultModel, allowedModels: provider.allowedModels.join(', '), enabled: provider.enabled,
    externalDataTransfer: provider.externalDataTransfer });
  profileOpen.value = true;
}

async function saveProvider(): Promise<void> {
  saving.value = true;
  try {
    const payload = { tenantId: tenancy.currentTenantId, name: form.name, providerType: form.providerType,
      baseUrl: form.baseUrl, apiKey: form.apiKey || undefined, defaultModel: form.defaultModel,
      allowedModels: form.allowedModels.split(',').map((item) => item.trim()).filter(Boolean), enabled: form.enabled,
      externalDataTransfer: form.externalDataTransfer };
    if (editingProfileId.value) await api.updateAiProviderProfile(editingProfileId.value, payload);
    else await api.createAiProviderProfile(payload);
    profileOpen.value = false; message.value = 'Provider Profile을 저장했습니다. 변경 후 연결 검증을 실행하세요.'; await load();
  } catch (error) { message.value = error instanceof Error ? error.message : 'Provider를 저장하지 못했습니다.'; }
  finally { saving.value = false; }
}
</script>

<template>
  <section class="page delivery-page ai-providers-page">
    <header class="delivery-hero"><div><span class="delivery-eyebrow">AI CONTROL PLANE</span><h1>AI Providers</h1><p>Provider credential과 Tenant별 용도 라우팅을 분리해 데이터 반출을 명시적으로 제어합니다.</p></div><button v-if="auth.hasCapability('ai-provider:manage')" class="primary-button" type="button" @click="openCreate"><i class="pi pi-plus"></i> Provider Profile</button></header>
    <div v-if="message" class="delivery-notice">{{ message }}</div>
    <section><div class="section-heading-row"><div><span class="delivery-eyebrow">AVAILABLE PROFILES</span><h2>Provider Profiles</h2></div><span class="delivery-badge">API key는 재표시하지 않음</span></div><div class="provider-grid"><article v-for="provider in providers" :key="provider.id" class="provider-card"><header><span class="provider-icon"><i :class="provider.providerType === 'OLLAMA' ? 'pi pi-server' : 'pi pi-cloud'"></i></span><div><h3>{{ provider.name }}</h3><p>{{ provider.providerType }} · {{ provider.defaultModel }}</p></div><span class="status-dot" :class="provider.validationStatus === 'VALID' ? 'success' : 'neutral'">{{ provider.enabled ? provider.validationStatus : 'DISABLED' }}</span></header><dl><div><dt>Endpoint</dt><dd>{{ provider.baseUrl }}</dd></div><div><dt>Data transfer</dt><dd>{{ provider.externalDataTransfer ? 'External' : 'Local only' }}</dd></div><div><dt>Credential</dt><dd>{{ provider.credentialConfigured ? 'Configured' : 'None' }}</dd></div></dl><div class="provider-card-actions"><button v-if="auth.hasCapability('ai-provider:manage')" class="secondary-button" type="button" @click="editProvider(provider)"><i class="pi pi-pencil"></i> 편집</button><button v-if="auth.hasCapability('ai-provider:manage') && provider.enabled" class="secondary-button" type="button" @click="validateProvider(provider)"><i class="pi pi-bolt"></i> 연결 검증</button></div><div v-if="provider.providerType === 'OLLAMA' && provider.enabled && auth.hasCapability('ai-model:manage')" class="local-model-tools"><div><strong>Local models · 최대 9B</strong><button class="text-button" type="button" @click="refreshModels(provider)">설치 목록 동기화</button></div><small v-if="localModels[provider.id]?.length">{{ localModels[provider.id].map(item => `${item.modelTag} (${item.status})`).join(' · ') }}</small><div><select v-model="pullingModel[provider.id]"><option disabled value="">다운로드할 승인 모델</option><option v-for="model in provider.allowedModels" :key="model" :value="model">{{ model }}</option></select><button class="secondary-button" type="button" :disabled="!pullingModel[provider.id]" @click="pullModel(provider)">다운로드</button></div></div></article></div></section>
    <section><div class="section-heading-row"><div><span class="delivery-eyebrow">TENANT ROUTING</span><h2>용도별 모델 선택</h2></div><span class="delivery-badge trusted">{{ tenancy.currentTenant?.name }}</span></div><div class="routing-list"><article v-for="purpose in purposes" :key="purpose.id" class="routing-row"><div><strong>{{ purpose.title }}</strong><p>{{ purpose.description }}</p></div><label>Provider<select v-model="routeForms[purpose.id].profileId" @change="selectProfile(purpose.id)"><option v-for="provider in providers" :key="provider.id" :value="provider.id">{{ provider.name }}</option></select></label><label>Model<select v-model="routeForms[purpose.id].model"><option v-for="model in providerMap[routeForms[purpose.id].profileId]?.allowedModels || []" :key="model">{{ model }}</option></select></label><label class="transfer-toggle"><input v-model="routeForms[purpose.id].external" type="checkbox" :disabled="providerMap[routeForms[purpose.id].profileId]?.providerType === 'OLLAMA'" /> 외부 전송 허용</label><button class="primary-button" type="button" @click="saveRouting(purpose.id)">저장</button></article></div></section>
    <div v-if="profileOpen" class="delivery-modal-backdrop" @click.self="profileOpen = false"><form class="delivery-modal" @submit.prevent="saveProvider"><header><div><span class="delivery-eyebrow">PLATFORM PROVIDER</span><h2>Provider Profile {{ editingProfileId ? '편집' : '추가' }}</h2><p>빈 API key는 기존 Secret을 유지하며 endpoint/model 변경 시 연결 검증은 초기화됩니다.</p></div><button class="icon-button" type="button" @click="profileOpen = false"><i class="pi pi-times"></i></button></header><div class="delivery-form-grid"><label>Provider<select v-model="form.providerType" @change="providerChanged"><option value="OLLAMA">Ollama</option><option value="OPENAI">OpenAI</option><option value="GOOGLE_GENAI">Google Gemini</option><option value="OPENAI_COMPATIBLE">OpenAI Compatible</option></select></label><label>이름<input v-model="form.name" required /></label><label class="wide">Base URL<input v-model="form.baseUrl" required /></label><label>Default model<input v-model="form.defaultModel" required /></label><label>Allowed models<input v-model="form.allowedModels" placeholder="comma separated" /></label><label class="wide">API key (선택)<input v-model="form.apiKey" type="password" autocomplete="new-password" /></label><label v-if="editingProfileId" class="wide checkbox-field"><input v-model="form.enabled" type="checkbox" /> 이 Profile 활성화</label><label v-if="form.providerType !== 'OLLAMA'" class="wide checkbox-field"><input v-model="form.externalDataTransfer" type="checkbox" /> Kubernetes evidence 외부 전송 가능 Profile</label></div><footer><button class="secondary-button" type="button" @click="profileOpen = false">취소</button><button class="primary-button" :disabled="saving">{{ saving ? '저장 중…' : '암호화하여 저장' }}</button></footer></form></div>
  </section>
</template>
