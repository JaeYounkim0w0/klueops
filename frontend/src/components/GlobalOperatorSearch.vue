<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { ApiError, api, type OperatorSearchResultResponse } from '@/api/client';

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();
const router = useRouter();
const { t } = useI18n();
const query = ref('');
const results = ref<OperatorSearchResultResponse[]>([]);
const loading = ref(false);
const error = ref('');
const input = ref<HTMLInputElement | null>(null);
const selectedType = ref('ALL');
const selectedIndex = ref(0);
let requestSequence = 0;
let debounceTimer: number | undefined;

const types = computed(() => ['ALL', ...new Set(results.value.map((item) => item.type))]);
const visibleResults = computed(() => selectedType.value === 'ALL'
  ? results.value
  : results.value.filter((item) => item.type === selectedType.value));

watch(() => props.open, async (open) => {
  if (!open) return;
  query.value = '';
  results.value = [];
  error.value = '';
  selectedType.value = 'ALL';
  await nextTick();
  input.value?.focus();
});

watch(query, () => {
  selectedIndex.value = 0;
  window.clearTimeout(debounceTimer);
  if (query.value.trim().length < 2) {
    results.value = [];
    loading.value = false;
    return;
  }
  debounceTimer = window.setTimeout(search, 250);
});
watch(selectedType, () => { selectedIndex.value = 0; });

/** search 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function search() {
  const sequence = ++requestSequence;
  loading.value = true;
  error.value = '';
  try {
    const response = await api.searchOperatorWorkspace(query.value.trim());
    if (sequence === requestSequence) results.value = response;
  } catch (cause) {
    if (sequence === requestSequence) error.value = cause instanceof ApiError ? cause.message : t('operatorSearch.failed');
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

/** openResult 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function openResult(result: OperatorSearchResultResponse) {
  emit('close');
  await router.push(result.targetPath);
}

/** moveSelection 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function moveSelection(delta: number) {
  if (!visibleResults.value.length) return;
  selectedIndex.value = (selectedIndex.value + delta + visibleResults.value.length) % visibleResults.value.length;
}

/** openSelected 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function openSelected() {
  const result = visibleResults.value[selectedIndex.value];
  if (result) void openResult(result);
}

/** icon 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function icon(type: string) {
  return ({ CLUSTER: 'pi-cloud', RESOURCE: 'pi-box', INCIDENT: 'pi-exclamation-circle', ANALYSIS: 'pi-chart-line', RUNBOOK: 'pi-book' } as Record<string, string>)[type] || 'pi-search';
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="operator-search-backdrop" @click.self="emit('close')" @keydown.esc="emit('close')">
      <section class="operator-search-dialog" role="dialog" aria-modal="true" :aria-label="t('operatorSearch.title')">
        <div class="operator-search-heading">
          <strong>{{ t('operatorSearch.title') }}</strong>
          <button class="operator-search-close" type="button" :aria-label="t('common.close')" :title="t('common.close')" @click="emit('close')">
            <i class="pi pi-times" aria-hidden="true"></i>
          </button>
        </div>
        <header class="operator-search-input-row">
          <i class="pi pi-search" aria-hidden="true"></i>
          <input ref="input" v-model="query" type="search" :placeholder="t('operatorSearch.placeholder')" @keydown.esc="emit('close')" @keydown.down.prevent="moveSelection(1)" @keydown.up.prevent="moveSelection(-1)" @keydown.enter.prevent="openSelected">
          <kbd>ESC</kbd>
        </header>
        <nav v-if="types.length > 1" class="operator-search-types" :aria-label="t('operatorSearch.filters')">
          <button v-for="type in types" :key="type" type="button" :class="{ active: selectedType === type }" @click="selectedType = type">{{ type === 'ALL' ? t('operatorSearch.all') : type }}</button>
        </nav>
        <div class="operator-search-results">
          <div v-if="loading" class="operator-search-state"><i class="pi pi-spin pi-spinner"></i><span>{{ t('common.loading') }}</span></div>
          <div v-else-if="error" class="operator-search-state error"><i class="pi pi-exclamation-triangle"></i><span>{{ error }}</span></div>
          <div v-else-if="query.trim().length < 2" class="operator-search-state"><i class="pi pi-compass"></i><span>{{ t('operatorSearch.hint') }}</span></div>
          <div v-else-if="!visibleResults.length" class="operator-search-state"><i class="pi pi-search"></i><span>{{ t('operatorSearch.empty') }}</span></div>
          <button v-for="(result, index) in visibleResults" v-else :key="`${result.type}:${result.id}`" class="operator-search-result" :class="{ selected: selectedIndex === index }" type="button" @mouseenter="selectedIndex = index" @click="openResult(result)">
            <span class="operator-search-icon"><i class="pi" :class="icon(result.type)"></i></span>
            <span class="operator-search-copy"><strong>{{ result.title }}</strong><span>{{ result.description || t('operatorSearch.noDescription') }}</span><small>{{ result.clusterName || result.type }}<template v-if="result.namespace"> / {{ result.namespace }}</template></small></span>
            <span class="status-pill low">{{ result.status || result.type }}</span>
            <i class="pi pi-arrow-right"></i>
          </button>
        </div>
        <footer><span><kbd>↑</kbd><kbd>↓</kbd> {{ t('operatorSearch.navigate') }}</span><span><kbd>Enter</kbd> {{ t('operatorSearch.open') }}</span><span><kbd>ESC</kbd> {{ t('common.close') }}</span></footer>
      </section>
    </div>
  </Teleport>
</template>
