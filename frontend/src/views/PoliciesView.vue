<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ApiError, api, type ClusterResponse, type PolicyDefinitionResponse, type PolicyEvaluationResponse, type ResourceChangeResponse } from '@/api/client';
import { formatTimestamp } from '@/utils/time';

const route = useRoute();
const clusters = ref<ClusterResponse[]>([]);
const policies = ref<PolicyDefinitionResponse[]>([]);
const evaluations = ref<PolicyEvaluationResponse[]>([]);
const changes = ref<ResourceChangeResponse[]>([]);
const clusterId = ref(String(route.query.clusterId ?? ''));
const namespace = ref(String(route.query.namespace ?? ''));
const resultFilter = ref('');
const activeTab = ref<'evaluations' | 'policies' | 'changes'>('evaluations');
const loading = ref(true);
const evaluating = ref(false);
const error = ref('');

const evaluationSummary = computed(() => ({
  fail: evaluations.value.filter((item) => item.result === 'FAIL').length,
  warn: evaluations.value.filter((item) => item.result === 'WARN').length,
  unknown: evaluations.value.filter((item) => item.result === 'NOT_APPLICABLE').length,
  pass: evaluations.value.filter((item) => item.result === 'PASS').length
}));

/** load 처리 결과를 조회해 반환한다. */
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [policyList, evaluationList, changeList] = await Promise.all([
      api.listPolicies(),
      api.listPolicyEvaluations({ clusterId: clusterId.value || undefined, namespace: namespace.value.trim() || undefined, result: resultFilter.value || undefined }),
      api.listResourceChanges({ clusterId: clusterId.value || undefined, namespace: namespace.value.trim() || undefined })
    ]);
    policies.value = policyList;
    evaluations.value = evaluationList;
    changes.value = changeList;
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '정책과 변경 이력을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

/** evaluate 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function evaluate() {
  if (!clusterId.value) {
    error.value = '정책을 평가할 클러스터를 선택하세요.';
    return;
  }
  evaluating.value = true;
  error.value = '';
  try {
    await api.evaluatePolicies(clusterId.value);
    await load();
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '정책 평가에 실패했습니다.';
  } finally {
    evaluating.value = false;
  }
}

/** togglePolicy 처리 데이터를 화면 또는 API 표현으로 변환한다. */
async function togglePolicy(policy: PolicyDefinitionResponse) {
  try {
    const updated = await api.updatePolicy(policy.id, !policy.enabled, policy.severity);
    policies.value = policies.value.map((item) => item.id === updated.id ? updated : item);
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '정책 설정을 변경하지 못했습니다.';
  }
}

/** resultClass 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function resultClass(value: string) { return value.toLowerCase().replace('_', '-'); }

watch([clusterId, resultFilter], load);
onMounted(async () => {
  try { clusters.value = await api.listClusters(); } catch { clusters.value = []; }
  await load();
});
</script>

<template>
  <section class="page policy-page">
    <header class="page-header operations-page-header">
      <div><span class="page-eyebrow">POLICY & CHANGE CONTROL</span><h1>Policies</h1><p>Kubernetes 구성 위험을 규칙으로 판정하고 안전한 snapshot 변경만 추적합니다.</p></div>
      <button class="primary-button" type="button" :disabled="evaluating || !clusterId" @click="evaluate"><i class="pi pi-shield" :class="{ 'pi-spin pi-spinner': evaluating }"></i><span>{{ evaluating ? '평가 중' : '선택 클러스터 평가' }}</span></button>
    </header>

    <section class="operations-filter-bar">
      <label><span>Cluster</span><select v-model="clusterId"><option value="">전체 클러스터</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label>
      <label><span>Namespace</span><input v-model="namespace" type="search" placeholder="namespace" @keyup.enter="load"></label>
      <label><span>Result</span><select v-model="resultFilter"><option value="">전체 결과</option><option>FAIL</option><option>WARN</option><option>PASS</option><option value="NOT_APPLICABLE">근거 부족</option></select></label>
      <button class="icon-button" type="button" title="필터 적용" aria-label="필터 적용" @click="load"><i class="pi pi-search"></i></button>
    </section>

    <div class="operations-summary-line policy-summary"><span class="danger"><strong>{{ evaluationSummary.fail }}</strong> Fail</span><span class="warning"><strong>{{ evaluationSummary.warn }}</strong> Warn</span><span><strong>{{ evaluationSummary.unknown }}</strong> Evidence gap</span><span class="success"><strong>{{ evaluationSummary.pass }}</strong> Pass</span></div>

    <nav class="detail-tabs"><button type="button" :class="{ active: activeTab === 'evaluations' }" @click="activeTab = 'evaluations'">평가 결과</button><button type="button" :class="{ active: activeTab === 'policies' }" @click="activeTab = 'policies'">정책 설정</button><button type="button" :class="{ active: activeTab === 'changes' }" @click="activeTab = 'changes'">변경 이력</button></nav>
    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>정책 근거를 불러오고 있습니다.</span></div>

    <section v-else-if="activeTab === 'evaluations'" class="policy-evaluation-list">
      <article v-for="item in evaluations" :key="item.id"><span class="policy-result" :class="resultClass(item.result)">{{ item.result === 'NOT_APPLICABLE' ? '근거 부족' : item.result }}</span><div class="policy-evaluation-main"><strong>{{ policies.find((policy) => policy.id === item.policyId)?.name || item.policyId }}</strong><p>{{ item.evidence }}</p><small>{{ item.clusterName }}<template v-if="item.namespace"> / {{ item.namespace }}</template> · {{ item.resourceKind }}/{{ item.resourceName }}</small></div><div class="policy-recommendation"><span>권장 확인</span><p>{{ item.recommendation }}</p></div><time>{{ formatTimestamp(item.evaluatedAt) }}</time></article>
      <div v-if="!evaluations.length" class="operations-empty-state"><i class="pi pi-shield"></i><div><strong>저장된 정책 평가가 없습니다.</strong><span>클러스터를 선택하고 평가를 실행하세요.</span></div></div>
    </section>

    <section v-else-if="activeTab === 'policies'" class="policy-definition-list">
      <article v-for="policy in policies" :key="policy.id"><div><span class="page-eyebrow">{{ policy.category }}</span><strong>{{ policy.name }}</strong><p>{{ policy.description }}</p></div><span class="status-pill" :class="policy.severity.toLowerCase()">{{ policy.severity }}</span><label class="switch-control"><input type="checkbox" :checked="policy.enabled" @change="togglePolicy(policy)"><span></span><small>{{ policy.enabled ? '사용' : '중지' }}</small></label></article>
    </section>

    <section v-else class="resource-change-list">
      <div class="evidence-disclaimer"><i class="pi pi-lock"></i> 전체 YAML이나 Secret 값이 아닌 안전 summary의 hash와 상태만 비교합니다.</div>
      <article v-for="change in changes" :key="change.id"><span class="change-type" :class="change.changeType.toLowerCase()">{{ change.changeType }}</span><div><strong>{{ change.resourceKind }}/{{ change.resourceName }}</strong><p>{{ change.summary }}</p><small>{{ change.clusterName }}<template v-if="change.namespace"> / {{ change.namespace }}</template></small></div><span class="status-transition">{{ change.previousStatus || '-' }} <i class="pi pi-arrow-right"></i> {{ change.currentStatus || '-' }}</span><time>{{ formatTimestamp(change.detectedAt) }}</time></article>
      <div v-if="!changes.length" class="operations-empty-state"><i class="pi pi-history"></i><div><strong>감지된 변경이 없습니다.</strong><span>첫 평가는 baseline을 만들고, 다음 동기화 이후 변경부터 기록합니다.</span></div></div>
    </section>
  </section>
</template>
