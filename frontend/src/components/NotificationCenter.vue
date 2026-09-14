<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { api, type OperationNotificationResponse } from '@/api/client';
import { formatTimestamp } from '@/utils/time';

const router = useRouter();
const { t } = useI18n();
const open = ref(false);
const unreadCount = ref(0);
const notifications = ref<OperationNotificationResponse[]>([]);
const loading = ref(false);
let timer: number | undefined;

async function refreshCount() {
  try { unreadCount.value = (await api.getUnreadNotificationCount()).count; } catch { unreadCount.value = 0; }
}

async function toggle() {
  open.value = !open.value;
  if (!open.value) return;
  loading.value = true;
  try { notifications.value = await api.listNotifications(false); } catch { notifications.value = []; }
  finally { loading.value = false; }
}

async function openNotification(item: OperationNotificationResponse) {
  if (!item.read) {
    await api.readNotification(item.id);
    item.read = true;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  }
  open.value = false;
  if (item.targetPath?.startsWith('/')) await router.push(item.targetPath);
}

async function readAll() {
  await api.readAllNotifications();
  notifications.value = notifications.value.map((item) => ({ ...item, read: true }));
  unreadCount.value = 0;
}

onMounted(() => {
  void refreshCount();
  timer = window.setInterval(refreshCount, 30000);
});
onBeforeUnmount(() => { if (timer) window.clearInterval(timer); });
</script>

<template>
  <div class="notification-center">
    <button class="notification-trigger" type="button" :aria-expanded="open" :aria-label="t('notifications.title')" :title="t('notifications.title')" @click="toggle"><i class="pi pi-bell"></i><span>{{ t('notifications.title') }}</span><em v-if="unreadCount">{{ unreadCount > 99 ? '99+' : unreadCount }}</em></button>
    <div v-if="open" class="notification-popover">
      <header><div><strong class="operator-content-title">{{ t('notifications.title') }}</strong><small>{{ t('notifications.description') }}</small></div><button v-if="unreadCount" type="button" @click="readAll">{{ t('notifications.markAllRead') }}</button></header>
      <div v-if="loading" class="notification-empty"><i class="pi pi-spin pi-spinner"></i><span>{{ t('notifications.loading') }}</span></div>
      <button v-for="item in notifications.slice(0, 20)" v-else :key="item.id" type="button" class="notification-row" :class="{ unread: !item.read }" @click="openNotification(item)"><span class="notification-dot" :class="item.severity.toLowerCase()"></span><span><strong class="operator-content-title">{{ item.title }}</strong><small>{{ item.message }}</small><time>{{ formatTimestamp(item.updatedAt) }}<template v-if="item.occurrenceCount > 1"> · {{ t('notifications.occurrences', { count: item.occurrenceCount }) }}</template></time></span></button>
      <div v-if="!loading && !notifications.length" class="notification-empty"><i class="pi pi-check-circle"></i><span>{{ t('notifications.empty') }}</span></div>
    </div>
  </div>
</template>
