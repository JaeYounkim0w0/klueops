<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { api, type ClusterResponse, type OidcGroupMappingResponse, type TenantMemberResponse, type WorkspaceResponse } from '@/api/client';
import { useTenancyStore } from '@/stores/tenancy';

const tenancy = useTenancyStore();
const members = ref<TenantMemberResponse[]>([]);
const mappings = ref<OidcGroupMappingResponse[]>([]);
const workspaces = ref<WorkspaceResponse[]>([]);
const clusters = ref<ClusterResponse[]>([]);
const tab = ref<'members' | 'groups'>('members');
const inviteOpen = ref(false);
const groupOpen = ref(false);
const message = ref('');
const issuer = import.meta.env.VITE_OIDC_ISSUER ?? '';
const invite = reactive({ issuer, email: '', role: 'OPERATOR', scopeType: 'TENANT', workspaceId: '', clusterId: '', namespace: '' });
const group = reactive({ issuer, groupValue: '', role: 'OPERATOR', scopeType: 'TENANT', workspaceId: '', clusterId: '', namespace: '' });
const inviteClusters = computed(() => clusters.value.filter((item) => !invite.workspaceId || item.workspaceId === invite.workspaceId));
const groupClusters = computed(() => clusters.value.filter((item) => !group.workspaceId || item.workspaceId === group.workspaceId));

onMounted(async () => { await tenancy.load(); [workspaces.value, clusters.value] = await Promise.all([api.listWorkspaces(tenancy.currentTenantId), api.listClusters({ tenantId: tenancy.currentTenantId })]); await load(); });
async function load() { [members.value, mappings.value] = await Promise.all([api.listTenantMembers(tenancy.currentTenantId), api.listOidcGroupMappings(tenancy.currentTenantId)]); }
function scopeFields(value: { scopeType: string; workspaceId: string; clusterId: string; namespace: string }) {
  return {
    workspaceId: ['WORKSPACE', 'CLUSTER', 'NAMESPACE'].includes(value.scopeType) ? value.workspaceId || undefined : undefined,
    clusterId: ['CLUSTER', 'NAMESPACE'].includes(value.scopeType) ? value.clusterId || undefined : undefined,
    namespace: value.scopeType === 'NAMESPACE' ? value.namespace || undefined : undefined,
  };
}
async function inviteMember() {
  try { await api.inviteTenantMember(tenancy.currentTenantId, { ...invite, ...scopeFields(invite) }); inviteOpen.value = false; message.value = '초대 대기 구성원을 추가했습니다. 첫 로그인 시 OIDC identity와 연결됩니다.'; await load(); }
  catch (error) { message.value = error instanceof Error ? error.message : '구성원을 추가하지 못했습니다.'; }
}
async function addGroup() {
  try { await api.createOidcGroupMapping(tenancy.currentTenantId, { ...group, ...scopeFields(group) }); groupOpen.value = false; message.value = 'OIDC Group mapping을 저장했습니다.'; await load(); }
  catch (error) { message.value = error instanceof Error ? error.message : 'Group mapping을 저장하지 못했습니다.'; }
}
async function toggle(member: TenantMemberResponse) {
  await api.setTenantMemberStatus(tenancy.currentTenantId, member.id, member.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED'); await load();
}
</script>

<template>
  <section class="page delivery-page access-page">
    <header class="delivery-hero"><div><span class="delivery-eyebrow">TENANT GOVERNANCE</span><h1>Users & Access</h1><p>Keycloak 사용자를 Tenant에 연결하고 역할과 Cluster/Namespace 범위를 명시적으로 관리합니다.</p></div><button class="primary-button" type="button" @click="tab === 'members' ? inviteOpen = true : groupOpen = true"><i class="pi pi-plus"></i> {{ tab === 'members' ? '사용자 초대' : 'Group Mapping' }}</button></header>
    <div class="access-summary"><div><span>Tenant</span><strong>{{ tenancy.currentTenant?.name }}</strong></div><div><span>Members</span><strong>{{ members.length }}</strong></div><div><span>Active groups</span><strong>{{ mappings.filter(item => item.active).length }}</strong></div><div><span>정책</span><strong>Grant union</strong></div></div>
    <div class="access-tabs"><button type="button" :class="{ active: tab === 'members' }" @click="tab = 'members'">구성원</button><button type="button" :class="{ active: tab === 'groups' }" @click="tab = 'groups'">OIDC Group Mapping</button></div>
    <div v-if="message" class="delivery-notice">{{ message }}</div>
    <div v-if="tab === 'members'" class="access-table"><div class="access-row header"><span>사용자</span><span>역할</span><span>범위</span><span>상태</span><span></span></div><div v-for="member in members" :key="member.id" class="access-row"><div class="member-cell"><span class="identity-avatar">{{ (member.displayName || member.email || '?').slice(0,1).toUpperCase() }}</span><div><strong>{{ member.displayName || member.email || 'OIDC 연결 대기' }}</strong><small>{{ member.username || member.email }}</small></div></div><span>{{ member.role }}</span><span>{{ member.scopeType }}<small v-if="member.namespace"> / {{ member.namespace }}</small></span><span class="status-dot" :class="member.status === 'ACTIVE' ? 'success' : 'neutral'">{{ member.status }}</span><button v-if="member.status !== 'OFFBOARDED'" class="text-button" type="button" @click="toggle(member)">{{ member.status === 'SUSPENDED' ? '활성화' : '중지' }}</button></div></div>
    <div v-else class="access-table"><div class="access-row header"><span>OIDC Group</span><span>역할</span><span>범위</span><span>상태</span><span></span></div><div v-for="mapping in mappings" :key="mapping.id" class="access-row"><div><strong>{{ mapping.groupValue }}</strong><small>{{ mapping.issuer }}</small></div><span>{{ mapping.role }}</span><span>{{ mapping.scopeType }}</span><span class="status-dot" :class="mapping.active ? 'success' : 'neutral'">{{ mapping.active ? 'ACTIVE' : 'INACTIVE' }}</span><span></span></div></div>
    <div v-if="inviteOpen" class="delivery-modal-backdrop" @click.self="inviteOpen = false"><form class="delivery-modal narrow" @submit.prevent="inviteMember"><header><div><span class="delivery-eyebrow">DIRECT MEMBERSHIP</span><h2>사용자 초대</h2><p>OIDC에 사용자를 생성하지 않습니다. 이메일이 일치하는 첫 로그인 identity에 Tenant 권한을 연결합니다.</p></div><button class="icon-button" type="button" @click="inviteOpen = false"><i class="pi pi-times"></i></button></header><div class="delivery-form-grid"><label class="wide">Issuer<input v-model="invite.issuer" required placeholder="https://keycloak.example.com/realms/klueops" /></label><label class="wide">Email<input v-model="invite.email" type="email" required /></label><label>역할<select v-model="invite.role"><option value="TENANT_ADMIN">Tenant Admin</option><option value="CLUSTER_ADMIN">Cluster Admin</option><option value="OPERATOR">Operator</option><option value="VIEWER">Viewer</option></select></label><label>범위<select v-model="invite.scopeType"><option value="TENANT">Tenant 전체</option><option value="WORKSPACE">Workspace</option><option value="CLUSTER">Cluster</option><option value="NAMESPACE">Namespace</option></select></label><label v-if="invite.scopeType !== 'TENANT'">Workspace<select v-model="invite.workspaceId" required><option disabled value="">Workspace 선택</option><option v-for="workspace in workspaces" :key="workspace.id" :value="workspace.id">{{ workspace.name }}</option></select></label><label v-if="['CLUSTER', 'NAMESPACE'].includes(invite.scopeType)">Cluster<select v-model="invite.clusterId" required><option disabled value="">Cluster 선택</option><option v-for="cluster in inviteClusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label><label v-if="invite.scopeType === 'NAMESPACE'">Namespace<input v-model="invite.namespace" required placeholder="default" /></label></div><footer><button class="secondary-button" type="button" @click="inviteOpen = false">취소</button><button class="primary-button">초대 대기 등록</button></footer></form></div>
    <div v-if="groupOpen" class="delivery-modal-backdrop" @click.self="groupOpen = false"><form class="delivery-modal narrow" @submit.prevent="addGroup"><header><div><span class="delivery-eyebrow">OIDC AUTOMATION</span><h2>Group Mapping</h2><p>Keycloak Group 구성원이 로그인할 때 Tenant 역할을 자동으로 합산합니다.</p></div><button class="icon-button" type="button" @click="groupOpen = false"><i class="pi pi-times"></i></button></header><div class="delivery-form-grid"><label class="wide">Issuer<input v-model="group.issuer" required /></label><label class="wide">Group path<input v-model="group.groupValue" required placeholder="/companies/aa/operators" /></label><label>역할<select v-model="group.role"><option value="TENANT_ADMIN">Tenant Admin</option><option value="CLUSTER_ADMIN">Cluster Admin</option><option value="OPERATOR">Operator</option><option value="VIEWER">Viewer</option></select></label><label>범위<select v-model="group.scopeType"><option value="TENANT">Tenant 전체</option><option value="WORKSPACE">Workspace</option><option value="CLUSTER">Cluster</option><option value="NAMESPACE">Namespace</option></select></label><label v-if="group.scopeType !== 'TENANT'">Workspace<select v-model="group.workspaceId" required><option disabled value="">Workspace 선택</option><option v-for="workspace in workspaces" :key="workspace.id" :value="workspace.id">{{ workspace.name }}</option></select></label><label v-if="['CLUSTER', 'NAMESPACE'].includes(group.scopeType)">Cluster<select v-model="group.clusterId" required><option disabled value="">Cluster 선택</option><option v-for="cluster in groupClusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }}</option></select></label><label v-if="group.scopeType === 'NAMESPACE'">Namespace<input v-model="group.namespace" required placeholder="default" /></label></div><footer><button class="secondary-button" type="button" @click="groupOpen = false">취소</button><button class="primary-button">Mapping 저장</button></footer></form></div>
  </section>
</template>
