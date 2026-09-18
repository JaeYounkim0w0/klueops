export interface NodePortValuesReview {
  errors: string[];
  warnings: string[];
}

const NODE_PORT_MIN = 30_000;
const NODE_PORT_MAX = 32_767;

/** Custom Values의 일반적인 Service 주소와 nodePort 설정 실수를 Helm 실행 전에 안내한다. */
export function reviewNodePortValues(values: unknown): NodePortValuesReview {
  const result: NodePortValuesReview = { errors: [], warnings: [] };
  walk(values, "$", undefined, result);
  return result;
}

/** 중첩된 Service 설정에서도 가장 가까운 type 값을 상속해 nodePort 계약을 확인한다. */
function walk(
  value: unknown,
  path: string,
  inheritedType: string | undefined,
  result: NodePortValuesReview,
): void {
  if (!value || typeof value !== "object" || Array.isArray(value)) return;
  const record = value as Record<string, unknown>;
  const localType =
    typeof record.type === "string" ? record.type.trim() : inheritedType;
  if (Object.prototype.hasOwnProperty.call(record, "clusterIP")) {
    reviewClusterIp(record.clusterIP, `${path}.clusterIP`, result);
  }
  if (Object.prototype.hasOwnProperty.call(record, "nodePort")) {
    reviewNodePort(record.nodePort, `${path}.nodePort`, localType, result);
  }
  Object.entries(record).forEach(([key, child]) => {
    if (key !== "nodePort") walk(child, `${path}.${key}`, localType, result);
  });
}

/** clusterIP가 Service type이나 port가 아니라 주소 표현인지 빠르게 확인한다. */
function reviewClusterIp(
  rawValue: unknown,
  path: string,
  result: NodePortValuesReview,
): void {
  if (rawValue == null || rawValue === "" || rawValue === "None") return;
  if (typeof rawValue !== "string" || !isIpAddress(rawValue)) {
    result.errors.push(
      `${path}에는 빈 값, None 또는 IPv4/IPv6 주소만 사용할 수 있습니다. NodePort는 type과 nodePort 필드에 설정하세요.`,
    );
  }
}

/** DNS 이름을 허용하지 않고 IPv4 또는 콜론 기반 IPv6 형태만 판별한다. */
function isIpAddress(value: string): boolean {
  if (value.includes(":")) {
    return /^[0-9a-f:.]+$/i.test(value) && value.split(":").length >= 3;
  }
  const parts = value.split(".");
  return (
    parts.length === 4 &&
    parts.every(
      (part) =>
        /^\d{1,3}$/.test(part) &&
        Number(part) <= 255 &&
        (part === "0" || !part.startsWith("0")),
    )
  );
}

/** nodePort 값과 함께 설정된 Service type의 일관성을 검사한다. */
function reviewNodePort(
  rawValue: unknown,
  path: string,
  serviceType: string | undefined,
  result: NodePortValuesReview,
): void {
  if (rawValue == null || rawValue === "" || rawValue === 0) return;
  if (typeof rawValue !== "number" || !Number.isInteger(rawValue)) {
    result.errors.push(`${path}는 정수여야 합니다.`);
    return;
  }
  if (rawValue < NODE_PORT_MIN || rawValue > NODE_PORT_MAX) {
    result.errors.push(
      `${path} ${rawValue}은 Kubernetes 기본 허용 범위 ${NODE_PORT_MIN}-${NODE_PORT_MAX} 밖입니다.`,
    );
  }
  if (!serviceType) {
    result.warnings.push(
      `${path}를 사용하려면 Chart의 Service type도 NodePort인지 확인하세요. type이 ClusterIP이면 값이 적용되지 않을 수 있습니다.`,
    );
  } else if (!["nodeport", "loadbalancer"].includes(serviceType.toLowerCase())) {
    result.warnings.push(
      `${path}가 있지만 가장 가까운 type은 ${serviceType}입니다. NodePort 외부 노출에는 type: NodePort가 필요합니다.`,
    );
  }
}
