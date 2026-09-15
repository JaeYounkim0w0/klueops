<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ApplicationDeliveryNav from '@/components/application/ApplicationDeliveryNav.vue';
import { api, type LibraryChartResponse } from '@/api/client';
import { useTenancyStore } from '@/stores/tenancy';

const route = useRoute();
const router = useRouter();
const tenancy = useTenancyStore();
const charts = ref<LibraryChartResponse[]>([]);
const loading = ref(true);
const uploadOpen = ref(false);
const uploadFile = ref<File | null>(null);
const uploading = ref(false);
const message = ref('');
const selectedVersions = ref<Record<string, string>>({});

onMounted(async () => {
  await tenancy.load();
  uploadOpen.value = route.query.upload === 'true';
  await load();
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    charts.value = await api.listLibraryCharts(tenancy.currentTenantId);
    charts.value.forEach((chart) => {
      const available = chart.versions.some((version) => version.id === selectedVersions.value[chart.id]);
      if (!available) selectedVersions.value[chart.id] = chart.versions[0]?.id || '';
    });
  }
  catch (error) { message.value = error instanceof Error ? error.message : 'Chart Library를 불러오지 못했습니다.'; }
  finally { loading.value = false; }
}

function selectedVersion(chart: LibraryChartResponse): string {
  return selectedVersions.value[chart.id] || chart.versions[0]?.id || '';
}

function deliveryTarget(path: 'values' | 'deploy', chart: LibraryChartResponse): string {
  const query = new URLSearchParams({ chart: chart.name });
  if (typeof route.query.upgradeApplicationId === 'string') query.set('upgradeApplicationId', route.query.upgradeApplicationId);
  if (typeof route.query.clusterId === 'string') query.set('clusterId', route.query.clusterId);
  if (typeof route.query.namespace === 'string') query.set('namespace', route.query.namespace);
  if (typeof route.query.releaseName === 'string') query.set('releaseName', route.query.releaseName);
  return `/applications/${path}/${selectedVersion(chart)}?${query}`;
}

function chooseFile(event: Event): void {
  uploadFile.value = (event.target as HTMLInputElement).files?.[0] ?? null;
}

async function upload(): Promise<void> {
  if (!uploadFile.value) return;
  uploading.value = true;
  try {
    await api.uploadChart(tenancy.currentTenantId, uploadFile.value);
    uploadOpen.value = false;
    uploadFile.value = null;
    message.value = 'Chart 안전 검사를 통과해 Library에 저장했습니다.';
    await router.replace('/applications/library');
    await load();
  } catch (error) { message.value = error instanceof Error ? error.message : 'Chart를 업로드하지 못했습니다.'; }
  finally { uploading.value = false; }
}
</script>

<template>
  <section class="page delivery-page">
    <header class="delivery-hero compact">
      <div><span class="delivery-eyebrow">TENANT ASSET</span><h1>Chart Library</h1><p>검증된 Chart와 version을 Tenant 단위로 관리하고 배포를 시작합니다.</p></div>
      <div class="header-actions"><button class="secondary-button" type="button" @click="uploadOpen = true"><i class="pi pi-upload"></i> .tgz 가져오기</button><RouterLink to="/applications/discover" class="primary-button"><i class="pi pi-search"></i> Chart 찾기</RouterLink></div>
    </header>
    <ApplicationDeliveryNav />
    <div v-if="message" class="delivery-notice">{{ message }}</div>
    <div v-if="loading" class="delivery-empty"><i class="pi pi-spin pi-spinner"></i><p>Library를 불러오는 중입니다.</p></div>
    <div v-else-if="charts.length" class="library-list">
      <article v-for="chart in charts" :key="chart.id" class="library-row">
        <span class="chart-logo">{{ chart.name.slice(0, 1).toUpperCase() }}</span>
        <div class="library-main"><div><h2>{{ chart.name }}</h2><span class="delivery-badge trusted"><i class="pi pi-shield"></i> {{ chart.trustStatus }}</span></div><p>{{ chart.description || 'Chart description 없음' }}</p><small>{{ chart.sourceType }} · {{ chart.sourceName || '직접 업로드' }}</small></div>
        <label class="version-select">Version<select v-model="selectedVersions[chart.id]"><option v-for="version in chart.versions" :key="version.id" :value="version.id">{{ version.chartVersion }} · {{ version.appVersion || 'app n/a' }}</option></select></label>
        <div class="library-actions"><RouterLink v-if="selectedVersion(chart)" :to="deliveryTarget('values', chart)" class="secondary-button">Values 편집</RouterLink><RouterLink v-if="selectedVersion(chart)" :to="deliveryTarget('deploy', chart)" class="primary-button">배포 준비</RouterLink></div>
      </article>
    </div>
    <div v-else class="delivery-empty"><i class="pi pi-folder-open"></i><h2>아직 저장한 Chart가 없습니다</h2><p>Artifact Hub에서 검색하거나 보유한 .tgz 파일을 가져오세요.</p><RouterLink to="/applications/discover" class="primary-button">Chart 검색</RouterLink></div>

    <div v-if="uploadOpen" class="delivery-modal-backdrop" @click.self="uploadOpen = false">
      <section class="delivery-modal narrow" role="dialog" aria-modal="true"><header><div><span class="delivery-eyebrow">DIRECT IMPORT</span><h2>Helm Chart 가져오기</h2><p>20 MiB 이하 .tgz만 허용하며 경로·link·압축 해제 크기를 검사합니다.</p></div><button class="icon-button" type="button" @click="uploadOpen = false"><i class="pi pi-times"></i></button></header>
        <label class="file-drop"><i class="pi pi-cloud-upload"></i><strong>{{ uploadFile?.name || '.tgz 파일 선택' }}</strong><small>원본은 변경하지 않고 digest로 중복을 판별합니다.</small><input type="file" accept=".tgz,application/gzip" @change="chooseFile" /></label>
        <footer><button class="secondary-button" type="button" @click="uploadOpen = false">취소</button><button class="primary-button" type="button" :disabled="!uploadFile || uploading" @click="upload">{{ uploading ? '검사 중…' : '검사 후 가져오기' }}</button></footer>
      </section>
    </div>
  </section>
</template>
