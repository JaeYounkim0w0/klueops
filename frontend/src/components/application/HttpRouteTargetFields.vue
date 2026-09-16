<script setup lang="ts">
import { computed } from 'vue';
import type { GatewayOptionResponse, RenderedServiceOptionResponse } from '@/api/client';

const props = defineProps<{
  hostname: string;
  path: string;
  serviceName: string;
  servicePort: number;
  gatewayName: string;
  gatewayNamespace: string;
  services: RenderedServiceOptionResponse[];
  gateways: GatewayOptionResponse[];
  discoveryStatus: 'AVAILABLE' | 'EMPTY' | 'UNAVAILABLE';
  loading: boolean;
  message: string;
}>();

const emit = defineEmits<{
  'update:hostname': [value: string];
  'update:path': [value: string];
  'select-service': [value: RenderedServiceOptionResponse];
  'select-gateway': [value: GatewayOptionResponse];
  refresh: [];
}>();

const selectedServiceIndex = computed(() => props.services.findIndex((item) =>
  item.name === props.serviceName && item.port === props.servicePort));
const selectedGatewayIndex = computed(() => props.gateways.findIndex((item) =>
  item.name === props.gatewayName && item.namespace === props.gatewayNamespace));
const selectedService = computed(() => props.services[selectedServiceIndex.value]);

// 화면에는 배열 순번만 노출하고 부모에는 검증된 전체 선택지를 전달해 필드 불일치를 막는다.
function chooseService(event: Event): void {
  const option = props.services[Number((event.target as HTMLSelectElement).value)];
  if (option && option.httpRouteCompatibility !== 'NON_HTTP') emit('select-service', option);
}

/** chooseGateway 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function chooseGateway(event: Event): void {
  const option = props.gateways[Number((event.target as HTMLSelectElement).value)];
  if (option) emit('select-gateway', option);
}

/** listenerSummary 처리 결과를 조회해 반환한다. */
function listenerSummary(gateway: GatewayOptionResponse): string {
  return gateway.listeners.map((item) => `${item.protocol}${item.port ? `:${item.port}` : ''}`).join(', ');
}
</script>

<template>
  <section class="http-route-target-fields">
    <header class="route-discovery-head">
      <div>
        <strong>클러스터 연결 정보</strong>
        <small>렌더링된 Service와 실제 Gateway API 리소스만 선택할 수 있습니다.</small>
      </div>
      <button class="text-button" type="button" :disabled="loading" @click="emit('refresh')">
        <i class="pi" :class="loading ? 'pi-spin pi-spinner' : 'pi-refresh'"></i>
        {{ loading ? '조회 중' : '다시 조회' }}
      </button>
    </header>

    <div v-if="message" class="route-discovery-message" role="status">{{ message }}</div>

    <div class="delivery-form-grid route-fields">
      <label class="wide">Hostname
        <input :value="hostname" required placeholder="nginx.cluster.example.com"
          @input="emit('update:hostname', ($event.target as HTMLInputElement).value)" />
      </label>
      <label>Path
        <input :value="path" required @input="emit('update:path', ($event.target as HTMLInputElement).value)" />
      </label>
      <label>Backend Service / Service Port
        <select :value="selectedServiceIndex" required :disabled="loading || !services.length" @change="chooseService">
          <option disabled :value="-1">{{ loading ? 'Chart 렌더링 중…' : 'Service와 Port 선택' }}</option>
          <option v-for="(service, index) in services" :key="`${service.namespace}/${service.name}:${service.port}`"
            :value="index" :disabled="service.httpRouteCompatibility === 'NON_HTTP'">
            {{ service.name }} : {{ service.port }} · {{ service.type }}{{ service.httpRouteCompatibility === 'NON_HTTP' ? ' · HTTPRoute 사용 불가' : '' }}
          </option>
        </select>
      </label>
      <label class="wide">Gateway (Namespace / Name)
        <select :value="selectedGatewayIndex" required :disabled="loading || !gateways.length" @change="chooseGateway">
          <option disabled :value="-1">{{ loading ? 'Gateway 조회 중…' : 'Gateway 선택' }}</option>
          <option v-for="(gateway, index) in gateways" :key="`${gateway.namespace}/${gateway.name}`"
            :value="index" :disabled="gateway.readiness !== 'READY'">
            {{ gateway.namespace }} / {{ gateway.name }} · {{ listenerSummary(gateway) }} · {{ gateway.readiness }}
          </option>
        </select>
      </label>
    </div>

    <div class="route-port-guide">
      <i class="pi pi-info-circle"></i>
      <p>
        <strong>HTTPRoute는 Service의 <code>spec.ports[].port</code>를 사용합니다.</strong>
        <span><code>targetPort</code>는 Pod 연결 포트이고 <code>nodePort</code>는 Node 외부 노출 포트이므로 HTTPRoute backend에 입력하지 않습니다.</span>
        <span>HTTPRoute는 HTTP/HTTPS 애플리케이션용입니다. PostgreSQL·Redis 같은 TCP 서비스는 port-forward, NodePort, LoadBalancer 또는 TCPRoute를 사용하세요.</span>
        <span v-if="selectedService">
          현재 선택: <code>{{ selectedService.name }}:{{ selectedService.port }}</code>
          <template v-if="selectedService.targetPort"> → targetPort {{ selectedService.targetPort }}</template>
          <template v-if="selectedService.nodePort"> · nodePort {{ selectedService.nodePort }}</template>
        </span>
        <span v-if="selectedService?.compatibilityMessage">{{ selectedService.compatibilityMessage }}</span>
      </p>
    </div>

    <div v-if="discoveryStatus === 'UNAVAILABLE'" class="route-prerequisite-guide">
      <i class="pi pi-exclamation-triangle"></i>
      <div>
        <strong>Service와 Gateway 선택 정보를 준비하지 못했습니다.</strong>
        <p>위 오류를 먼저 해결한 뒤 다시 조회하세요. Chart 렌더링 실패를 Gateway 미설치로 판단하지 않습니다.</p>
      </div>
    </div>

    <div v-else-if="!gateways.length" class="route-prerequisite-guide">
      <i class="pi pi-exclamation-triangle"></i>
      <div>
        <strong>선택할 수 있는 Gateway가 없습니다.</strong>
        <p>Cluster Admin이 Gateway API CRD와 Controller를 설치하고 HTTP/HTTPS Listener가 있는 Gateway를 생성해야 합니다.</p>
        <code>kubectl get gatewayclass</code>
        <code>kubectl get gateway -A</code>
        <small>다른 Namespace의 Gateway를 사용할 때는 Listener의 allowedRoutes가 Application Namespace를 허용해야 합니다.</small>
      </div>
    </div>
  </section>
</template>
