<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ApiError, api, type ManagedRunbookResponse, type RunbookVersionResponse, type RunbookWriteRequest } from '@/api/client';
import { useAuthStore } from '@/stores/auth';
import { formatTimestamp } from '@/utils/time';

const auth = useAuthStore();
const runbooks = ref<ManagedRunbookResponse[]>([]);
const versions = ref<RunbookVersionResponse[]>([]);
const loading = ref(true);
const saving = ref(false);
const error = ref('');
const search = ref('');
const category = ref('');
const source = ref('ALL');
const mode = ref<'beginner' | 'expert'>('beginner');
const expanded = ref<string | null>(null);
const editorOpen = ref(false);
const versionsOpen = ref(false);
const editingId = ref<string | null>(null);
const selectedRunbook = ref<ManagedRunbookResponse | null>(null);
const canEdit = computed(() => auth.hasCapability('operation:execute'));

const emptyForm = (): RunbookWriteRequest => ({
  signal: '', category: '', resourceKind: 'Pod', title: '', beginnerExplanation: '',
  verificationCommand: '', expectedResult: '', safeAction: '', validationCommand: '',
  rollbackGuidance: '', safetyLevel: 'READ_ONLY', enabled: true, changeNote: ''
});
const form = ref<RunbookWriteRequest>(emptyForm());

const categories = computed(() => [...new Set(runbooks.value.map((item) => item.category))].sort());
const filtered = computed(() => runbooks.value.filter((item) => {
  const text = `${item.title} ${item.signal} ${item.category} ${item.resourceKind ?? ''}`.toLowerCase();
  return (!search.value.trim() || text.includes(search.value.trim().toLowerCase()))
    && (!category.value || item.category === category.value)
    && (source.value === 'ALL' || item.sourceType === source.value);
}));

async function load() {
  loading.value = true;
  error.value = '';
  try { runbooks.value = await api.listRunbookLibrary(); }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Runbook을 불러오지 못했습니다.'; }
  finally { loading.value = false; }
}

function createRunbook() {
  editingId.value = null;
  form.value = emptyForm();
  editorOpen.value = true;
}

function editRunbook(runbook: ManagedRunbookResponse) {
  if (runbook.sourceType !== 'CUSTOM') return;
  editingId.value = runbook.id;
  form.value = {
    signal: runbook.signal, category: runbook.category, resourceKind: runbook.resourceKind,
    title: runbook.title, beginnerExplanation: runbook.beginnerExplanation,
    verificationCommand: runbook.verificationCommand, expectedResult: runbook.expectedResult,
    safeAction: runbook.safeAction, validationCommand: runbook.validationCommand,
    rollbackGuidance: runbook.rollbackGuidance, safetyLevel: runbook.safetyLevel,
    enabled: runbook.enabled, changeNote: ''
  };
  editorOpen.value = true;
}

async function saveRunbook() {
  saving.value = true;
  error.value = '';
  try {
    if (editingId.value) await api.updateCustomRunbook(editingId.value, form.value);
    else await api.createCustomRunbook(form.value);
    editorOpen.value = false;
    await load();
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Runbook을 저장하지 못했습니다.'; }
  finally { saving.value = false; }
}

async function duplicate(runbook: ManagedRunbookResponse) {
  try { await api.duplicateRunbook(runbook.id); await load(); }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Runbook 복제에 실패했습니다.'; }
}

async function toggle(runbook: ManagedRunbookResponse) {
  try { await api.setCustomRunbookEnabled(runbook.id, !runbook.enabled); await load(); }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Runbook 상태를 변경하지 못했습니다.'; }
}

async function remove(runbook: ManagedRunbookResponse) {
  if (!window.confirm(`사용자 Runbook '${runbook.title}'과 버전 이력을 삭제할까요?`)) return;
  try { await api.deleteCustomRunbook(runbook.id); await load(); }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : 'Runbook을 삭제하지 못했습니다.'; }
}

async function showVersions(runbook: ManagedRunbookResponse) {
  selectedRunbook.value = runbook;
  versionsOpen.value = true;
  versions.value = await api.listCustomRunbookVersions(runbook.id);
}

async function restore(version: RunbookVersionResponse) {
  if (!selectedRunbook.value || !window.confirm(`v${version.version} 내용을 새 버전으로 복원할까요?`)) return;
  await api.restoreCustomRunbookVersion(selectedRunbook.value.id, version.version);
  versionsOpen.value = false;
  await load();
}

async function copy(command: string) { await navigator.clipboard.writeText(command); }
onMounted(load);
</script>

<template>
  <section class="page runbook-library-page">
    <header class="page-header operations-page-header">
      <div><span class="page-eyebrow">OPERATIONS KNOWLEDGE</span><h1>Runbooks</h1><p>검증된 시스템 절차를 사용하거나 팀의 운영 지식을 버전으로 관리합니다.</p></div>
      <div class="page-header-actions">
        <div class="analysis-mode-switch"><button type="button" :class="{ active: mode === 'beginner' }" @click="mode = 'beginner'">초보자</button><button type="button" :class="{ active: mode === 'expert' }" @click="mode = 'expert'">숙련자</button></div>
        <button v-if="canEdit" class="primary-button" type="button" @click="createRunbook"><i class="pi pi-plus"></i><span>Runbook 작성</span></button>
      </div>
    </header>

    <section class="runbook-library-guide">
      <i class="pi pi-shield"></i><div><strong>시스템 절차는 보호됩니다</strong><p>시스템 Runbook은 바로 수정하지 않고 복제해 팀 절차로 발전시킵니다. 모든 사용자 Runbook 변경은 새 버전으로 남습니다.</p></div>
    </section>
    <section class="operations-filter-bar"><label class="wide"><span>Search</span><input v-model="search" type="search" placeholder="FailedMount, Port, PVC..."></label><label><span>Source</span><select v-model="source"><option value="ALL">전체</option><option value="SYSTEM">시스템</option><option value="CUSTOM">사용자</option></select></label><label><span>Category</span><select v-model="category"><option value="">전체 분류</option><option v-for="item in categories" :key="item">{{ item }}</option></select></label></section>
    <div v-if="error" class="inline-feedback error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
    <div v-if="loading" class="operations-empty-state"><i class="pi pi-spin pi-spinner"></i><span>Runbook을 불러오고 있습니다.</span></div>

    <div v-else class="runbook-library-list">
      <article v-for="runbook in filtered" :key="runbook.id" :class="{ expanded: expanded === runbook.id, disabled: !runbook.enabled }">
        <button type="button" class="runbook-library-summary" @click="expanded = expanded === runbook.id ? null : runbook.id">
          <span class="runbook-signal-icon"><i class="pi pi-book"></i></span>
          <span><small>{{ runbook.category }} · {{ runbook.resourceKind || 'Kubernetes' }}</small><strong class="runbook-library-title">{{ runbook.title }}</strong><p>{{ mode === 'beginner' ? runbook.beginnerExplanation : `${runbook.signal} · v${runbook.version} · ${runbook.safetyLevel}` }}</p></span>
          <span class="runbook-badges"><span class="status-pill" :class="runbook.sourceType === 'SYSTEM' ? 'low' : 'success'">{{ runbook.sourceType === 'SYSTEM' ? 'SYSTEM' : 'CUSTOM' }}</span><span v-if="!runbook.enabled" class="status-pill warning">OFF</span></span>
          <i class="pi" :class="expanded === runbook.id ? 'pi-chevron-up' : 'pi-chevron-down'"></i>
        </button>
        <div v-if="expanded === runbook.id" class="runbook-library-detail">
          <div class="runbook-detail-toolbar">
            <span><i class="pi pi-user"></i>{{ runbook.owner }}<template v-if="runbook.updatedAt"> · {{ formatTimestamp(runbook.updatedAt) }}</template></span>
            <div v-if="canEdit">
              <button class="icon-button" type="button" title="사용자 Runbook으로 복제" aria-label="사용자 Runbook으로 복제" @click="duplicate(runbook)"><i class="pi pi-copy"></i></button>
              <template v-if="runbook.sourceType === 'CUSTOM'"><button class="icon-button" type="button" title="편집" aria-label="편집" @click="editRunbook(runbook)"><i class="pi pi-pencil"></i></button><button class="icon-button" type="button" title="버전 이력" aria-label="버전 이력" @click="showVersions(runbook)"><i class="pi pi-history"></i></button><button class="icon-button" type="button" :title="runbook.enabled ? '비활성화' : '활성화'" :aria-label="runbook.enabled ? '비활성화' : '활성화'" @click="toggle(runbook)"><i class="pi" :class="runbook.enabled ? 'pi-pause' : 'pi-play'"></i></button><button class="icon-button danger" type="button" title="삭제" aria-label="삭제" @click="remove(runbook)"><i class="pi pi-trash"></i></button></template>
            </div>
          </div>
          <div class="runbook-sequence-step"><span>1</span><div><strong>원인 확인</strong><p>{{ runbook.expectedResult }}</p><code>{{ runbook.verificationCommand }}</code></div><button class="icon-button" type="button" title="명령 복사" aria-label="명령 복사" @click="copy(runbook.verificationCommand)"><i class="pi pi-copy"></i></button></div>
          <div v-if="runbook.safeAction" class="runbook-sequence-step"><span>2</span><div><strong>안전 조치</strong><p>{{ runbook.safeAction }}</p></div></div>
          <div class="runbook-sequence-step"><span>{{ runbook.safeAction ? 3 : 2 }}</span><div><strong>조치 후 검증</strong><code>{{ runbook.validationCommand }}</code></div><button class="icon-button" type="button" title="검증 명령 복사" aria-label="검증 명령 복사" @click="copy(runbook.validationCommand)"><i class="pi pi-copy"></i></button></div>
          <div v-if="runbook.rollbackGuidance" class="runbook-rollback-note"><i class="pi pi-history"></i><div><strong>Rollback 기준</strong><p>{{ runbook.rollbackGuidance }}</p></div></div>
        </div>
      </article>
      <div v-if="!filtered.length" class="operations-empty-state"><i class="pi pi-search"></i><span>조건에 맞는 Runbook이 없습니다.</span></div>
    </div>

    <div v-if="editorOpen" class="modal-backdrop" @click.self="editorOpen = false"><section class="modal-card runbook-editor-modal" role="dialog" aria-modal="true"><header><div><span class="page-eyebrow">VERSIONED RUNBOOK</span><h2>{{ editingId ? 'Runbook 편집' : 'Runbook 작성' }}</h2><p>명령의 목적과 기대 결과를 분리해 다음 운영자도 안전하게 판단할 수 있게 합니다.</p></div><button class="icon-button" type="button" title="닫기" aria-label="닫기" @click="editorOpen = false"><i class="pi pi-times"></i></button></header><form class="runbook-editor-form" @submit.prevent="saveRunbook">
      <div class="runbook-form-grid"><label><span>Title</span><input v-model="form.title" required maxlength="255"></label><label><span>Signal</span><input v-model="form.signal" required maxlength="100"></label><label><span>Category</span><input v-model="form.category" required maxlength="100"></label><label><span>Resource kind</span><input v-model="form.resourceKind" maxlength="100"></label><label><span>Safety</span><select v-model="form.safetyLevel"><option>READ_ONLY</option><option>CHANGE_REQUIRES_REVIEW</option><option>DESTRUCTIVE</option></select></label><label class="toggle-label"><input v-model="form.enabled" type="checkbox"><span>활성화</span></label></div>
      <label><span>초보자 설명</span><textarea v-model="form.beginnerExplanation" required maxlength="2000" rows="3"></textarea></label>
      <div class="runbook-command-editor"><label><span>원인 확인 명령</span><textarea v-model="form.verificationCommand" required maxlength="2000" rows="3"></textarea></label><label><span>기대 결과</span><textarea v-model="form.expectedResult" required maxlength="2000" rows="3"></textarea></label></div>
      <label><span>안전 조치 설명</span><textarea v-model="form.safeAction" maxlength="2000" rows="2"></textarea></label>
      <div class="runbook-command-editor"><label><span>조치 후 검증 명령</span><textarea v-model="form.validationCommand" required maxlength="2000" rows="3"></textarea></label><label><span>Rollback 기준</span><textarea v-model="form.rollbackGuidance" maxlength="2000" rows="3"></textarea></label></div>
      <label><span>변경 메모</span><input v-model="form.changeNote" maxlength="1000" :required="Boolean(editingId)" placeholder="왜 변경했는지 기록하세요"></label>
      <div class="modal-actions"><button class="secondary-button" type="button" @click="editorOpen = false">취소</button><button class="primary-button" type="submit" :disabled="saving"><i class="pi pi-save"></i><span>{{ saving ? '저장 중' : '버전 저장' }}</span></button></div>
    </form></section></div>

    <div v-if="versionsOpen" class="modal-backdrop" @click.self="versionsOpen = false"><section class="modal-card runbook-version-modal" role="dialog" aria-modal="true"><header><div><span class="page-eyebrow">IMMUTABLE HISTORY</span><h2>버전 이력</h2><p>{{ selectedRunbook?.title }}</p></div><button class="icon-button" type="button" title="닫기" aria-label="닫기" @click="versionsOpen = false"><i class="pi pi-times"></i></button></header><div class="runbook-version-list"><article v-for="version in versions" :key="version.id"><span>v{{ version.version }}</span><div><strong>{{ version.changeNote || '변경 메모 없음' }}</strong><small>{{ version.createdBy }} · {{ formatTimestamp(version.createdAt) }}</small></div><button class="secondary-button compact" type="button" @click="restore(version)"><i class="pi pi-replay"></i><span>복원</span></button></article></div></section></div>
  </section>
</template>
