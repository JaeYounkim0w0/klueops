<script setup lang="ts">
defineProps<{
  title: string;
  subtitle?: string;
  sections: Array<{ label: string; content: string }>;
}>();

const emit = defineEmits<{ close: [] }>();
</script>

<template>
  <div class="modal-backdrop analysis-text-detail-modal" @click.self="emit('close')">
    <section class="modal-panel feedback-detail-modal" aria-modal="true" role="dialog" aria-label="분석 상세">
      <header class="modal-header">
        <div>
          <h2>{{ title }}</h2>
          <p>{{ subtitle || '분석 상세 내용' }}</p>
        </div>
        <button class="icon-button" title="닫기" type="button" @click="emit('close')">
          <i class="pi pi-times"></i>
        </button>
      </header>
      <div class="feedback-detail-body">
        <section class="analysis-text-detail-sections">
          <article v-for="section in sections" :key="`${section.label}-${section.content.slice(0, 40)}`">
            <span class="label">{{ section.label }}</span>
            <pre>{{ section.content }}</pre>
          </article>
        </section>
      </div>
      <footer class="modal-actions">
        <button class="secondary-button" type="button" @click="emit('close')">닫기</button>
      </footer>
    </section>
  </div>
</template>
