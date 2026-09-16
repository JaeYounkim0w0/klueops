<script setup lang="ts">
defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();
</script>

<template>
  <div v-if="open" class="delivery-modal-backdrop" @click.self="emit('close')">
    <section class="delivery-modal" role="dialog" aria-modal="true" aria-labelledby="deployment-start-title">
      <header>
        <div>
          <span class="delivery-eyebrow">APPLICATION DEPLOYMENT</span>
          <h2 id="deployment-start-title">어떤 Chart로 시작할까요?</h2>
          <p>Cluster 변경은 Chart와 Values를 확정하고 Preview를 승인한 뒤에만 시작됩니다.</p>
        </div>
        <button class="icon-button" type="button" aria-label="닫기" @click="emit('close')"><i class="pi pi-times"></i></button>
      </header>
      <div class="deployment-entry-grid">
        <RouterLink to="/applications/library" class="deployment-entry recommended" @click="emit('close')">
          <span class="entry-icon"><i class="pi pi-folder-open"></i></span>
          <div><strong>Chart Library에서 선택</strong><small>검증하고 저장한 Tenant Chart로 시작합니다.</small></div>
          <span class="delivery-badge trusted">권장</span>
        </RouterLink>
        <RouterLink to="/applications/discover" class="deployment-entry" @click="emit('close')">
          <span class="entry-icon"><i class="pi pi-search"></i></span>
          <div><strong>새 Chart 검색</strong><small>Artifact Hub에서 찾고 Tenant Library로 가져옵니다.</small></div>
          <i class="pi pi-arrow-right"></i>
        </RouterLink>
        <RouterLink :to="{ path: '/applications/library', query: { upload: 'true' } }" class="deployment-entry" @click="emit('close')">
          <span class="entry-icon"><i class="pi pi-upload"></i></span>
          <div><strong>.tgz 직접 가져오기</strong><small>로컬 Helm package를 안전 검사 후 저장합니다.</small></div>
          <i class="pi pi-arrow-right"></i>
        </RouterLink>
      </div>
      <footer><button class="secondary-button" type="button" @click="emit('close')">취소</button></footer>
    </section>
  </div>
</template>
