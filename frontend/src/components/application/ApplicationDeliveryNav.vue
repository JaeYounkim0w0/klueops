<script setup lang="ts">
const items = [
  { to: '/applications', label: '배포된 Applications', icon: 'pi pi-box' },
  { to: '/applications/library', label: 'Chart Library', icon: 'pi pi-folder' },
  { to: '/applications/discover', label: 'Discover', icon: 'pi pi-search' },
  { to: '/applications/sources', label: 'Sources', icon: 'pi pi-database' },
];
</script>

<template>
  <nav class="delivery-tabs" aria-label="Application Delivery">
    <RouterLink v-for="item in items" :key="item.to" :to="item.to" class="delivery-tab">
      <i :class="item.icon"></i><span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>
