# Application Exposure 로컬 수용시험

기준일: 2026-09-15

이 문서는 Phase 2 Application Delivery의 두 외부 노출 경로가 화면에만 존재하는 옵션이 아니라 실제 Kubernetes 리소스와 HTTP 트래픽으로 동작하는지 검증한 결과를 기록한다. 결론은 Chart Values가 생성한 Ingress와 KlueOps가 Service에 연결한 companion HTTPRoute 모두 로컬 Kubernetes에서 배포, 상태 수집과 HTTP 접근에 성공했다.

## 검증 환경

| 항목 | 값 | 용도 |
| --- | --- | --- |
| Kubernetes | Docker Desktop v1.34.1 | KlueOps와 대상 Application 실행 |
| Helm | v4.0.0 | Chart 렌더 및 Release 실행 |
| ingress-nginx | Chart 4.15.1, Controller 1.15.1 | Chart-managed Ingress 트래픽 검증 |
| Envoy Gateway | v1.9.1 | Gateway API HTTPRoute 트래픽 검증 |
| 테스트 Chart | Bitnami nginx 25.1.11, Application 1.31.5 | 두 노출 방식의 동일 backend |

ingress-nginx와 Envoy Gateway는 수용시험용으로 별도 namespace에 설치했다. 두 Controller는 KlueOps의 필수 구성요소가 아니며, 사용자가 선택한 노출 방식에 맞는 Ingress Controller 또는 Gateway API Controller는 대상 Cluster 운영자가 준비한다.

## 보안 경계

테스트 대상 Cluster 등록에는 전용 ServiceAccount를 사용했다. Cluster 범위는 Namespace와 Node 조회만 허용하고, Application namespace에서는 built-in `admin` RoleBinding을 사용했다. Gateway와 HTTPRoute 권한은 테스트 namespace의 별도 Role로 제한했다. 임시 kubeconfig token은 2시간 유효시간으로 발급했으며 검증 후 로컬 임시 파일을 폐기한다.

## Chart managed Ingress

1. nginx Values Profile에서 `ingress.enabled=true`, `ingressClassName=nginx`, hostname `chart-nginx.klueops.test`를 지정했다.
2. 배포 Wizard의 `Chart에서 관리`를 선택했다.
3. Preview가 렌더된 top-level Ingress를 식별했고 exact confirmation 후 Helm install을 실행했다.
4. Application `chart-nginx-acceptance`가 `RUNNING`, Pod `1/1 Ready`, Workload `HEALTHY`로 수렴했다.
5. Runtime에서 `http://chart-nginx.klueops.test/` Ingress endpoint를 발견했다.
6. ingress-nginx Controller로 Host header 요청을 전달해 HTTP 200과 nginx 환영 페이지를 확인했다.

이 검증은 Chart가 Values를 통해 Ingress를 직접 소유하는 경우 KlueOps가 companion Route를 중복 생성하지 않고 렌더 검증과 런타임 발견을 수행함을 확인한다.

## KlueOps companion HTTPRoute

1. nginx Release `route-nginx-acceptance`의 Service port 80을 backend로 선택했다.
2. 배포 Wizard의 `KlueOps HTTPRoute`에서 hostname `route-nginx.klueops.test`, path `/`, parent Gateway `klueops-exposure-acceptance/klueops-acceptance`를 지정했다.
3. Preview와 exact confirmation 후 Helm install을 실행하고 companion `route-nginx-acceptance-klueops` HTTPRoute를 적용했다.
4. Application이 `RUNNING`, Pod `1/1 Ready`, Workload `HEALTHY`로 수렴했다.
5. HTTPRoute Controller 상태는 `Accepted=True`, `ResolvedRefs=True`였고 제품 Runtime endpoint는 `READY`로 판정했다.
6. Envoy Gateway로 Host header 요청을 전달해 `http://route-nginx.klueops.test/`에서 HTTP 200과 nginx 환영 페이지를 확인했다.

이 검증은 KlueOps가 Chart template을 수정하지 않고 기존 Service를 Gateway API에 연결하며, Gateway의 HTTP listener에 맞춰 `http` URL을 표시함을 확인한다.

## 합격 판정

| 검증 항목 | Chart managed Ingress | KlueOps HTTPRoute |
| --- | --- | --- |
| Preview | 통과, Ingress 포함 확인 | 통과, Service·Gateway 입력 확인 |
| Helm Application | RUNNING | RUNNING |
| Pod와 Workload | 1/1 Ready, HEALTHY | 1/1 Ready, HEALTHY |
| Route Controller | Ingress Controller가 route 처리 | Accepted=True, ResolvedRefs=True |
| 제품 Runtime URL | 발견됨 | 발견됨, READY |
| 실제 HTTP 접근 | 200, nginx 응답 | 200, nginx 응답 |

두 노출 경로는 로컬 수용 기준을 통과했다. DNS zone 등록, 공인 인증서 발급, cross-namespace backend와 복잡한 `allowedRoutes` 정책은 이번 로컬 시험 범위가 아니며 후속 고급 연동 항목으로 유지한다.

## 재현과 정리

테스트 전용 Namespace, ServiceAccount, RBAC, GatewayClass와 Gateway는 `scripts/acceptance/fixtures/application-exposure.yaml`에 정의한다. 먼저 ingress-nginx와 Envoy Gateway를 설치한 뒤 이 fixture를 적용하고, KlueOps에서 대상 Cluster를 등록해 두 Wizard 흐름을 실행한다.

검증 후에는 KlueOps Application에서 Uninstall preview와 exact confirmation으로 Release 및 companion resource를 정리한다. Controller를 다른 테스트가 사용하지 않는 경우에만 각 Helm Release를 제거하고, 마지막으로 fixture를 삭제한다. 공유 Cluster에서는 Namespace나 GatewayClass를 일괄 삭제하기 전에 실제 소유자와 사용 중인 Route를 반드시 확인한다.
