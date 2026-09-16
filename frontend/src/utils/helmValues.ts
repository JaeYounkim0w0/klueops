export interface NodePortValuesReview {
  errors: string[];
  warnings: string[];
}

const NODE_PORT_MIN = 30_000;
const NODE_PORT_MAX = 32_767;

/** Custom Values의 일반적인 nodePort 설정 실수를 Helm 실행 전에 안내한다. */
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
  if (Object.prototype.hasOwnProperty.call(record, "nodePort")) {
    reviewNodePort(record.nodePort, `${path}.nodePort`, localType, result);
  }
  Object.entries(record).forEach(([key, child]) => {
    if (key !== "nodePort") walk(child, `${path}.${key}`, localType, result);
  });
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
