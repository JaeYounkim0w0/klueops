<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ApiError, api, type AuditLogResponse } from '@/api/client';
import { formatTimestamp } from '@/utils/time';

const logs = ref<AuditLogResponse[]>([]);
const actor = ref('');
const action = ref('');
const targetType = ref('');
const requestId = ref('');
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try { logs.value = await api.listAuditLogs({ actor: actor.value.trim() || undefined, action: action.value.trim() || undefined, targetType: targetType.value || undefined, requestId: requestId.value.trim() || undefined }); }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : '감사 이력을 불러오지 못했습니다.'; }
  finally { loading.value = false; }
}
onMounted(load);
</script>

<template>
  <section class="page audit-page">
    <header class="page-header"><span class="page-eyebrow">AUDIT TRAIL</span><h1>Audit</h1><p>분석, 조치, 설정 변경을 request ID와 대상 기준으로 추적합니다.</p></header>
    <section class="operations-filter-bar"><label><span>Actor</span><input v-model="actor" type="search" placeholder="anonymous" @keyup.enter="load"></label><label><span>Action</span><input v-model="action" type="search" placeholder="INCIDENT..." @keyup.enter="load"></label><label><span>Target</span><select v-model="targetType"><option value="">전체 대상</option><option>CLUSTER</option><option>ANALYSIS</option><option>INCIDENT</option><option>POLICY</option><option>SETTINGS</option><option>APPLICATION</option></select></label><label class="wide"><span>Request ID</span><input v-model="requestId" type="search" placeholder="requestId" @keyup.enter="load"></label><button class="icon-button" type="button" title="조회" aria-label="조회" @click="load"><i class="pi pi-search"></i></button></section>
    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>감사 이력을 불러오고 있습니다.</span></div>
    <div v-else class="audit-table"><div class="audit-table-head"><span>시간</span><span>Actor / Action</span><span>대상</span><span>Request ID</span></div><article v-for="log in logs" :key="log.id"><time>{{ formatTimestamp(log.createdAt) }}</time><span><strong>{{ log.action }}</strong><small>{{ log.actor }}</small></span><span><strong>{{ log.targetType }}</strong><small>{{ log.targetId }}</small></span><code>{{ log.requestId || '-' }}</code></article><div v-if="!logs.length" class="operations-empty-state"><i class="pi pi-history"></i><span>조건에 맞는 감사 이력이 없습니다.</span></div></div>
  </section>
</template>
