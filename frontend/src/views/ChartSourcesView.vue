<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import ApplicationDeliveryNav from '@/components/application/ApplicationDeliveryNav.vue';
import { api, type ChartSourceResponse } from '@/api/client';
import { useTenancyStore } from '@/stores/tenancy';

const tenancy = useTenancyStore();
const sources = ref<ChartSourceResponse[]>([]);
const open = ref(false);
const saving = ref(false);
const message = ref('');
const form = reactive({ sourceType: 'HELM_REPOSITORY', name: '', endpoint: '', credential: '' });

onMounted(async () => { await tenancy.load(); await load(); });
/** load 처리 결과를 조회해 반환한다. */
async function load() { sources.value = await api.listChartSources(tenancy.currentTenantId); }
/** save 처리에 필요한 데이터를 생성하거나 저장한다. */
async function save() {
  saving.value = true;
  try {
    await api.createChartSource({ tenantId: tenancy.currentTenantId, ...form });
    open.value = false; Object.assign(form, { sourceType: 'HELM_REPOSITORY', name: '', endpoint: '', credential: '' });
    message.value = 'Source를 저장했습니다.'; await load();
  } catch (error) { message.value = error instanceof Error ? error.message : 'Source를 저장하지 못했습니다.'; }
  finally { saving.value = false; }
}
/** remove 처리 대상과 관련 상태를 안전하게 정리한다. */
async function remove(source: ChartSourceResponse) {
  if (!window.confirm(`${source.name} Source를 제거할까요? Library에 가져온 Chart는 유지됩니다.`)) return;
  await api.deleteChartSource(tenancy.currentTenantId, source.id); await load();
}
</script>

<template>
  <section class="page delivery-page">
    <header class="delivery-hero compact"><div><span class="delivery-eyebrow">TENANT CONFIGURATION</span><h1>Sources</h1><p>재사용할 Helm Repository와 OCI Registry 연결을 관리합니다.</p></div><button class="primary-button" type="button" @click="open = true"><i class="pi pi-plus"></i> Repository</button></header>
    <ApplicationDeliveryNav /><div v-if="message" class="delivery-notice">{{ message }}</div>
    <div class="source-table"><div class="source-row header"><span>Source</span><span>Endpoint</span><span>인증</span><span>상태</span><span></span></div><div v-for="source in sources" :key="source.id" class="source-row"><div><strong>{{ source.name }}</strong><small>{{ source.sourceType }}</small></div><code>{{ source.endpoint }}</code><span>{{ source.credentialConfigured ? 'Secret 설정됨' : 'Public' }}</span><span class="delivery-badge" :class="{ trusted: source.enabled }">{{ source.enabled ? '사용 중' : '중지' }}</span><button class="icon-button" type="button" title="삭제" @click="remove(source)"><i class="pi pi-trash"></i></button></div></div>
    <div v-if="!sources.length" class="delivery-empty"><i class="pi pi-database"></i><h2>추가 Source가 없습니다</h2><p>Artifact Hub 검색과 직접 업로드는 Source 등록 없이도 사용할 수 있습니다.</p></div>
    <div v-if="open" class="delivery-modal-backdrop" @click.self="open = false"><form class="delivery-modal narrow" @submit.prevent="save"><header><div><span class="delivery-eyebrow">NEW SOURCE</span><h2>Repository 추가</h2><p>외부 공개 HTTPS 주소만 허용됩니다.</p></div><button class="icon-button" type="button" @click="open = false"><i class="pi pi-times"></i></button></header><div class="delivery-form-grid"><label>유형<select v-model="form.sourceType"><option value="HELM_REPOSITORY">Helm Repository</option><option value="OCI_REGISTRY">OCI Registry</option></select></label><label>표시 이름<input v-model="form.name" required placeholder="Platform Charts" /></label><label class="wide">Endpoint<input v-model="form.endpoint" type="url" required placeholder="https://charts.example.com" /></label><label class="wide">Credential (선택)<input v-model="form.credential" type="password" autocomplete="new-password" placeholder="저장 후 다시 표시하지 않습니다" /></label></div><footer><button class="secondary-button" type="button" @click="open = false">취소</button><button class="primary-button" :disabled="saving">{{ saving ? '검증 중…' : '검증 후 저장' }}</button></footer></form></div>
  </section>
</template>
