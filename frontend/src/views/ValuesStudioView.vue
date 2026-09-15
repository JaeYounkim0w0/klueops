<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ApplicationDeliveryNav from '@/components/application/ApplicationDeliveryNav.vue';
import { api, type ValuesProfileResponse } from '@/api/client';
import { useTenancyStore } from '@/stores/tenancy';

const route = useRoute();
const router = useRouter();
const tenancy = useTenancyStore();
const chartVersionId = computed(() => String(route.params.chartVersionId));
const chartName = computed(() => String(route.query.chart || 'Helm Chart'));
const profiles = ref<ValuesProfileResponse[]>([]);
const selectedProfileId = ref('');
const profileName = ref('default');
const valuesYaml = ref('# Tenant별 Custom Values만 저장합니다.\nreplicaCount: 1\n');
const saving = ref(false);
const message = ref('');

onMounted(async () => { await tenancy.load(); profiles.value = await api.listValuesProfiles(tenancy.currentTenantId, chartVersionId.value); selectedProfileId.value = profiles.value[0]?.id || ''; });

async function saveAndContinue(): Promise<void> {
  saving.value = true;
  try {
    let profileId = selectedProfileId.value;
    if (!profileId) {
      const profile = await api.createValuesProfile({ tenantId: tenancy.currentTenantId, chartVersionId: chartVersionId.value, name: profileName.value, description: `${chartName.value} custom values` });
      profileId = profile.id;
    }
    const revision = await api.createValuesRevision(tenancy.currentTenantId, profileId, valuesYaml.value);
    await router.push({ path: `/applications/deploy/${chartVersionId.value}`, query: { chart: chartName.value, valuesRevisionId: revision.id } });
  } catch (error) { message.value = error instanceof Error ? error.message : 'Values를 저장하지 못했습니다.'; }
  finally { saving.value = false; }
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
      <section class="yaml-editor-panel"><header><div><strong>values.yaml</strong><small>YAML object · 최대 1 MiB</small></div><span class="delivery-badge">Revision 생성</span></header><textarea v-model="valuesYaml" spellcheck="false" aria-label="Custom Values YAML"></textarea><footer><span>{{ valuesYaml.length.toLocaleString() }} characters</span><button class="primary-button" type="button" :disabled="saving" @click="saveAndContinue">{{ saving ? '검증 및 저장 중…' : 'Revision 저장 후 Target 선택' }} <i class="pi pi-arrow-right"></i></button></footer></section>
    </div>
  </section>
</template>
