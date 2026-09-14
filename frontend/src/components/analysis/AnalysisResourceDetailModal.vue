<script setup lang="ts">
import type { NamespaceDiagnosticsResponse } from '@/api/client';

type Resource = NamespaceDiagnosticsResponse['problemResources'][number];
type Event = NamespaceDiagnosticsResponse['warningEvents'][number];
type Evidence = NamespaceDiagnosticsResponse['evidenceSignals'][number];
type PodLog = NamespaceDiagnosticsResponse['podLogSources'][number];
type Reference = { kind: string; name: string; reason: string };

defineProps<{
  resource: Resource;
  events: Event[];
  evidence: Evidence[];
  podLogs: PodLog[];
  references: Reference[];
  logAvailability: string;
  formattedSummary: string;
  collectedAtLabel: (value?: string) => string;
}>();

const emit = defineEmits<{
  close: [];
  logs: [resource: Resource];
  podLogs: [log: PodLog];
}>();
</script>

<template>
  <div class="modal-backdrop analysis-resource-detail-modal" @click.self="emit('close')">
    <section class="modal-panel feedback-detail-modal" aria-modal="true" role="dialog" aria-label="리소스 상세 진단">
      <header class="modal-header">
        <div>
          <h2>리소스 상세 진단</h2>
          <p>{{ resource.resourceType }}/{{ resource.resourceName }}</p>
        </div>
        <button class="icon-button" title="닫기" type="button" @click="emit('close')"><i class="pi pi-times"></i></button>
      </header>
      <div class="feedback-detail-body">
        <section class="resource-detail-summary">
          <div><span class="label">Status</span><strong>{{ resource.status || '-' }}</strong></div>
          <div><span class="label">Log Availability</span><strong>{{ logAvailability }}</strong></div>
        </section>
        <section class="resource-detail-grid">
          <article class="analysis-detail-section">
            <h3>Event Timeline</h3>
            <ul v-if="events.length">
              <li v-for="event in events" :key="`${event.reason}-${event.eventTime}-${event.message}`">
                <strong>{{ event.reason || event.type || 'Event' }} · {{ event.count ?? 1 }}회</strong>
                <span>{{ event.message || '-' }}</span>
                <small>{{ collectedAtLabel(event.eventTime) }}</small>
              </li>
            </ul>
            <p v-else>이 리소스에 직접 연결된 Warning 이벤트가 없습니다.</p>
          </article>
          <article class="analysis-detail-section">
            <h3>Evidence</h3>
            <ul v-if="evidence.length">
              <li v-for="item in evidence" :key="`${item.source}-${item.message}`"><strong>{{ item.severity || 'INFO' }} · {{ item.source || '-' }}</strong><span>{{ item.message || '-' }}</span></li>
            </ul>
            <p v-else>이 리소스에 직접 매칭된 evidence가 없습니다.</p>
          </article>
          <article class="analysis-detail-section">
            <h3>Related Pod Logs</h3>
            <ul v-if="podLogs.length">
              <li v-for="log in podLogs" :key="`${log.podName}-${log.containerName}`">
                <div class="diagnostic-row-heading">
                  <strong>{{ log.podName }} · {{ log.containerName }}</strong>
                  <button class="icon-button table-icon-button" title="Pod 로그 조회" aria-label="Pod 로그 조회" type="button" @click="emit('podLogs', log)"><i class="pi pi-list"></i></button>
                </div>
                <span>{{ log.truncated ? '수집 로그가 일부 잘렸습니다.' : '최근 로그 소스가 있습니다.' }}</span>
              </li>
            </ul>
            <p v-else>{{ logAvailability }}</p>
          </article>
          <article class="analysis-detail-section">
            <h3>Config / Storage References</h3>
            <ul v-if="references.length">
              <li v-for="reference in references" :key="`${reference.kind}-${reference.name}-${reference.reason}`"><strong>{{ reference.kind }}/{{ reference.name }}</strong><span>{{ reference.reason }}</span><small>FailedMount, volumeMount, ConfigMap, Secret, PVC 상태를 우선 확인하세요.</small></li>
            </ul>
            <p v-else>ConfigMap, Secret, PVC, volumeMount 관련 직접 참조가 감지되지 않았습니다.</p>
          </article>
          <article class="analysis-detail-section"><h3>Summary</h3><pre class="resource-summary-json">{{ formattedSummary || '요약 정보가 없습니다.' }}</pre></article>
        </section>
      </div>
      <footer class="modal-actions">
        <button class="secondary-button" type="button" @click="emit('logs', resource)"><i class="pi pi-list"></i><span>관련 로그 조회</span></button>
        <button class="secondary-button" type="button" @click="emit('close')">닫기</button>
      </footer>
    </section>
  </div>
</template>
