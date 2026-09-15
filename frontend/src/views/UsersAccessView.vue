<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { api, type ClusterResponse, type OidcGroupMappingResponse, type TenantFeatureKey,
  type TenantMemberOffboardPlanResponse, type TenantMemberResponse, type WorkspaceResponse } from '@/api/client';
import { useAuthStore } from '@/stores/auth';
import { useTenancyStore } from '@/stores/tenancy';

type AccessTab = 'members' | 'groups' | 'features';
const auth = useAuthStore();
const tenancy = useTenancyStore();
const router = useRouter();
const members = ref<TenantMemberResponse[]>([]);
const mappings = ref<OidcGroupMappingResponse[]>([]);
const features = ref<Partial<Record<TenantFeatureKey, boolean>>>({});
const workspaces = ref<WorkspaceResponse[]>([]);
const clusters = ref<ClusterResponse[]>([]);
const tab = ref<AccessTab>('members');
const dialog = ref<'invite' | 'group' | 'offboard' | 'delete-mapping' | null>(null);
const busy = ref(false);
const message = ref('');
const confirmation = ref('');
const offboardPlan = ref<TenantMemberOffboardPlanResponse | null>(null);
const selectedMapping = ref<OidcGroupMappingResponse | null>(null);
const issuer = import.meta.env.VITE_OIDC_ISSUER ?? '';
const invite = reactive({ issuer, email: '', role: 'OPERATOR', scopeType: 'TENANT', workspaceId: '', clusterId: '', namespace: '' });
const group = reactive({ issuer, groupValue: '', role: 'OPERATOR', scopeType: 'TENANT', workspaceId: '', clusterId: '', namespace: '' });
const inviteClusters = computed(() => clusters.value.filter((item) => !invite.workspaceId || item.workspaceId === invite.workspaceId));
const groupClusters = computed(() => clusters.value.filter((item) => !group.workspaceId || item.workspaceId === group.workspaceId));
const canManageFeatures = computed(() => auth.hasCapability('tenant:manage'));
const featureItems: Array<{ key: TenantFeatureKey; label: string; description: string; mandatory?: boolean }> = [
  { key: 'CORE_OVERVIEW', label: 'Overview', description: 'Tenant 기본 현황과 진입 화면', mandatory: true },
  { key: 'CLUSTER_OPERATIONS', label: 'Cluster Operations', description: 'Cluster, Fleet와 운영 정책 메뉴' },
  { key: 'KUBERNETES_CONSOLE', label: 'Kubernetes Console', description: 'kubectl 기반 점검 Console' },
  { key: 'AI_OPERATIONS', label: 'AI Operations', description: '분석, Chat, Incident와 Runbook' },
  { key: 'APPLICATION_DELIVERY', label: 'Application Delivery', description: 'Helm Chart 관리와 배포 수명주기' },
  { key: 'AI_PROVIDER_ROUTING', label: 'AI Provider Routing', description: 'Tenant별 Provider와 목적별 모델 경로' },
  { key: 'ACCESS_CONTROL', label: 'Users & Access', description: '사용자와 OIDC Group 권한 관리', mandatory: true },
  { key: 'AUDIT', label: 'Audit', description: '감사 기록 열람', mandatory: true },
];

onMounted(async () => {
  await tenancy.load();
  [workspaces.value, clusters.value] = await Promise.all([
    api.listWorkspaces(tenancy.currentTenantId), api.listClusters({ tenantId: tenancy.currentTenantId }),
  ]);
  await load();
});

async function load(): Promise<void> {
  [members.value, mappings.value, features.value] = await Promise.all([
    api.listTenantMembers(tenancy.currentTenantId), api.listOidcGroupMappings(tenancy.currentTenantId),
    api.getTenantFeatures(tenancy.currentTenantId),
  ]);
}

function scopeFields(value: { scopeType: string; workspaceId: string; clusterId: string; namespace: string }) {
  // 선택 범위보다 넓은 식별자를 전송하지 않아 서버의 권한 대상을 명확하게 유지한다.
  return {
    workspaceId: ['WORKSPACE', 'CLUSTER', 'NAMESPACE'].includes(value.scopeType) ? value.workspaceId || undefined : undefined,
    clusterId: ['CLUSTER', 'NAMESPACE'].includes(value.scopeType) ? value.clusterId || undefined : undefined,
    namespace: value.scopeType === 'NAMESPACE' ? value.namespace || undefined : undefined,
  };
}

async function run(action: () => Promise<void>, fallback: string): Promise<void> {
  busy.value = true; message.value = '';
  try { await action(); } catch (error) { message.value = error instanceof Error ? error.message : fallback; }
  finally { busy.value = false; }
}

async function inviteMember(): Promise<void> {
  await run(async () => { await api.inviteTenantMember(tenancy.currentTenantId, { ...invite, ...scopeFields(invite) }); dialog.value = null; message.value = '초대 대기 구성원을 추가했습니다.'; await load(); }, '구성원을 추가하지 못했습니다.');
}
async function addGroup(): Promise<void> {
  await run(async () => { await api.createOidcGroupMapping(tenancy.currentTenantId, { ...group, ...scopeFields(group) }); dialog.value = null; message.value = 'OIDC Group mapping을 저장했습니다.'; await load(); }, 'Group mapping을 저장하지 못했습니다.');
}
async function toggleMember(member: TenantMemberResponse): Promise<void> {
  await run(async () => { await api.setTenantMemberStatus(tenancy.currentTenantId, member.id, member.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED'); await load(); }, '구성원 상태를 변경하지 못했습니다.');
}
async function prepareOffboard(member: TenantMemberResponse): Promise<void> {
  await run(async () => { offboardPlan.value = await api.getTenantMemberOffboardPlan(tenancy.currentTenantId, member.id); confirmation.value = ''; dialog.value = 'offboard'; }, '오프보딩 영향을 확인하지 못했습니다.');
}
async function executeOffboard(): Promise<void> {
  if (!offboardPlan.value) return;
  await run(async () => { await api.offboardTenantMember(tenancy.currentTenantId, offboardPlan.value!.membershipId, confirmation.value); dialog.value = null; message.value = 'Tenant 접근을 제거했습니다. IdP 계정은 유지됩니다.'; await load(); }, '사용자를 오프보딩하지 못했습니다.');
}
async function toggleMapping(mapping: OidcGroupMappingResponse): Promise<void> {
  await run(async () => { await api.setOidcGroupMappingActive(tenancy.currentTenantId, mapping.id, !mapping.active); await load(); }, 'Mapping 상태를 변경하지 못했습니다.');
}
function prepareMappingDelete(mapping: OidcGroupMappingResponse): void {
  selectedMapping.value = mapping; confirmation.value = ''; dialog.value = 'delete-mapping';
}
async function deleteMapping(): Promise<void> {
  if (!selectedMapping.value || confirmation.value !== `DELETE ${selectedMapping.value.groupValue}`) return;
  await run(async () => { await api.deleteOidcGroupMapping(tenancy.currentTenantId, selectedMapping.value!.id); dialog.value = null; message.value = 'OIDC Group mapping을 삭제했습니다.'; await load(); }, 'Mapping을 삭제하지 못했습니다.');
}
async function toggleFeature(item: { key: TenantFeatureKey; mandatory?: boolean }): Promise<void> {
  if (item.mandatory || !canManageFeatures.value) return;
  await run(async () => { features.value = await api.updateTenantFeature(tenancy.currentTenantId, item.key, !features.value[item.key]); await auth.loadEffectiveAccess(tenancy.currentTenantId, tenancy.currentWorkspaceId || undefined); message.value = 'Tenant 메뉴 및 API 기능 정책을 변경했습니다.'; }, '기능 정책을 변경하지 못했습니다.');
}
</script>

<template>
  <section class="page delivery-page access-page">
    <header class="delivery-hero"><div><span class="delivery-eyebrow">TENANT GOVERNANCE</span><h1>사용자 및 권한</h1><p>Keycloak 사용자를 Tenant에 연결하고 역할, 범위와 제품 기능을 관리합니다.</p></div><div class="header-actions"><button v-if="!auth.session.localDevelopment && auth.hasCapability('identity:manage')" class="secondary-button" type="button" @click="router.push('/settings/access')"><i class="pi pi-id-card"></i> 플랫폼 계정 권한</button><button v-if="tab !== 'features'" class="primary-button" type="button" @click="dialog = tab === 'members' ? 'invite' : 'group'"><i class="pi pi-plus"></i> {{ tab === 'members' ? '사용자 초대' : 'Group Mapping' }}</button></div></header>
    <div class="access-summary"><div><span>Tenant</span><strong>{{ tenancy.currentTenant?.name }}</strong></div><div><span>Members</span><strong>{{ members.length }}</strong></div><div><span>Active groups</span><strong>{{ mappings.filter(item => item.active).length }}</strong></div><div><span>권한 합산</span><strong>Grant union</strong></div></div>
    <div class="access-tabs"><button type="button" :class="{ active: tab === 'members' }" @click="tab = 'members'">구성원</button><button type="button" :class="{ active: tab === 'groups' }" @click="tab = 'groups'">OIDC Group Mapping</button><button type="button" :class="{ active: tab === 'features' }" @click="tab = 'features'">메뉴 및 기능</button></div>
    <div v-if="message" class="delivery-notice">{{ message }}</div>

    <div v-if="tab === 'members'" class="access-table"><div class="access-row header"><span>사용자</span><span>역할</span><span>범위</span><span>상태</span><span>작업</span></div><div v-for="member in members" :key="member.id" class="access-row"><div class="member-cell"><span class="identity-avatar">{{ (member.displayName || member.email || '?').slice(0, 1).toUpperCase() }}</span><div><strong>{{ member.displayName || member.email || 'OIDC 연결 대기' }}</strong><small>{{ member.username || member.email }}</small></div></div><span>{{ member.role }}</span><span>{{ member.scopeType }}<small v-if="member.namespace"> / {{ member.namespace }}</small></span><span class="status-dot" :class="member.status === 'ACTIVE' ? 'success' : 'neutral'">{{ member.status }}</span><div class="delivery-inline-actions"><button v-if="member.status !== 'OFFBOARDED'" class="text-button" type="button" :disabled="busy" @click="toggleMember(member)">{{ member.status === 'SUSPENDED' ? '활성화' : '중지' }}</button><button v-if="member.status !== 'OFFBOARDED'" class="text-button danger" type="button" :disabled="busy" @click="prepareOffboard(member)">제거</button></div></div></div>
    <div v-else-if="tab === 'groups'" class="access-table"><div class="access-row header"><span>OIDC Group</span><span>역할</span><span>범위</span><span>상태</span><span>작업</span></div><div v-for="mapping in mappings" :key="mapping.id" class="access-row"><div><strong>{{ mapping.groupValue }}</strong><small>{{ mapping.issuer }}</small></div><span>{{ mapping.role }}</span><span>{{ mapping.scopeType }}</span><span class="status-dot" :class="mapping.active ? 'success' : 'neutral'">{{ mapping.active ? 'ACTIVE' : 'INACTIVE' }}</span><div class="delivery-inline-actions"><button class="text-button" type="button" :disabled="busy" @click="toggleMapping(mapping)">{{ mapping.active ? '비활성화' : '활성화' }}</button><button class="text-button danger" type="button" :disabled="busy" @click="prepareMappingDelete(mapping)">삭제</button></div></div></div>
    <div v-else class="feature-policy-grid"><article v-for="item in featureItems" :key="item.key" class="feature-policy-card"><div><span>{{ item.mandatory ? '필수 기능' : '선택 기능' }}</span><h3>{{ item.label }}</h3><p>{{ item.description }}</p></div><button class="feature-toggle" :class="{ enabled: features[item.key] }" type="button" role="switch" :aria-checked="features[item.key]" :disabled="busy || item.mandatory || !canManageFeatures" @click="toggleFeature(item)"><span></span>{{ features[item.key] ? '사용' : '중지' }}</button></article><p v-if="!canManageFeatures" class="delivery-notice">기능 정책 변경은 Tenant Admin 또는 Platform Manager만 할 수 있습니다.</p></div>

    <div v-if="dialog === 'invite'" class="delivery-modal-backdrop" @click.self="dialog = null"><form class="delivery-modal narrow" @submit.prevent="inviteMember"><header><div><span class="delivery-eyebrow">DIRECT MEMBERSHIP</span><h2>사용자 초대</h2><p>OIDC 사용자를 만들지 않습니다. 일치하는 첫 로그인 identity에 권한을 연결합니다.</p></div><button class="icon-button" type="button" @click="dialog = null"><i class="pi pi-times"></i></button></header><div class="delivery-form-grid"><label class="wide">Issuer<input v-model="invite.issuer" required /></label><label class="wide">Email<input v-model="invite.email" type="email" required /></label><label>역할<select v-model="invite.role"><option value="TENANT_ADMIN">Tenant Admin</option><option value="CLUSTER_ADMIN">Cluster Admin</option><option value="OPERATOR">Operator</option><option value="VIEWER">Viewer</option></select></label><label>범위<select v-model="invite.scopeType"><option value="TENANT">Tenant 전체</option><option value="WORKSPACE">Workspace</option><option value="CLUSTER">Cluster</option><option value="NAMESPACE">Namespace</option></select></label><label v-if="invite.scopeType !== 'TENANT'">Workspace<select v-model="invite.workspaceId" required><option disabled value="">Workspace 선택</option><option v-for="workspace in workspaces" :key="workspace.id" :value="workspace.id">{{ workspace.name }}</option></select></label><label v-if="['CLUSTER', 'NAMESPACE'].includes(invite.scopeType)">Cluster<select v-model="invite.clusterId" required><option disabled value="">Cluster 선택</option><option v-for="cluster in inviteClusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label><label v-if="invite.scopeType === 'NAMESPACE'">Namespace<input v-model="invite.namespace" required /></label></div><footer><button class="secondary-button" type="button" @click="dialog = null">취소</button><button class="primary-button" :disabled="busy">초대 대기 등록</button></footer></form></div>
    <div v-if="dialog === 'group'" class="delivery-modal-backdrop" @click.self="dialog = null"><form class="delivery-modal narrow" @submit.prevent="addGroup"><header><div><span class="delivery-eyebrow">OIDC AUTOMATION</span><h2>Group Mapping</h2><p>Keycloak Group 구성원이 로그인할 때 Tenant 역할을 자동으로 합산합니다.</p></div><button class="icon-button" type="button" @click="dialog = null"><i class="pi pi-times"></i></button></header><div class="delivery-form-grid"><label class="wide">Issuer<input v-model="group.issuer" required /></label><label class="wide">Group path<input v-model="group.groupValue" required placeholder="/companies/aa/operators" /></label><label>역할<select v-model="group.role"><option value="TENANT_ADMIN">Tenant Admin</option><option value="CLUSTER_ADMIN">Cluster Admin</option><option value="OPERATOR">Operator</option><option value="VIEWER">Viewer</option></select></label><label>범위<select v-model="group.scopeType"><option value="TENANT">Tenant 전체</option><option value="WORKSPACE">Workspace</option><option value="CLUSTER">Cluster</option><option value="NAMESPACE">Namespace</option></select></label><label v-if="group.scopeType !== 'TENANT'">Workspace<select v-model="group.workspaceId" required><option disabled value="">Workspace 선택</option><option v-for="workspace in workspaces" :key="workspace.id" :value="workspace.id">{{ workspace.name }}</option></select></label><label v-if="['CLUSTER', 'NAMESPACE'].includes(group.scopeType)">Cluster<select v-model="group.clusterId" required><option disabled value="">Cluster 선택</option><option v-for="cluster in groupClusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label><label v-if="group.scopeType === 'NAMESPACE'">Namespace<input v-model="group.namespace" required /></label></div><footer><button class="secondary-button" type="button" @click="dialog = null">취소</button><button class="primary-button" :disabled="busy">Mapping 저장</button></footer></form></div>
    <div v-if="dialog === 'offboard' && offboardPlan" class="delivery-modal-backdrop" @click.self="dialog = null"><form class="delivery-modal narrow" @submit.prevent="executeOffboard"><header><div><span class="delivery-eyebrow">IRREVERSIBLE TENANT ACTION</span><h2>Tenant 사용자 제거</h2><p>역할 {{ offboardPlan.roleBindingsToRemove }}개를 제거합니다. Keycloak 계정은 유지됩니다.</p></div></header><label class="delivery-confirm-field">아래 문구를 정확히 입력하세요.<code>{{ offboardPlan.confirmationText }}</code><input v-model="confirmation" autocomplete="off" /></label><footer><button class="secondary-button" type="button" @click="dialog = null">취소</button><button class="danger-button" :disabled="busy || confirmation !== offboardPlan.confirmationText">접근 제거</button></footer></form></div>
    <div v-if="dialog === 'delete-mapping' && selectedMapping" class="delivery-modal-backdrop" @click.self="dialog = null"><form class="delivery-modal narrow" @submit.prevent="deleteMapping"><header><div><span class="delivery-eyebrow">GROUP MAPPING DELETE</span><h2>Mapping 삭제</h2><p>자동 권한 부여가 중단되며 기존 직접 Membership은 유지됩니다.</p></div></header><label class="delivery-confirm-field">아래 문구를 정확히 입력하세요.<code>DELETE {{ selectedMapping.groupValue }}</code><input v-model="confirmation" autocomplete="off" /></label><footer><button class="secondary-button" type="button" @click="dialog = null">취소</button><button class="danger-button" :disabled="busy || confirmation !== `DELETE ${selectedMapping.groupValue}`">Mapping 삭제</button></footer></form></div>
  </section>
</template>
