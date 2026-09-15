<script setup lang="ts">
import { onMounted, ref } from 'vue';
import ApplicationDeliveryNav from '@/components/application/ApplicationDeliveryNav.vue';
import { api, type CatalogPackageResponse } from '@/api/client';
import { ApiError } from '@/api/http';
import { useTenancyStore } from '@/stores/tenancy';

const tenancy = useTenancyStore();
const query = ref('nginx');
const results = ref<CatalogPackageResponse[]>([]);
const loading = ref(false);
const importing = ref('');
const message = ref('');

onMounted(async () => {
  await tenancy.load();
  await search();
});

async function search(): Promise<void> {
  if (!tenancy.currentTenantId) return;
  loading.value = true;
  message.value = '';
  try {
    results.value = await api.searchChartCatalog(tenancy.currentTenantId, query.value.trim(), 24);
  } catch (error) {
    message.value = error instanceof ApiError && error.status >= 500
      ? 'Chart 검색 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'
      : error instanceof Error ? error.message : 'Chart를 검색하지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

async function importPackage(item: CatalogPackageResponse): Promise<void> {
  importing.value = item.packageId;
  message.value = '';
  try {
    await api.importChart({ tenantId: tenancy.currentTenantId, repository: item.repository, name: item.name, version: item.version });
    message.value = `${item.name} ${item.version}을 Tenant Library로 가져왔습니다.`;
  } catch (error) {
    message.value = error instanceof Error ? error.message : 'Chart를 가져오지 못했습니다.';
  } finally {
    importing.value = '';
  }
}
</script>

<template>
  <section class="page delivery-page">
    <header class="delivery-hero compact">
      <div><span class="delivery-eyebrow">APPLICATION DELIVERY</span><h1>Discover</h1><p>Artifact Hub의 Helm Chart를 탐색하고 Tenant Library로 안전하게 가져옵니다.</p></div>
      <RouterLink to="/applications/library" class="secondary-button"><i class="pi pi-folder"></i> Chart Library</RouterLink>
    </header>
    <ApplicationDeliveryNav />
    <form class="delivery-search" @submit.prevent="search">
      <i class="pi pi-search"></i><input v-model="query" aria-label="Chart 검색어" placeholder="nginx, postgresql, prometheus…" />
      <button class="primary-button" type="submit" :disabled="loading">{{ loading ? '검색 중…' : '검색' }}</button>
    </form>
    <div v-if="message" class="delivery-notice error" role="alert"><i class="pi pi-exclamation-triangle"></i> {{ message }}</div>
    <div v-if="loading" class="delivery-skeleton-grid" aria-label="검색 중"><span v-for="item in 6" :key="item"></span></div>
    <div v-else-if="results.length" class="chart-card-grid">
      <article v-for="item in results" :key="item.packageId" class="chart-card">
        <header><span class="chart-logo">{{ item.name.slice(0, 1).toUpperCase() }}</span><div><h2>{{ item.name }}</h2><p>{{ item.repositoryDisplayName }}</p></div></header>
        <p class="chart-description">{{ item.description || '설명이 제공되지 않았습니다.' }}</p>
        <div class="chart-meta"><span>v{{ item.version }}</span><span v-if="item.official" class="delivery-badge trusted">Official</span><span v-else-if="item.verifiedPublisher" class="delivery-badge">Verified</span></div>
        <button class="primary-button full" type="button" :disabled="importing === item.packageId" @click="importPackage(item)">
          {{ importing === item.packageId ? '검사 및 저장 중…' : 'Tenant Library로 가져오기' }}
        </button>
      </article>
    </div>
    <div v-else class="delivery-empty"><i class="pi pi-search"></i><h2>검색 결과가 없습니다</h2><p>다른 제품명이나 기능 키워드로 검색해 보세요.</p></div>
  </section>
</template>
