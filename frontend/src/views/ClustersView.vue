<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { api, type ClusterConnectionTestResponse, type ClusterResponse, type RegisterClusterRequest } from '@/api/client';
import { useTenancyStore } from '@/stores/tenancy';

const router = useRouter();
const tenancy = useTenancyStore();
const clusters = ref<ClusterResponse[]>([]);
const loading = ref(true);
const saving = ref(false);
const showRegistration = ref(false);
const errorMessage = ref('');
const formError = ref('');
const runningAction = ref<Record<string, string>>({});
const clusterFeedback = ref<Record<string, { tone: 'success' | 'error' | 'info'; summary: string; detail?: string }>>({});
const activeActionClusterId = ref<string | null>(null);
const actionMenuPosition = ref({ top: 0, left: 0 });
const detailFeedbackClusterId = ref<string | null>(null);

const activeActionCluster = computed(() => (
  clusters.value.find((cluster) => cluster.id === activeActionClusterId.value) || null
));

const detailFeedbackCluster = computed(() => (
  clusters.value.find((cluster) => cluster.id === detailFeedbackClusterId.value) || null
));

const detailFeedback = computed(() => {
  if (!detailFeedbackClusterId.value) {
    return null;
  }
  return clusterFeedback.value[detailFeedbackClusterId.value] || null;
});

const form = ref({
  name: '',
  description: '',
  environment: 'DEV' as RegisterClusterRequest['environment'],
  provider: 'KIND' as RegisterClusterRequest['provider'],
  region: '',
  credentialType: 'KUBECONFIG' as RegisterClusterRequest['credentialType'],
  kubeconfig: '',
  kubeconfigServerOverride: '',
  kubeconfigInsecureSkipTlsVerify: false,
  apiServerUrl: '',
  caCertificate: '',
  token: '',
  clusterWide: true,
  allowedNamespacesText: '',
  defaultNamespace: 'default',
  autoSyncEnabled: true,
  syncIntervalSeconds: 300,
  testAfterRegister: true
});

const canSubmit = computed(() => {
  if (!form.value.name.trim()) {
    return false;
  }
  if (!tenancy.currentTenantId || !tenancy.currentWorkspaceId) return false;
  if (form.value.credentialType === 'KUBECONFIG') {
    return form.value.kubeconfig.trim().length > 0;
  }
  return Boolean(
    form.value.apiServerUrl.trim()
    && form.value.caCertificate.trim()
    && form.value.token.trim()
  );
});

onMounted(async () => {
  await tenancy.load();
  await loadClusters();
  window.addEventListener('resize', closeActionMenu);
  window.addEventListener('scroll', closeActionMenu, true);
  document.addEventListener('click', closeActionMenu);
  document.addEventListener('keydown', handleEscape);
});

watch(() => [tenancy.currentTenantId, tenancy.currentWorkspaceId], ([tenantId, workspaceId], previous) => {
  if (previous && tenantId && workspaceId) void loadClusters();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', closeActionMenu);
  window.removeEventListener('scroll', closeActionMenu, true);
  document.removeEventListener('click', closeActionMenu);
  document.removeEventListener('keydown', handleEscape);
});

/** handleEscape 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
function handleEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape') return;
  showRegistration.value = false;
  detailFeedbackClusterId.value = null;
  closeActionMenu();
}

/** loadClusters 처리 결과를 조회해 반환한다. */
async function loadClusters() {
  closeActionMenu();
  loading.value = true;
  errorMessage.value = '';
  try {
    clusters.value = await api.listClusters({
      tenantId: tenancy.currentTenantId,
      workspaceId: tenancy.currentWorkspaceId,
    });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '클러스터 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

/** registerCluster 처리에 필요한 데이터를 생성하거나 저장한다. */
async function registerCluster() {
  if (!canSubmit.value) {
    formError.value = '필수 입력값을 확인해주세요.';
    return;
  }

  saving.value = true;
  formError.value = '';
  try {
    const created = await api.registerCluster(buildRequest());
    showRegistration.value = false;
    const shouldTestAfterRegister = form.value.testAfterRegister;
    resetForm();
    await loadClusters();
    if (shouldTestAfterRegister) {
      await testConnection(created.id);
    }
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '클러스터 등록에 실패했습니다.';
  } finally {
    saving.value = false;
  }
}

/** importKubeconfig 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function importKubeconfig(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }
  form.value.kubeconfig = await file.text();
  input.value = '';
}

/** buildRequest 처리에 필요한 결과를 조합해 반환한다. */
function buildRequest(): RegisterClusterRequest {
  const allowedNamespaces = form.value.allowedNamespacesText
    .split(',')
    .map((namespace) => namespace.trim())
    .filter(Boolean);

  return {
    tenantId: tenancy.currentTenantId,
    workspaceId: tenancy.currentWorkspaceId,
    name: form.value.name.trim(),
    description: optionalText(form.value.description),
    environment: form.value.environment,
    provider: form.value.provider,
    region: optionalText(form.value.region),
    credentialType: form.value.credentialType,
    kubeconfig: form.value.credentialType === 'KUBECONFIG' ? preparedKubeconfig() : undefined,
    serviceAccount: form.value.credentialType === 'SERVICE_ACCOUNT_TOKEN'
      ? {
          apiServerUrl: form.value.apiServerUrl.trim(),
          caCertificate: form.value.caCertificate,
          token: form.value.token
        }
      : undefined,
    namespaceAccess: {
      clusterWide: form.value.clusterWide,
      allowedNamespaces: form.value.clusterWide ? [] : allowedNamespaces,
      defaultNamespace: optionalText(form.value.defaultNamespace)
    },
    syncSettings: {
      autoSyncEnabled: form.value.autoSyncEnabled,
      syncIntervalSeconds: form.value.syncIntervalSeconds
    }
  };
}

/** optionalText 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function optionalText(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

/** resetForm 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function resetForm() {
  form.value = {
    name: '',
    description: '',
    environment: 'DEV',
    provider: 'KIND',
    region: '',
    credentialType: 'KUBECONFIG',
    kubeconfig: '',
    kubeconfigServerOverride: '',
    kubeconfigInsecureSkipTlsVerify: false,
    apiServerUrl: '',
    caCertificate: '',
    token: '',
    clusterWide: true,
    allowedNamespacesText: '',
    defaultNamespace: 'default',
    autoSyncEnabled: true,
    syncIntervalSeconds: 300,
    testAfterRegister: true
  };
}

/** testConnection 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function testConnection(clusterId: string) {
  closeActionMenu();
  await runClusterAction(clusterId, 'connection-test', async () => {
    const result = await api.testClusterConnection(clusterId);
    clusterFeedback.value = {
      ...clusterFeedback.value,
      [clusterId]: connectionFeedback(result)
    };
  });
}

/** syncCluster 처리의 핵심 작업 흐름을 실행한다. */
async function syncCluster(clusterId: string) {
  closeActionMenu();
  await runClusterAction(clusterId, 'sync', async () => {
    const result = await api.syncCluster(clusterId);
    clusterFeedback.value = {
      ...clusterFeedback.value,
      [clusterId]: {
        tone: 'info',
        summary: '동기화 작업 시작',
        detail: `동기화 작업을 시작했습니다.\njobId=${result.jobId}`
      }
    };
  });
}

/** deleteCluster 처리 대상과 관련 상태를 안전하게 정리한다. */
async function deleteCluster(cluster: ClusterResponse) {
  closeActionMenu();
  const confirmed = window.confirm(`${cluster.name} 클러스터 등록 정보를 삭제할까요? 실제 Kubernetes 리소스는 삭제하지 않습니다.`);
  if (!confirmed) {
    return;
  }

  await runClusterAction(cluster.id, 'delete', async () => {
    await api.deleteCluster(cluster.id);
    const nextFeedback = { ...clusterFeedback.value };
    delete nextFeedback[cluster.id];
    clusterFeedback.value = nextFeedback;
    await loadClusters();
  });
}

/** runClusterAction 처리의 핵심 작업 흐름을 실행한다. */
async function runClusterAction(clusterId: string, action: string, work: () => Promise<void>) {
  runningAction.value = {
    ...runningAction.value,
    [clusterId]: action
  };
  try {
    await work();
  } catch (error) {
    clusterFeedback.value = {
      ...clusterFeedback.value,
      [clusterId]: {
        tone: 'error',
        summary: '작업 실패',
        detail: error instanceof Error ? error.message : '작업을 처리하지 못했습니다.'
      }
    };
  } finally {
    const nextRunningAction = { ...runningAction.value };
    delete nextRunningAction[clusterId];
    runningAction.value = nextRunningAction;
  }
}

/** preparedKubeconfig 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function preparedKubeconfig() {
  let kubeconfig = form.value.kubeconfig;
  const serverOverride = form.value.kubeconfigServerOverride.trim();
  if (serverOverride) {
    kubeconfig = replaceKubeconfigServer(kubeconfig, serverOverride);
  }
  if (form.value.kubeconfigInsecureSkipTlsVerify) {
    kubeconfig = applyKubeconfigInsecureSkipTlsVerify(kubeconfig);
  }
  return kubeconfig;
}

/** replaceKubeconfigServer 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function replaceKubeconfigServer(kubeconfig: string, serverUrl: string) {
  return kubeconfig.replace(/^(\s*server:\s*).+$/m, `$1${serverUrl}`);
}

/** applyKubeconfigInsecureSkipTlsVerify 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function applyKubeconfigInsecureSkipTlsVerify(kubeconfig: string) {
  const normalized = kubeconfig.replace(/\r\n/g, '\n');
  const lines = normalized.split('\n');
  const nextLines: string[] = [];

  for (const line of lines) {
    if (/^\s*certificate-authority(-data)?:\s*/.test(line)) {
      continue;
    }
    if (/^\s*insecure-skip-tls-verify:\s*/.test(line)) {
      continue;
    }

    nextLines.push(line);

    const serverMatch = line.match(/^(\s*)server:\s*/);
    if (serverMatch) {
      nextLines.push(`${serverMatch[1]}insecure-skip-tls-verify: true`);
    }
  }

  return nextLines.join('\n');
}

/** connectionFeedback 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function connectionFeedback(result: ClusterConnectionTestResponse) {
  const failureMessage = result.message || '연결에 실패했습니다.';
  const namespaces = result.namespaces || [];
  return {
    tone: result.reachable ? 'success' as const : 'error' as const,
    summary: result.reachable
      ? `연결 정상${result.kubernetesVersion ? ` · ${result.kubernetesVersion}` : ''}`
      : connectionFailureSummary(failureMessage),
    detail: result.reachable
      ? [
          'Kubernetes API connection succeeded',
          result.kubernetesVersion ? `version: ${result.kubernetesVersion}` : null,
          namespaces.length > 0 ? `namespaces: ${namespaces.join(', ')}` : null
        ].filter(Boolean).join('\n')
      : failureMessage
  };
}

/** connectionFailureSummary 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function connectionFailureSummary(message: string) {
  if (message.includes('x509') || message.includes('certificate')) {
    return '연결 실패 · 인증서 오류';
  }
  if (message.includes('timeout') || message.includes('timed out')) {
    return '연결 실패 · 시간 초과';
  }
  if (message.includes('Unauthorized') || message.includes('Forbidden')) {
    return '연결 실패 · 권한 오류';
  }
  return '연결 실패';
}

/** showFeedbackDetail 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function showFeedbackDetail(clusterId: string) {
  detailFeedbackClusterId.value = clusterId;
}

/** closeFeedbackDetail 처리 대상과 관련 상태를 안전하게 정리한다. */
function closeFeedbackDetail() {
  detailFeedbackClusterId.value = null;
}

/** toggleActionMenu 처리 데이터를 화면 또는 API 표현으로 변환한다. */
function toggleActionMenu(event: MouseEvent, clusterId: string) {
  event.stopPropagation();
  if (activeActionClusterId.value === clusterId) {
    closeActionMenu();
    return;
  }

  const trigger = event.currentTarget as HTMLElement;
  const rect = trigger.getBoundingClientRect();
  const menuWidth = 184;
  const menuHeight = 188;
  const gap = 8;
  const horizontalPadding = 12;
  const verticalPadding = 12;
  const left = Math.min(
    Math.max(horizontalPadding, rect.right - menuWidth),
    window.innerWidth - menuWidth - horizontalPadding
  );
  const hasBottomSpace = rect.bottom + gap + menuHeight <= window.innerHeight - verticalPadding;
  const top = hasBottomSpace
    ? rect.bottom + gap
    : Math.max(verticalPadding, rect.top - gap - menuHeight);

  actionMenuPosition.value = { top, left };
  activeActionClusterId.value = clusterId;
}

/** closeActionMenu 처리 대상과 관련 상태를 안전하게 정리한다. */
function closeActionMenu() {
  activeActionClusterId.value = null;
}

/** openClusterDetail 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openClusterDetail(clusterId: string) {
  closeActionMenu();
  router.push({ name: 'cluster-detail', params: { clusterId } });
}
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1>{{ $t('pages.clustersTitle') }}</h1>
        <p>{{ $t('pages.clustersDescription') }}</p>
      </div>
      <div class="header-actions">
        <button class="secondary-button" type="button" @click="loadClusters">
          <i class="pi pi-refresh"></i>
          <span>{{ $t('common.refresh') }}</span>
        </button>
        <button class="primary-button" type="button" @click="showRegistration = true">
          <i class="pi pi-plus"></i>
          <span>{{ $t('pages.clustersRegister') }}</span>
        </button>
      </div>
    </header>
    <div v-if="errorMessage" class="inline-error">
      <i class="pi pi-exclamation-triangle"></i>
      <span>{{ errorMessage }}</span>
    </div>
    <div v-if="loading" class="empty-state">
      <i class="pi pi-spin pi-spinner"></i>
      <span>{{ $t('pages.clustersLoading') }}</span>
    </div>
    <div v-else-if="clusters.length === 0" class="empty-state">
      <i class="pi pi-cloud"></i>
      <span>아직 등록된 클러스터가 없습니다.</span>
    </div>
    <div v-else class="table-panel">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Environment</th>
            <th>Provider</th>
            <th>Region</th>
            <th>Status</th>
            <th>Operations</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="cluster in clusters" :key="cluster.id">
            <td>
              <strong>{{ cluster.name }}</strong>
              <small>{{ cluster.id }}</small>
            </td>
            <td>{{ cluster.environment || '-' }}</td>
            <td>{{ cluster.provider || '-' }}</td>
            <td>{{ cluster.region || '-' }}</td>
            <td>
              <span class="status-pill">{{ cluster.status || 'REGISTERED' }}</span>
              <small
                v-if="clusterFeedback[cluster.id]"
                class="operation-feedback"
                :class="clusterFeedback[cluster.id].tone"
              >
                <span>{{ clusterFeedback[cluster.id].summary }}</span>
                <button
                  v-if="clusterFeedback[cluster.id].detail"
                  type="button"
                  @click="showFeedbackDetail(cluster.id)"
                >
                  상세 보기
                </button>
              </small>
            </td>
            <td>
              <div class="action-menu">
                <button
                  class="action-menu-trigger"
                  title="작업"
                  type="button"
                  @click="toggleActionMenu($event, cluster.id)"
                >
                  <i class="pi pi-ellipsis-v"></i>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-if="activeActionCluster"
      class="action-menu-panel floating-action-menu"
      :style="{ top: `${actionMenuPosition.top}px`, left: `${actionMenuPosition.left}px` }"
      @click.stop
    >
      <button type="button" @click="openClusterDetail(activeActionCluster.id)">
        <i class="pi pi-search"></i>
        <span>상세</span>
      </button>
      <button type="button" @click="testConnection(activeActionCluster.id)">
        <i :class="runningAction[activeActionCluster.id] === 'connection-test' ? 'pi pi-spin pi-spinner' : 'pi pi-wifi'"></i>
        <span>연결 확인</span>
      </button>
      <button type="button" @click="syncCluster(activeActionCluster.id)">
        <i :class="runningAction[activeActionCluster.id] === 'sync' ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i>
        <span>동기화</span>
      </button>
      <button class="danger-action" type="button" @click="deleteCluster(activeActionCluster)">
        <i :class="runningAction[activeActionCluster.id] === 'delete' ? 'pi pi-spin pi-spinner' : 'pi pi-trash'"></i>
        <span>등록 삭제</span>
      </button>
    </div>

    <div v-if="detailFeedback" class="modal-backdrop" @click.self="closeFeedbackDetail">
      <section class="modal-panel feedback-detail-modal" aria-modal="true" role="dialog">
        <header class="modal-header">
          <div>
            <h2>작업 상세</h2>
            <p>{{ detailFeedbackCluster?.name || 'Cluster' }}</p>
          </div>
          <button class="icon-button" title="닫기" type="button" @click="closeFeedbackDetail">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <div class="feedback-detail-body">
          <span class="status-pill" :class="detailFeedback.tone">{{ detailFeedback.summary }}</span>
          <pre>{{ detailFeedback.detail }}</pre>
        </div>
        <footer class="modal-actions">
          <button class="secondary-button" type="button" @click="closeFeedbackDetail">
            <span>닫기</span>
          </button>
        </footer>
      </section>
    </div>

    <div v-if="showRegistration" class="modal-backdrop" @click.self="showRegistration = false">
      <section class="modal-panel cluster-registration" aria-modal="true" role="dialog">
        <header class="modal-header">
          <div>
            <h2>클러스터 등록</h2>
            <p>kubeconfig를 우선 사용하고, 불가할 때 ServiceAccount token을 적용합니다.</p>
          </div>
          <button class="icon-button" title="닫기" type="button" @click="showRegistration = false">
            <i class="pi pi-times"></i>
          </button>
        </header>

        <form class="form-grid" @submit.prevent="registerCluster">
          <div v-if="formError" class="inline-error form-wide">
            <i class="pi pi-exclamation-triangle"></i>
            <span>{{ formError }}</span>
          </div>

          <label class="form-field">
            <span>Name</span>
            <input v-model="form.name" placeholder="dev-cluster" required />
          </label>

          <label class="form-field">
            <span>Environment</span>
            <select v-model="form.environment">
              <option value="DEV">DEV</option>
              <option value="STAGING">STAGING</option>
              <option value="PROD">PROD</option>
              <option value="ETC">ETC</option>
            </select>
          </label>

          <label class="form-field">
            <span>Provider</span>
            <select v-model="form.provider">
              <option value="KIND">KIND</option>
              <option value="EKS">EKS</option>
              <option value="GKE">GKE</option>
              <option value="AKS">AKS</option>
              <option value="ON_PREM">ON_PREM</option>
              <option value="ETC">ETC</option>
            </select>
          </label>

          <label class="form-field">
            <span>Region</span>
            <input v-model="form.region" placeholder="ap-northeast-2" />
          </label>

          <label class="form-field form-wide">
            <span>Description</span>
            <input v-model="form.description" placeholder="Development Kubernetes cluster" />
          </label>

          <div class="form-field form-wide">
            <span>Credential</span>
            <div class="segmented-control">
              <button
                type="button"
                :class="{ active: form.credentialType === 'KUBECONFIG' }"
                @click="form.credentialType = 'KUBECONFIG'"
              >
                kubeconfig
              </button>
              <button
                type="button"
                :class="{ active: form.credentialType === 'SERVICE_ACCOUNT_TOKEN' }"
                @click="form.credentialType = 'SERVICE_ACCOUNT_TOKEN'"
              >
                ServiceAccount token
              </button>
            </div>
          </div>

          <template v-if="form.credentialType === 'KUBECONFIG'">
            <label class="form-field form-wide">
              <span>kubeconfig YAML</span>
              <textarea v-model="form.kubeconfig" rows="8" placeholder="apiVersion: v1&#10;clusters: ..." required />
            </label>
            <label class="form-field form-wide">
              <span>API server override</span>
              <input v-model="form.kubeconfigServerOverride" placeholder="https://10.10.10.89:6443" />
              <small class="field-help">
                kubeconfig의 server 값을 등록 시점에만 변경합니다. 원본 master node kubeconfig 파일은 변경하지 않습니다.
              </small>
            </label>
            <div class="form-field form-wide">
              <span>Test TLS mode</span>
              <label class="toggle-row">
                <input v-model="form.kubeconfigInsecureSkipTlsVerify" type="checkbox" />
                <span>테스트용으로 TLS 인증서 검증 건너뛰기</span>
              </label>
              <small class="field-help">
                활성화하면 등록 요청 kubeconfig에서 certificate-authority-data를 제거하고 insecure-skip-tls-verify를 추가합니다.
              </small>
            </div>
            <label class="file-field form-wide">
              <i class="pi pi-upload"></i>
              <span>kubeconfig 파일 불러오기</span>
              <input type="file" accept=".yaml,.yml,.conf,.config,text/yaml,text/plain" @change="importKubeconfig" />
            </label>
          </template>

          <template v-else>
            <label class="form-field form-wide">
              <span>API server URL</span>
              <input v-model="form.apiServerUrl" placeholder="https://10.0.0.1:6443" required />
            </label>
            <label class="form-field form-wide">
              <span>CA certificate</span>
              <textarea v-model="form.caCertificate" rows="5" placeholder="-----BEGIN CERTIFICATE-----" required />
            </label>
            <label class="form-field form-wide">
              <span>Bearer token</span>
              <textarea v-model="form.token" rows="4" placeholder="eyJhbGciOi..." required />
            </label>
          </template>

          <div class="form-field">
            <span>Namespace access</span>
            <label class="toggle-row">
              <input v-model="form.clusterWide" type="checkbox" />
              <span>Cluster-wide</span>
            </label>
            <small class="field-help">
              전체 리소스 동기화는 ServiceAccount에 Deployment, Pod, Service 등 cluster-wide list 권한이 있어야 합니다. 연결 확인 성공만으로 동기화 권한까지 보장되지는 않습니다.
            </small>
          </div>

          <label class="form-field">
            <span>Default namespace</span>
            <input v-model="form.defaultNamespace" placeholder="default" />
          </label>

          <label v-if="!form.clusterWide" class="form-field form-wide">
            <span>Allowed namespaces</span>
            <input v-model="form.allowedNamespacesText" placeholder="default, monitoring, ingress-nginx" />
          </label>

          <div class="form-field">
            <span>Auto sync</span>
            <label class="toggle-row">
              <input v-model="form.autoSyncEnabled" type="checkbox" />
              <span>Enabled</span>
            </label>
          </div>

          <label class="form-field">
            <span>Sync interval seconds</span>
            <input v-model.number="form.syncIntervalSeconds" min="60" step="60" type="number" />
          </label>

          <div class="form-field form-wide">
            <span>Registration check</span>
            <label class="toggle-row">
              <input v-model="form.testAfterRegister" type="checkbox" />
              <span>등록 완료 후 즉시 연결 확인</span>
            </label>
          </div>

          <footer class="modal-actions form-wide">
            <button class="secondary-button" type="button" @click="showRegistration = false">
              <span>취소</span>
            </button>
            <button class="primary-button" :disabled="!canSubmit || saving" type="submit">
              <i :class="saving ? 'pi pi-spin pi-spinner' : 'pi pi-check'"></i>
              <span>{{ saving ? '등록 중' : '등록' }}</span>
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
