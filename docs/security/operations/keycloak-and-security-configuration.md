# Keycloak And Security Configuration

## 제공 모드

기본 패키지 모드는 `managed-keycloak`이다. Helm chart가 Keycloak workload와 Realm bootstrap을 제공하고 기존 PostgreSQL server에 전용 `keycloak` database와 최소 권한 `keycloak_app` role을 사용한다. 조직 표준 IdP가 있으면 `external-oidc`로 전환할 수 있으며 Backend application 코드는 변경하지 않는다.

Managed 모드는 public issuer/authorization URL과 Backend 내부 token/JWKS/user-info URL을 분리한다. public issuer 검증은 유지하며 내부 HTTP는 Kubernetes `.svc` 이름에만 허용한다.

## Kubernetes 설치

1. `aiops-keycloak-db`: `username`, `password`
2. `aiops-keycloak-admin`: `username`, `password`
3. `aiops-keycloak-client`: `client-secret`
4. `aiops-initial-platform-admin`: `username`, `password`
5. 자동 DB bootstrap이면 `aiops-postgresql-bootstrap`: `username`, `password`

위 Secret을 대상 namespace에 먼저 생성하고 값을 Helm 파일이나 shell history에 기록하지 않는다. 검증 후 설치한다.

```bash
./scripts/install-managed-keycloak.sh --dry-run \
  --values deploy/helm/aiops/values-local.yaml
./scripts/install-managed-keycloak.sh \
  --namespace aiops-system \
  --release aiops \
  --values deploy/helm/aiops/values-local.yaml
```

DBA가 database와 role을 미리 만든 운영 환경은 `postgresql.bootstrapMode=manual`을 사용한다. 자동 모드는 임시 bootstrap Secret을 hook에서만 읽으며 Keycloak runtime에는 전용 role만 전달한다.

### 로컬 NodePort

로컬 Kubernetes는 내부 `aiops-keycloak` ClusterIP와 브라우저 전용 `aiops-keycloak-public` NodePort를 분리한다. public Service는 HTTP `30080`만 노출하며 management `9000`은 노출하지 않는다. `/etc/hosts`에 `127.0.0.1 auth.aiops.local`을 등록한 뒤 `http://auth.aiops.local:30080/admin/`로 접근한다.

NodePort public URL은 Realm issuer, authorization endpoint와 redirect URI에 동일하게 사용한다. 운영 환경에서는 이 설정을 비활성화하고 HTTPS Ingress와 Secure cookie를 사용한다.

## Realm 계약

1. confidential client를 생성한다.
2. redirect URI에 `{frontend-origin}/login/oauth2/code/aiops`를 등록한다.
3. post logout redirect URI에 frontend origin을 등록한다.
4. group claim mapper를 추가해 `groups` claim을 ID token과 user info에 포함한다.
5. `aiops-platform-admins`, `aiops-cluster-admins`, `aiops-operators`, `aiops-viewers` group을 생성한다.
6. 초기 platform administrator는 master Realm 관리자가 아닌 `aiops` Realm 사용자이며 최초 비밀번호 변경이 필요하다.
7. client secret은 `.env`, 문서, Git에 기록하지 않고 Kubernetes Secret 또는 secret manager로 주입한다.

### 관리자와 제품 사용자 구분

- `aiops-kc-admin`은 Keycloak `master` Realm 관리자이며 Keycloak 관리자 콘솔에 로그인할 때 사용한다.
- `aiops-admin`은 제품용 `aiops` Realm 사용자이며 `aiops-platform-admins` 그룹에 속한다. Keycloak `master` Realm의 Users 목록에는 나타나지 않는다.
- 관리자 콘솔에서 제품 사용자를 확인하려면 좌측 상단 Realm selector를 `master`에서 `aiops`로 변경한 뒤 `Users`를 연다.
- `aiops-admin`은 AIOps 애플리케이션 로그인 계정이지 Keycloak master 관리자 계정이 아니다.

### 로컬 호환성 환경

`compose.identity.yaml`은 로그인/세션/그룹 claim을 검증하기 위한 분리된 개발용 Keycloak이다. `start-dev`를 사용하므로 운영에 배포하지 않는다.

```bash
export AIOPS_KEYCLOAK_ADMIN_PASSWORD='local-only-secret'
export AIOPS_OIDC_CLIENT_SECRET='local-only-client-secret'
./scripts/bootstrap-keycloak-dev.sh
```

개발과 Kubernetes는 같은 공통 bootstrap 스크립트를 사용한다. `aiops` Realm, confidential `aiops-bff` client, groups claim mapper, 네 개 표준 그룹과 초기 platform administrator를 멱등 생성한다.

로컬 애플리케이션 설정:

```bash
./scripts/run-local-oidc.sh
```

이 스크립트는 Kubernetes Secret에서 client secret을 읽어 프로세스 환경에만 주입하고 파일이나 로그에 기록하지 않는다. 기본 `security-oidc` profile도 공통 PostgreSQL datasource를 사용한다. 접속 정보는 `AIOPS_DATASOURCE_URL`, `AIOPS_DATASOURCE_USERNAME`, `AIOPS_DATASOURCE_PASSWORD`로 주입하며 H2 fallback은 없다. 로컬 HTTP에서는 Secure cookie를 끄지만 운영 HTTPS에서는 반드시 활성화한다.

기본 로컬 Portal 주소는 `http://127.0.0.1:5173`이며 `aiops-bff` client는 exact URI만 허용한다.

- callback: `http://127.0.0.1:5173/login/oauth2/code/aiops`
- web origin: `http://127.0.0.1:5173`
- post logout: `http://127.0.0.1:5173/login`

LAN IP로 개발 Portal을 노출할 때는 loopback origin을 제거하지 않고 추가 origin을 명시한다. wildcard redirect나 wildcard web origin은 사용하지 않는다.

```bash
export AIOPS_KEYCLOAK_ADMIN_PASSWORD='local-only-secret'
export AIOPS_OIDC_CLIENT_SECRET='local-only-client-secret'
export AIOPS_INITIAL_ADMIN_PASSWORD='temporary-initial-password'
export AIOPS_ADDITIONAL_PUBLIC_URLS='http://<workstation-lan-ip>:5173'
./scripts/bootstrap-keycloak-dev.sh
```

위 명령은 다음 값을 `aiops-bff` client에 추가한다.

- callback: `http://<workstation-lan-ip>:5173/login/oauth2/code/aiops`
- web origin: `http://<workstation-lan-ip>:5173`
- post logout: `http://<workstation-lan-ip>:5173/login`

Docker Desktop Kubernetes의 NodePort Portal을 다른 LAN PC에서 사용할 때는 다음 주소도 Keycloak client에 exact URL로 등록한다. `values-local.yaml`은 이를 기본 포함한다.

- Portal: `http://<workstation-lan-ip>:30081`
- callback: `http://<workstation-lan-ip>:30081/login/oauth2/code/aiops`
- web origin: `http://<workstation-lan-ip>:30081`
- post logout: `http://<workstation-lan-ip>:30081/login`
- 원격 PC hosts: `<workstation-lan-ip> auth.aiops.local`

원격 PC의 자체 IP는 callback에 등록하지 않는다. 여러 사용자가 같은 `http://<workstation-lan-ip>:30081` Portal로 접속한다면 Keycloak에는 이 Portal origin을 한 번만 exact URL로 등록한다. 각 원격 PC의 hosts에는 `<workstation-lan-ip> auth.aiops.local`을 추가하고 로컬 방화벽에서 해당 사설망이 NodePort `30080`, `30081`에 접근할 수 있게 한다. Portal host, port 또는 protocol이 바뀌는 경우에만 새 callback, web origin, post logout URL을 추가하고 bootstrap 또는 Helm upgrade를 다시 수행한다.

Vite는 `AIOPS_FRONTEND_HOST=0.0.0.0 npm run dev`로 실행한다. Spring Security의 callback은 `{baseUrl}`과 Vite의 forwarded host를 사용하므로 사용자가 접속한 exact origin으로 돌아온다.

Keycloak 이미지는 검증 시점의 보안 패치 릴리스 `26.7.3`으로 고정한다. 업그레이드는 release note와 migration guide 검토 후 별도 검증한다.

### 로컬 역할 검증

Managed Keycloak과 OIDC 모드 Backend/Frontend를 기동한 상태에서 다음 명령으로 실제 브라우저 권한 경계를 검증한다.

```bash
./scripts/validate-local-oidc-rbac.sh
```

검증 스크립트는 강한 임시 비밀번호를 가진 네 사용자를 Keycloak에 생성하고 각 표준 그룹을 할당한 뒤 Playwright로 다음 항목을 확인한다.

1. `PLATFORM_ADMIN`, `CLUSTER_ADMIN`, `OPERATOR`, `VIEWER`의 capability와 platform scope가 계약과 정확히 일치한다.
2. platform administrator만 사용자 관리 API에 접근할 수 있다.
3. viewer는 유효한 CSRF token을 사용해도 cluster 변경 API가 `403`으로 거부된다.
4. logout 후 기존 BFF session으로 보호 API에 접근할 수 없다.

비밀번호는 출력하거나 파일에 저장하지 않는다. 테스트 성공/실패와 관계없이 trap이 임시 사용자를 삭제한다. 이 검증은 역할 기반 권한 경계를 다루며, 실제 cluster/namespace별 object-scope 격리는 두 cluster를 사용하는 별도 수용 테스트로 검증한다.

최초 OIDC 로그인은 동일 사용자의 병렬 요청이 발생할 수 있다. Backend는 `V17__identity_provision_locks.sql`의 bucket lock을 사용해 JIT 사용자 생성 경쟁을 직렬화한다. 기존 사용자는 lock 없이 조회하는 fast path를 사용한다.

## 운영 필수값

- HTTPS와 Secure cookie
- 30분 이하 idle session, 조직 정책에 맞는 absolute session timeout
- 관리자 MFA 또는 passkey
- login brute-force 방어와 IdP audit
- PostgreSQL TLS/접근 제어/backup
- 두 개 이상의 bootstrap platform administrator
- 비상 접근 계정은 IdP에서 별도 보관하고 사용 시 감사

## 점검

- issuer discovery, JWKS 회전, clock skew를 검증한다.
- 프론트 source/map/network log에 token이 없는지 확인한다.
- logout 후 기존 session ID가 재사용되지 않는지 확인한다.
- 계정 비활성화 후 새 요청이 즉시 403인지 확인한다.
- cluster 범위 사용자의 `/api/clusters` 응답에 다른 cluster가 포함되지 않는지 확인한다.
- 자기 계정 비활성화와 존재하지 않는 사용자 역할 할당이 400으로 거부되는지 확인한다.
- `AIOPS_LIVE_IDENTITY_ENABLED=true ./scripts/validate-managed-keycloak-live.sh`로 실제 Pod DB 연결과 세 사용자 범위 격리를 승인한다.
