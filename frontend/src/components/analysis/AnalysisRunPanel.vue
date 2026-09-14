<script setup lang="ts">
import type { ApplicationResponse, ClusterResponse, KubernetesNamespaceResponse } from '@/api/client';

type AnalysisMode = 'cluster' | 'namespace' | 'application';
type Feedback = { tone: 'success' | 'error' | 'info'; message: string; detail?: string };
type ReadinessStep = { label: string; detail: string; status: string };

defineProps<{
  mode: AnalysisMode;
  clusterId: string;
  namespace: string;
  applicationId: string;
  clusters: ClusterResponse[];
  namespaces: KubernetesNamespaceResponse[];
  applications: ApplicationResponse[];
  readinessSteps: ReadinessStep[];
  loading: boolean;
  loadingNamespaces: boolean;
  running: boolean;
  canRun: boolean;
  feedback: Feedback | null;
}>();

const emit = defineEmits<{
  'update:mode': [value: AnalysisMode];
  'update:clusterId': [value: string];
  'update:namespace': [value: string];
  'update:applicationId': [value: string];
  run: [];
  'show-feedback': [];
}>();

function selectValue(event: Event) {
  return (event.target as HTMLSelectElement).value;
}

function inputValue(event: Event) {
  return (event.target as HTMLInputElement).value;
}

function updateMode(event: Event) {
  emit('update:mode', selectValue(event) as AnalysisMode);
}
</script>

<template>
  <section class="analysis-run-panel">
    <ol class="analysis-flow-steps" aria-label="AI 분석 실행 단계">
      <li v-for="step in readinessSteps" :key="step.label" :class="`step-${step.status}`">
        <span>{{ step.label }}</span>
        <strong>{{ step.detail }}</strong>
      </li>
    </ol>

    <div class="analysis-run-fields">
      <label class="form-field">
        <span>Mode</span>
        <select :value="mode" @change="updateMode">
          <option value="cluster">Cluster</option>
          <option value="namespace">Namespace</option>
          <option value="application">Application</option>
        </select>
      </label>

      <label class="form-field">
        <span>Cluster</span>
        <select :value="clusterId" :disabled="loading || clusters.length === 0" @change="emit('update:clusterId', selectValue($event))">
          <option value="" disabled>클러스터 선택</option>
          <option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option>
        </select>
      </label>

      <label v-if="mode !== 'cluster'" class="form-field">
        <span>Namespace</span>
        <select
          v-if="namespaces.length > 0"
          :value="namespace"
          :disabled="loadingNamespaces"
          @change="emit('update:namespace', selectValue($event))"
        >
          <option v-for="item in namespaces" :key="item.name" :value="item.name">{{ item.name }}</option>
        </select>
        <input v-else :value="namespace" placeholder="default" @input="emit('update:namespace', inputValue($event))" />
      </label>

      <label v-if="mode === 'application'" class="form-field">
        <span>Application</span>
        <select :value="applicationId" :disabled="applications.length === 0" @change="emit('update:applicationId', selectValue($event))">
          <option value="" disabled>애플리케이션 선택</option>
          <option v-for="application in applications" :key="application.id" :value="application.id">
            {{ application.name }} · {{ application.namespace || '-' }}
          </option>
        </select>
      </label>
    </div>

    <div class="analysis-run-actions">
      <small v-if="feedback" class="operation-feedback" :class="feedback.tone">
        <span>{{ feedback.message }}</span>
        <button v-if="feedback.detail" type="button" @click="emit('show-feedback')">상세 보기</button>
      </small>
      <button class="primary-button" :disabled="!canRun" type="button" @click="emit('run')">
        <i :class="running ? 'pi pi-spin pi-spinner' : 'pi pi-sparkles'"></i>
        <span>{{ running ? '분석 중' : mode === 'cluster' ? 'Cluster AI 분석 실행' : mode === 'application' ? 'Application AI 분석 실행' : 'Namespace AI 분석 실행' }}</span>
      </button>
    </div>
  </section>
</template>
