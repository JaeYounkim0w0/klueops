<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  commandCookbookCategories,
  commandCookbookItems,
  commandCookbookProcedures,
  commandPlaceholders,
  filterCommandCookbook,
  type CommandCookbookItem
} from '@/utils/commandCookbook';

const props = defineProps<{ unavailableRequirements?: string[] }>();
const emit = defineEmits<{ select: [item: CommandCookbookItem] }>();
const { t } = useI18n();
const category = ref('all');
const query = ref('');
const expandedId = ref<string | null>(null);

const visibleItems = computed(() => filterCommandCookbook(commandCookbookItems, category.value, query.value,
  (item) => `${t(`console.cookbookItems.${item.id}`)} ${t(`console.cookbookCategoryDescription.${item.category}`)}`));

/** choose 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function choose(item: CommandCookbookItem) {
  if (isUnavailable(item)) return;
  emit('select', item);
}

/** isUnavailable 처리 조건의 충족 여부를 판단한다. */
function isUnavailable(item: CommandCookbookItem) {
  return item.requirements?.some((requirement) => props.unavailableRequirements?.includes(requirement)) ?? false;
}

/** procedureItems 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function procedureItems(commandIds: string[]) {
  return commandIds.map((id) => commandCookbookItems.find((item) => item.id === id)).filter(Boolean) as CommandCookbookItem[];
}
</script>

<template>
  <section class="cookbook-panel">
    <header>
      <strong>{{ t('console.cookbookTitle') }}</strong>
      <small>{{ t('console.cookbookDescription') }}</small>
    </header>
    <input v-model="query" class="cookbook-search" type="search" :placeholder="t('console.cookbookSearch')" />
    <details class="cookbook-procedures">
      <summary>{{ t('console.cookbookProcedures') }}</summary>
      <article v-for="procedure in commandCookbookProcedures" :key="procedure.id">
        <strong>{{ t(`console.cookbookProcedure.${procedure.id}`) }}</strong>
        <ol><li v-for="item in procedureItems(procedure.commandIds)" :key="item.id"><button type="button" :disabled="isUnavailable(item)" @click="choose(item)">{{ t(`console.cookbookItems.${item.id}`) }}</button></li></ol>
      </article>
    </details>
    <div class="cookbook-categories" role="tablist" :aria-label="t('console.cookbookCategories')">
      <button type="button" :class="{ active: category === 'all' }" @click="category = 'all'">{{ t('console.cookbookCategory.all') }}</button>
      <button v-for="item in commandCookbookCategories" :key="item" type="button" :class="{ active: category === item }" @click="category = item">
        {{ t(`console.cookbookCategory.${item}`) }}
      </button>
    </div>
    <div class="cookbook-list">
      <article v-for="item in visibleItems" :key="item.id" :class="{ expanded: expandedId === item.id }">
        <button class="cookbook-summary" type="button" @click="expandedId = expandedId === item.id ? null : item.id">
          <span><strong>{{ t(`console.cookbookItems.${item.id}`) }}</strong><small>{{ t(`console.cookbookCategory.${item.category}`) }}</small></span>
          <i :class="expandedId === item.id ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"></i>
        </button>
        <div v-if="expandedId === item.id" class="cookbook-detail">
          <p>{{ t(`console.cookbookCategoryDescription.${item.category}`) }}</p>
          <code>{{ item.command }}</code>
          <div class="cookbook-badges">
            <span :class="item.safety">{{ t(`console.cookbookSafety.${item.safety}`) }}</span>
            <span>{{ t(`console.cookbookScope.${item.scope}`) }}</span>
            <span v-for="requirement in item.requirements" :key="requirement" class="requirement">{{ t(`console.cookbookRequirement.${requirement}`) }}</span>
          </div>
          <p v-if="commandPlaceholders(item.command).length" class="cookbook-placeholder">
            {{ t('console.cookbookPlaceholder', { placeholders: commandPlaceholders(item.command).join(', ') }) }}
          </p>
          <p v-if="item.expectedResult" class="cookbook-expected"><strong>{{ t('console.cookbookExpected') }}</strong> {{ t(`console.cookbookExpectedResult.${item.expectedResult}`) }}</p>
          <p v-if="item.nextCommandId" class="cookbook-next"><strong>{{ t('console.cookbookNext') }}</strong> {{ t(`console.cookbookItems.${item.nextCommandId}`) }}</p>
          <p v-if="isUnavailable(item)" class="cookbook-unavailable">{{ t('console.cookbookUnavailable') }}</p>
          <button class="primary-button compact-button" type="button" :disabled="isUnavailable(item)" @click="choose(item)">
            <i class="pi pi-arrow-left"></i>{{ t('console.cookbookUse') }}
          </button>
        </div>
      </article>
      <div v-if="!visibleItems.length" class="utility-empty">{{ t('console.cookbookEmpty') }}</div>
    </div>
  </section>
</template>
