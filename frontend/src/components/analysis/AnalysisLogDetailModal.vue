<script setup lang="ts">
import type { PodLogsResponse } from '@/api/client';

defineProps<{
  targetLabel: string;
  collectedAt?: string;
  logs: PodLogsResponse | null;
  loading: boolean;
  errorMessage: string;
  tailLines: number;
  containerName: string;
  containerOptions: string[];
}>();

const emit = defineEmits<{
  close: [];
  reload: [];
  'update:tailLines': [value: number];
  'update:containerName': [value: string];
}>();
</script>

<template>
  <div class="modal-backdrop analysis-log-detail-modal" @click.self="emit('close')">
    <section class="modal-panel feedback-detail-modal" aria-modal="true" role="dialog" aria-label="로그 조회">
      <header class="modal-header">
        <div><h2>로그 조회</h2><p>{{ targetLabel }} · {{ collectedAt || '-' }}</p></div>
        <button class="icon-button" title="닫기" type="button" @click="emit('close')"><i class="pi pi-times"></i></button>
      </header>
      <div class="feedback-detail-body">
        <div class="log-query-toolbar">
          <label class="form-field"><span>Rows</span><select :value="tailLines" :disabled="loading" @change="emit('update:tailLines', Number(($event.target as HTMLSelectElement).value)); emit('reload')"><option :value="50">50</option><option :value="100">100</option><option :value="200">200</option><option :value="500">500</option><option :value="1000">1000</option></select></label>
          <label class="form-field"><span>Container</span><select :value="containerName" :disabled="loading || containerOptions.length === 0" @change="emit('update:containerName', ($event.target as HTMLSelectElement).value); emit('reload')"><option value="">전체</option><option v-for="name in containerOptions" :key="name" :value="name">{{ name }}</option></select></label>
          <button class="secondary-button compact-button" :disabled="loading" type="button" @click="emit('reload')"><i :class="loading ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i><span>조회</span></button>
        </div>
        <div v-if="errorMessage" class="inline-error"><i class="pi pi-exclamation-triangle"></i><span>{{ errorMessage }}</span></div>
        <div v-else-if="loading" class="empty-state compact"><i class="pi pi-spin pi-spinner"></i><span>로그를 조회하는 중입니다.</span></div>
        <div v-else-if="!logs?.containers.length" class="empty-state compact"><i class="pi pi-search"></i><span>표시할 로그가 없습니다.</span></div>
        <div v-else class="log-result-list"><article v-for="container in logs.containers" :key="container.containerName" class="log-result"><header><strong>{{ container.containerName || '-' }}</strong><span v-if="container.truncated" class="status-pill info">truncated</span></header><pre>{{ container.log }}</pre></article></div>
      </div>
      <footer class="modal-actions"><button class="secondary-button" type="button" @click="emit('close')">닫기</button></footer>
    </section>
  </div>
</template>
