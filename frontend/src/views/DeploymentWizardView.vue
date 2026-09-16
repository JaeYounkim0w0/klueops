<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import ApplicationDeliveryNav from "@/components/application/ApplicationDeliveryNav.vue";
import HttpRouteTargetFields from "@/components/application/HttpRouteTargetFields.vue";
import {
  api,
  type ClusterResponse,
  type DeploymentPlanResponse,
  type GatewayOptionResponse,
  type KubernetesNamespaceResponse,
  type RenderedServiceOptionResponse,
} from "@/api/client";
import { useAuthStore } from "@/stores/auth";
import { useJobCenterStore } from "@/stores/jobCenter";
import { useTenancyStore } from "@/stores/tenancy";

const route = useRoute();
const router = useRouter();
const tenancy = useTenancyStore();
const jobs = useJobCenterStore();
const auth = useAuthStore();
const chartVersionId = computed(() => String(route.params.chartVersionId));
const chartName = computed(() => String(route.query.chart || "Helm Chart"));
const valuesRevisionId = computed(() =>
  typeof route.query.valuesRevisionId === "string"
    ? route.query.valuesRevisionId
    : undefined,
);
const upgradeApplicationId = computed(() =>
  typeof route.query.upgradeApplicationId === "string"
    ? route.query.upgradeApplicationId
    : "",
);
const clusters = ref<ClusterResponse[]>([]);
const namespaces = ref<KubernetesNamespaceResponse[]>([]);
const planning = ref(false);
const executing = ref(false);
const plan = ref<DeploymentPlanResponse | null>(null);
const confirmation = ref("");
const message = ref("");
const renderedServices = ref<RenderedServiceOptionResponse[]>([]);
const gateways = ref<GatewayOptionResponse[]>([]);
const targetOptionsLoading = ref(false);
const targetOptionsMessage = ref("");
const targetOptionsBlockingError = ref(false);
const gatewayDiscoveryStatus = ref<"AVAILABLE" | "EMPTY" | "UNAVAILABLE">(
  "EMPTY",
);
let targetOptionsSequence = 0;
let targetOptionsTimer: number | undefined;
type ExposureType =
  "NONE" | "CHART_MANAGED" | "HTTP_ROUTE" | "INGRESS" | "TCP_ROUTE";
const requestedExposure = [
  "NONE",
  "CHART_MANAGED",
  "HTTP_ROUTE",
  "INGRESS",
  "TCP_ROUTE",
].includes(String(route.query.exposureType))
  ? (String(route.query.exposureType) as ExposureType)
  : "NONE";
// 노출 리소스의 소유권을 명시해 Chart 업그레이드와 KlueOps companion 수명주기가 섞이지 않게 한다.
const target = reactive({
  clusterId: "",
  namespace: "default",
  createNamespace: false,
  releaseName: "",
  exposureType: requestedExposure,
  hostname: "",
  exposurePath: "/",
  backendServiceNamespace: "default",
  backendServiceName: "",
  backendServicePort: 80,
  gatewayName: "",
  gatewayNamespace: "default",
});

onMounted(async () => {
  await tenancy.load();
  clusters.value = await api.listClusters({
    tenantId: tenancy.currentTenantId,
    workspaceId: tenancy.currentWorkspaceId,
  });
  const requestedCluster =
    typeof route.query.clusterId === "string" ? route.query.clusterId : "";
  target.clusterId = clusters.value.some((item) => item.id === requestedCluster)
    ? requestedCluster
    : clusters.value[0]?.id || "";
  target.releaseName =
    chartName.value
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, "-")
      .replace(/^-|-$/g, "")
      .slice(0, 53) || "application";
  // Library/Upgrade에서 전달된 안전한 Target 초깃값을 유지해 같은 정보를 다시 입력하지 않게 한다.
  if (typeof route.query.namespace === "string")
    target.namespace = route.query.namespace;
  if (typeof route.query.releaseName === "string")
    target.releaseName = route.query.releaseName;
  target.backendServiceName = target.releaseName;
  target.backendServiceNamespace = target.namespace;
  if (upgradeApplicationId.value) {
    target.backendServiceName = target.releaseName;
    target.createNamespace = false;
  }
});

watch(
  () => target.clusterId,
  async (clusterId) => {
    namespaces.value = clusterId
      ? await api.listNamespaces(clusterId).catch(() => [])
      : [];
    if (
      !target.createNamespace &&
      !namespaces.value.some((item) => item.name === target.namespace)
    )
      target.namespace =
        namespaces.value.find((item) => item.name === "default")?.name ||
        namespaces.value[0]?.name ||
        "default";
  },
  { immediate: false },
);

watch(
  [
    () => target.exposureType,
    () => target.clusterId,
    () => target.namespace,
    () => target.releaseName,
  ],
  () => {
    window.clearTimeout(targetOptionsTimer);
    ++targetOptionsSequence;
    if (
      !target.clusterId ||
      !target.namespace ||
      !target.releaseName
    ) {
      targetOptionsLoading.value = false;
      return;
    }
    // Release name 입력 중 매 키마다 Helm 렌더링하지 않도록 짧게 모아서 조회한다.
    targetOptionsLoading.value = true;
    targetOptionsTimer = window.setTimeout(loadTargetOptions, 350);
  },
);

onBeforeUnmount(() => window.clearTimeout(targetOptionsTimer));

const exposureReady = computed(
  () =>
    !["HTTP_ROUTE", "INGRESS", "TCP_ROUTE"].includes(target.exposureType) ||
    (Boolean(target.backendServiceName) &&
      target.backendServicePort > 0 &&
      (target.exposureType === "TCP_ROUTE" ||
        selectedRenderedService.value?.httpRouteCompatibility !== "NON_HTTP") &&
      (target.exposureType === "TCP_ROUTE" || Boolean(target.hostname)) &&
      (target.exposureType === "INGRESS" ||
        (Boolean(target.gatewayName) && Boolean(target.gatewayNamespace)))),
);
const selectedRenderedService = computed(() =>
  renderedServices.value.find(
    (item) =>
      item.name === target.backendServiceName &&
      item.port === target.backendServicePort,
  ),
);

/** loadTargetOptions 처리 결과를 조회해 반환한다. */
async function loadTargetOptions(): Promise<void> {
  const sequence = ++targetOptionsSequence;
  targetOptionsLoading.value = true;
  targetOptionsMessage.value = "";
  targetOptionsBlockingError.value = false;
  try {
    const result = await api.getDeploymentTargetOptions({
      tenantId: tenancy.currentTenantId,
      clusterId: target.clusterId,
      chartVersionId: chartVersionId.value,
      valuesRevisionId: valuesRevisionId.value,
      namespace: target.namespace,
      releaseName: target.releaseName,
      exposureType: target.exposureType,
    });
    if (sequence !== targetOptionsSequence) return;
    renderedServices.value = result.services.filter(
      (item) => item.namespace === target.namespace,
    );
    gateways.value = result.gateways;
    gatewayDiscoveryStatus.value = result.gatewayDiscoveryStatus;
    const compatibleServices =
      ["NONE", "CHART_MANAGED", "TCP_ROUTE"].includes(target.exposureType)
        ? renderedServices.value
        : renderedServices.value.filter(
            (item) => item.httpRouteCompatibility !== "NON_HTTP",
          );
    const selectedService =
      compatibleServices.find(
        (item) =>
          item.name === target.backendServiceName &&
          item.port === target.backendServicePort,
      ) || compatibleServices[0];
    if (selectedService) selectService(selectedService);
    else {
      target.backendServiceName = "";
      target.backendServicePort = 0;
    }
    const selectedGateway =
      gateways.value.find(
        (item) =>
          item.name === target.gatewayName &&
          item.namespace === target.gatewayNamespace &&
          item.readiness === "READY",
      ) || gateways.value.find((item) => item.readiness === "READY");
    if (selectedGateway) selectGateway(selectedGateway);
    else {
      target.gatewayName = "";
      target.gatewayNamespace = "";
    }
    if (!renderedServices.value.length)
      targetOptionsMessage.value =
        "현재 Values로 렌더링되는 Service가 없습니다. Service 활성화 Values를 확인하세요.";
    else if (
      ["HTTP_ROUTE", "INGRESS"].includes(target.exposureType) &&
      !compatibleServices.length
    )
      targetOptionsMessage.value =
        "HTTPRoute로 연결할 HTTP Service가 없습니다. PostgreSQL·Redis 같은 TCP 서비스는 port-forward, NodePort, LoadBalancer 또는 TCPRoute를 사용하세요.";
    else if (
      ["HTTP_ROUTE", "TCP_ROUTE"].includes(target.exposureType) &&
      !selectedGateway
    )
      targetOptionsMessage.value =
        result.gatewayDiscoveryMessage || "준비된 Gateway가 없습니다.";
  } catch (error) {
    if (sequence !== targetOptionsSequence) return;
    renderedServices.value = [];
    gateways.value = [];
    gatewayDiscoveryStatus.value = "UNAVAILABLE";
    targetOptionsBlockingError.value = true;
    target.backendServiceName = "";
    target.backendServicePort = 0;
    target.gatewayName = "";
    target.gatewayNamespace = "";
    targetOptionsMessage.value =
      error instanceof Error
        ? error.message
        : "Service와 Gateway를 조회하지 못했습니다.";
  } finally {
    if (sequence === targetOptionsSequence) targetOptionsLoading.value = false;
  }
}

/** selectService 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function selectService(option: RenderedServiceOptionResponse): void {
  if (
    target.exposureType !== "TCP_ROUTE" &&
    option.httpRouteCompatibility === "NON_HTTP"
  )
    return;
  target.backendServiceNamespace = option.namespace;
  target.backendServiceName = option.name;
  target.backendServicePort = option.port;
}

/** selectGateway 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function selectGateway(option: GatewayOptionResponse): void {
  if (!option) return;
  target.gatewayNamespace = option.namespace;
  target.gatewayName = option.name;
}

/** native select의 coordinate 값을 실제 Service option으로 해석한다. */
function selectServiceValue(value: string): void {
  const option = renderedServices.value.find((item) => `${item.namespace}/${item.name}:${item.port}` === value);
  if (option) selectService(option);
}

/** native select의 coordinate 값을 실제 Gateway option으로 해석한다. */
function selectGatewayValue(value: string): void {
  const option = gateways.value.find((item) => `${item.namespace}/${item.name}` === value);
  if (option) selectGateway(option);
}

/** preview 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function preview(): Promise<void> {
  planning.value = true;
  message.value = "";
  try {
    plan.value = await api.createDeploymentPlan({
      tenantId: tenancy.currentTenantId,
      applicationId: upgradeApplicationId.value || undefined,
      clusterId: target.clusterId,
      chartVersionId: chartVersionId.value,
      valuesRevisionId: valuesRevisionId.value,
      namespace: target.namespace,
      releaseName: target.releaseName,
      createNamespace: target.createNamespace,
      exposureType: target.exposureType,
      hostname: target.hostname || undefined,
      exposurePath: target.exposurePath,
      backendServiceNamespace: target.backendServiceNamespace,
      backendServiceName: target.backendServiceName,
      backendServicePort: target.backendServicePort,
      gatewayName: target.gatewayName,
      gatewayNamespace: target.gatewayNamespace,
    });
    confirmation.value = "";
  } catch (error) {
    message.value =
      error instanceof Error ? error.message : "Preview를 생성하지 못했습니다.";
  } finally {
    planning.value = false;
  }
}

/** deploy 처리에 필요한 화면 또는 업무 로직을 수행한다. */
async function deploy(): Promise<void> {
  if (!plan.value || confirmation.value.trim() !== plan.value.confirmationText)
    return;
  executing.value = true;
  try {
    const accepted = await api.executeDeploymentPlan(
      plan.value.id,
      tenancy.currentTenantId,
      confirmation.value,
    );
    // 화면 이동 뒤에도 전역 Job Center가 실제 서버 상태를 계속 추적한다.
    void jobs
      .trackJob({
        jobId: accepted.jobId,
        title: "Helm Application 배포",
        detail: `${target.namespace}/${target.releaseName}`,
        type: "HELM_INSTALL",
      })
      .catch(() => undefined);
    await router.push({
      path: "/applications",
      query: { applicationId: accepted.applicationId },
    });
  } catch (error) {
    message.value =
      error instanceof Error ? error.message : "배포를 시작하지 못했습니다.";
  } finally {
    executing.value = false;
  }
}
</script>

<template>
  <section class="page delivery-page">
    <header class="delivery-hero compact">
      <div>
        <span class="delivery-eyebrow">{{
          upgradeApplicationId ? "UPGRADE WIZARD" : "DEPLOYMENT WIZARD"
        }}</span>
        <h1>{{ chartName }} {{ upgradeApplicationId ? "Upgrade" : "배포" }}</h1>
        <p>대상과 노출 방식을 선택한 뒤 렌더 결과를 검토합니다.</p>
      </div>
      <RouterLink to="/applications/library" class="secondary-button"
        >나가기</RouterLink
      >
    </header>
    <ApplicationDeliveryNav />
    <div class="wizard-steps">
      <span class="done">1 Chart</span><span class="done">2 Values</span
      ><span :class="{ active: !plan }">3 Target</span
      ><span :class="{ active: plan }">4 Preview</span>
    </div>
    <div v-if="message" class="delivery-notice error">{{ message }}</div>
    <div v-if="!plan" class="target-layout">
      <section class="delivery-panel">
        <header>
          <span class="delivery-eyebrow">TARGET</span>
          <h2>Cluster와 Namespace</h2>
        </header>
        <div class="delivery-form-grid">
          <label class="wide"
            >Cluster<select v-model="target.clusterId" required>
              <option disabled value="">Cluster 선택</option>
              <option
                v-for="cluster in clusters"
                :key="cluster.id"
                :value="cluster.id"
              >
                {{ cluster.name }} · {{ cluster.environment }}
              </option>
            </select></label
          ><label
            >Namespace<select
              v-if="!target.createNamespace"
              v-model="target.namespace"
              required
            >
              <option v-for="item in namespaces" :key="item.name">
                {{ item.name }}
              </option></select
            ><input
              v-else
              v-model="target.namespace"
              required
              placeholder="new-namespace" /></label
          ><label
            >Release name<input v-model="target.releaseName" required /></label
          ><label
            v-if="auth.hasCapability('namespace:create')"
            class="wide checkbox-field"
            ><input v-model="target.createNamespace" type="checkbox" /> 배포 시
            Namespace 생성 (Cluster Admin 이상)</label
          >
        </div>
      </section>
      <section class="delivery-panel">
        <header>
          <span class="delivery-eyebrow">EXPOSURE</span>
          <h2>접근 경로</h2>
        </header>
        <div class="exposure-options">
          <label :class="{ selected: target.exposureType === 'NONE' }"
            ><input
              v-model="target.exposureType"
              type="radio"
              value="NONE"
            /><strong>Cluster 내부</strong
            ><small>Chart 기본 Service만 사용</small></label
          ><label
            v-if="auth.hasCapability('application:exposure')"
            :class="{ selected: target.exposureType === 'CHART_MANAGED' }"
            ><input
              v-model="target.exposureType"
              type="radio"
              value="CHART_MANAGED"
            /><strong>Chart 관리형</strong
            ><small>Values가 만드는 Ingress/HTTPRoute 사용</small></label
          ><label
            v-if="auth.hasCapability('application:exposure')"
            :class="{ selected: target.exposureType === 'HTTP_ROUTE' }"
            ><input
              v-model="target.exposureType"
              type="radio"
              value="HTTP_ROUTE"
            /><strong>KlueOps HTTPRoute</strong
            ><small>기존 Service에 도메인 연결</small></label
          >
          <label
            v-if="auth.hasCapability('application:exposure')"
            :class="{ selected: target.exposureType === 'INGRESS' }"
            ><input v-model="target.exposureType" type="radio" value="INGRESS" /><strong>KlueOps Ingress</strong><small>HTTP Service에 companion Ingress 생성</small></label
          >
          <label
            v-if="auth.hasCapability('application:exposure')"
            :class="{ selected: target.exposureType === 'TCP_ROUTE' }"
            ><input v-model="target.exposureType" type="radio" value="TCP_ROUTE" /><strong>KlueOps TCPRoute</strong><small>PostgreSQL·Redis 같은 TCP Service 연결</small></label
          >
        </div>
        <div
          v-if="target.exposureType === 'CHART_MANAGED'"
          class="exposure-guidance"
        >
          <i class="pi pi-info-circle"></i>
          <p>
            <strong>Values에서 노출 설정을 먼저 활성화하세요.</strong
            ><span
              >Preview가 실제 Ingress 또는 HTTPRoute 생성 여부를 확인하며,
              없으면 배포를 차단합니다.</span
            >
          </p>
        </div>
        <div
          v-if="
            ['NONE', 'CHART_MANAGED'].includes(target.exposureType) &&
            renderedServices.length
          "
          class="exposure-guidance"
        >
          <i class="pi pi-server"></i>
          <p>
            <strong>현재 Values로 렌더링되는 Service</strong>
            <span
              v-for="service in renderedServices"
              :key="`${service.namespace}/${service.name}:${service.port}`"
            >
              {{ service.name }} · {{ service.type }} · {{ service.port }} →
              {{ service.targetPort || service.port
              }}<template v-if="service.nodePort">
                · 외부 NodePort {{ service.nodePort }}</template
              >
            </span>
          </p>
        </div>
        <div
          v-if="
            ['NONE', 'CHART_MANAGED'].includes(target.exposureType) &&
            targetOptionsMessage
          "
          class="delivery-notice"
          :class="{ error: targetOptionsBlockingError }"
          role="status"
        >
          {{ targetOptionsMessage }}
        </div>
        <HttpRouteTargetFields
          v-if="target.exposureType === 'HTTP_ROUTE'"
          :hostname="target.hostname"
          :path="target.exposurePath"
          :service-name="target.backendServiceName"
          :service-port="target.backendServicePort"
          :gateway-name="target.gatewayName"
          :gateway-namespace="target.gatewayNamespace"
          :services="renderedServices"
          :gateways="gateways"
          :discovery-status="gatewayDiscoveryStatus"
          :loading="targetOptionsLoading"
          :message="targetOptionsMessage"
          @update:hostname="target.hostname = $event"
          @update:path="target.exposurePath = $event"
          @select-service="selectService"
          @select-gateway="selectGateway"
          @refresh="loadTargetOptions"
        />
        <div v-if="target.exposureType === 'INGRESS' || target.exposureType === 'TCP_ROUTE'" class="delivery-form-grid exposure-companion-fields">
          <label v-if="target.exposureType === 'INGRESS'" class="wide">Hostname<input v-model="target.hostname" placeholder="app.example.com" /></label>
          <label v-if="target.exposureType === 'INGRESS'">Path<input v-model="target.exposurePath" /></label>
          <label>Backend namespace<input v-model="target.backendServiceNamespace" /></label>
          <label class="wide">Backend Service / Port<select :value="`${target.backendServiceNamespace}/${target.backendServiceName}:${target.backendServicePort}`" @change="selectServiceValue(($event.target as HTMLSelectElement).value)"><option value="">Service 선택</option><option v-for="service in renderedServices" :key="`${service.name}:${service.port}`" :value="`${service.namespace}/${service.name}:${service.port}`">{{ service.namespace }}/{{ service.name }} : {{ service.port }} · {{ service.protocol }}</option></select></label>
          <label v-if="target.exposureType === 'TCP_ROUTE'" class="wide">TCP Gateway<select :value="`${target.gatewayNamespace}/${target.gatewayName}`" @change="selectGatewayValue(($event.target as HTMLSelectElement).value)"><option value="">Gateway 선택</option><option v-for="gateway in gateways" :key="`${gateway.namespace}/${gateway.name}`" :value="`${gateway.namespace}/${gateway.name}`">{{ gateway.namespace }}/{{ gateway.name }} · {{ gateway.readiness }}</option></select></label>
          <div v-if="targetOptionsMessage" class="delivery-notice" role="status">{{ targetOptionsMessage }}</div>
          <div class="exposure-guidance wide"><i class="pi pi-shield"></i><p><strong>Cross-namespace는 ReferenceGrant가 필수입니다.</strong><span>KlueOps는 기존 권한을 자동 확대하지 않고 Preview에서 Service와 ReferenceGrant를 확인합니다.</span></p></div>
        </div>
      </section>
      <footer class="wizard-footer">
        <span>Preview는 Cluster를 변경하지 않습니다.</span
        ><button
          class="primary-button"
          type="button"
          :disabled="
            planning ||
            targetOptionsLoading ||
            targetOptionsBlockingError ||
            !target.clusterId ||
            !exposureReady
          "
          @click="preview"
        >
          {{ planning ? "Helm 렌더링 중…" : "Preview 생성" }}
          <i class="pi pi-arrow-right"></i>
        </button>
      </footer>
    </div>
    <div v-else class="preview-layout">
      <section class="delivery-panel preview-summary">
        <header>
          <div>
            <span class="delivery-eyebrow">IMMUTABLE PLAN</span>
            <h2>배포 전 최종 검토</h2>
          </div>
          <button class="text-button" type="button" @click="plan = null">
            대상 수정
          </button>
        </header>
        <dl>
          <div>
            <dt>Target</dt>
            <dd>{{ target.clusterId }} / {{ plan.namespace }}</dd>
          </div>
          <div>
            <dt>Release</dt>
            <dd>{{ plan.releaseName }}</dd>
          </div>
          <div>
            <dt>Manifest SHA-256</dt>
            <dd>
              <code>{{ plan.manifestSha256 }}</code>
            </dd>
          </div>
          <div>
            <dt>Plan 만료</dt>
            <dd>{{ new Date(plan.expiresAt).toLocaleString() }}</dd>
          </div>
        </dl>
        <div v-if="plan.warnings.length" class="risk-list">
          <strong
            ><i class="pi pi-exclamation-triangle"></i> 검토할 위험
            {{ plan.warnings.length }}건</strong
          >
          <ul>
            <li v-for="warning in plan.warnings" :key="warning">
              {{ warning }}
            </li>
          </ul>
        </div>
        <div v-else class="risk-clear">
          <i class="pi pi-check-circle"></i> 자동 검사에서 고위험 패턴이
          발견되지 않았습니다.
        </div>
      </section>
      <section class="delivery-panel manifest-panel">
        <header>
          <h2>Rendered Manifest</h2>
          <span class="delivery-badge">read-only</span>
        </header>
        <pre>{{ plan.renderedManifest }}</pre>
      </section>
      <section class="exact-confirm">
        <label
          >아래 문구를 정확히 입력하세요.<code>{{ plan.confirmationText }}</code
          ><input v-model="confirmation" autocomplete="off" /></label
        ><button
          class="danger-button"
          type="button"
          :disabled="executing || confirmation.trim() !== plan.confirmationText"
          @click="deploy"
        >
          {{ executing ? "배포 요청 중…" : "승인하고 배포 시작" }}
        </button>
      </section>
    </div>
  </section>
</template>
