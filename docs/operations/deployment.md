# Deployment

현재 패키지는 개발/단일 host용 Docker Compose와 Kubernetes용 all-in-one Helm 구성을 함께 제공한다. Helm은 Backend, Frontend, 선택형 Managed Keycloak, 격리 Command Runner를 설치한다. PostgreSQL server는 패키지 외부에 이미 존재한다고 가정하며 Keycloak은 애플리케이션 DB와 분리된 database/role을 사용한다.

## 문서 안내

- 새 환경의 구성 요소별 준비와 전체 설치: `initial-installation.md`
- Backend, Frontend, Keycloak, Command Runner 소스 변경 후 반복 배포: `component-deployment.md`
- PostgreSQL과 Keycloak 백업/복구: `backup-restore.md`
- release 승인 기준: `release-candidate-checklist.md`

`scripts/init`은 최초 준비와 설치, `scripts/deploy`는 설치 이후 컴포넌트 이미지 배포에만 사용한다. 기존 `scripts/install-local-all-in-one.sh`는 호환 wrapper다.

## 구성

- PostgreSQL 17: durable metadata와 운영 이력
- Spring Boot backend: Java 17 non-root process
- Nginx frontend: Vue 정적 파일, SPA history fallback, backend reverse proxy
- Managed Keycloak: OIDC 로그인, 사용자와 Realm 관리
- Command Runner: Backend 전용 ClusterIP에서 일반 kubectl 명령을 격리 실행
- Ollama: host 또는 외부 endpoint를 `AIOPS_AI_BASE_URL`로 연결

## 시작

`.env.example`의 키 이름을 기준으로 local `.env`를 만들고 password와 master key를 secret storage에서 주입한다. 빈 값으로 시작하지 않는다.

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

기본 접속 주소는 frontend `http://127.0.0.1:8088`, backend `http://127.0.0.1:8080`이다. Compose PostgreSQL host port는 기존 로컬 5432와 충돌하지 않도록 15432를 사용한다.

### 로컬 OIDC 개발 서버

Kubernetes에 설치한 Managed Keycloak과 로컬 PostgreSQL을 사용하는 개발 backend는 다음과 같이 실행한다.

```bash
export AIOPS_DATASOURCE_PASSWORD='<portal-db-password>'
./scripts/run-local-oidc.sh
```

`run-local-oidc.sh`는 `aiops-keycloak-client/client-secret`, `aiops-portal-master-key/master-key`, `aiops-portal-db`의 `username/password`를 `aiops-system` namespace에서 읽어 프로세스 환경에만 주입한다. 값은 출력하거나 파일에 기록하지 않는다. 이미 `AIOPS_LOCAL_MASTER_KEY` 또는 datasource 환경변수를 명시했다면 그 값을 우선한다.

등록된 kubeconfig는 이 master key로 AES-256-GCM 암호화되어 있으므로 PostgreSQL을 유지한 채 키를 변경하거나 개발 기본 키로 기동하면 `Failed to decrypt secret`이 발생한다. 재기동·소스 배포 시에도 최초 등록에 사용한 `aiops-portal-master-key`를 유지하며, 키 유실 시 기존 credential을 임의 복구하려 하지 말고 해당 클러스터 credential을 다시 등록한다.

## Kubernetes All-In-One

운영 설치에는 최소 다음 Secret이 필요하다. Secret 값은 values 파일이나 Git에 기록하지 않는다.

```bash
kubectl -n aiops-system create secret generic aiops-portal-db \
  --from-literal=username='<portal-db-user>' \
  --from-literal=password='<portal-db-password>'
kubectl -n aiops-system create secret generic aiops-portal-master-key \
  --from-literal=master-key='<32-byte-or-longer-random-key>'
kubectl -n aiops-system create secret generic aiops-keycloak-client \
  --from-literal=client-secret='<oidc-confidential-client-secret>'
```

Managed Keycloak을 선택하면 Keycloak DB/admin/초기 platform admin Secret도 추가한다. 실제 이름은 `values-production.yaml`의 `runtimeExistingSecret`, `adminExistingSecret`, `clientExistingSecret`, `initialAdminExistingSecret`에 지정한다. 이 구성은 all-in-one 설치 편의를 위한 단일 인스턴스 인증 프로필이다. 인증 계층의 고가용성이 필요한 환경은 조직이 운영하는 Keycloak 또는 다른 OIDC 공급자를 `authentication.mode=external-oidc`로 연결한다.

```bash
helm template aiops deploy/helm/aiops \
  --namespace aiops-system \
  --values deploy/helm/aiops/values-production.yaml
helm upgrade --install aiops deploy/helm/aiops \
  --namespace aiops-system --create-namespace \
  --values deploy/helm/aiops/values-production.yaml \
  --wait --timeout 15m --rollback-on-failure
```

`global.productionMode=true`에서는 HTTP URL, Secure cookie 비활성화, ingress/TLS/Portal DB/master key 누락을 Helm render 단계에서 거부한다. Frontend는 단일 origin으로 Backend BFF를 프록시하므로 Portal ingress만 브라우저에 노출한다. Managed Keycloak은 별도 인증 host로 노출한다.

### 로컬 전체 패키지 설치

Docker Desktop Kubernetes의 로컬 acceptance 설치는 전용 설치기를 사용한다. 설치기는 현재 context가 기본값 `docker-desktop`과 일치하는지 확인하고, Backend/Frontend/Keycloak/Command Runner 이미지를 준비한 뒤 Helm upgrade, 네 Deployment rollout, Portal health, session API, OIDC discovery와 Runner 내부 capability를 순서대로 검증한다. 다른 context에 실수로 설치하지 않도록 불일치 시 즉시 중단한다. Service endpoint 전파가 Deployment readiness보다 늦을 수 있으므로 공개 endpoint는 요청별 timeout과 bounded retry로 확인한다.

```bash
export AIOPS_PORTAL_DB_USERNAME='<portal-db-user>'
export AIOPS_PORTAL_DB_PASSWORD='<portal-db-password>'
export AIOPS_POSTGRES_BOOTSTRAP_USERNAME='<postgres-bootstrap-user>'
export AIOPS_POSTGRES_BOOTSTRAP_PASSWORD='<postgres-bootstrap-password>'

./scripts/init/all-in-one.sh --dry-run
./scripts/init/all-in-one.sh
```

Secret 값은 명령 인자, values, 로그에 기록하지 않는다. 첫 설치에서 없는 `aiops-portal-master-key`는 설치기가 난수로 생성한다. 로컬 기본값은 Portal `http://127.0.0.1:30081`, Keycloak `http://auth.aiops.local:30080`이며 Vite 개발 주소 `http://127.0.0.1:5173`도 별도 OIDC callback으로 유지한다. 다른 LAN 주소에서는 `AIOPS_PUBLIC_HOST` 하나로 기본 Portal callback을 파생하거나 아래처럼 URL을 명시한다. 운영 환경은 Frontend NodePort와 HTTP URL을 Helm render 단계에서 거부하고 TLS Ingress만 허용한다.

```bash
# LAN acceptance
AIOPS_PUBLIC_HOST='<workstation-lan-ip>' ./scripts/init/all-in-one.sh

# Production: DNS와 인증서가 준비된 뒤 explicit URL만 사용
AIOPS_PRODUCTION_MODE=true \
AIOPS_PORTAL_PUBLIC_URL=https://aiops.example.com \
AIOPS_OIDC_PUBLIC_ISSUER=https://auth.aiops.example.com/realms/aiops \
./scripts/init/all-in-one.sh
```

설치와 후속 컴포넌트 배포는 같은 URL 파생 함수를 사용한다. URL 조합이 바뀌면 `./scripts/test-public-url-config.sh`와 `./scripts/validate-managed-keycloak.sh`를 먼저 실행한다. 상세 옵션과 컴포넌트 준비 순서는 `initial-installation.md`를 따른다.

Backend 컨테이너는 숫자 UID/GID `999:999`로 실행한다. 이는 Kubernetes `runAsNonRoot`가 이미지 사용자를 확실히 검증할 수 있게 하기 위한 배포 계약이다.

## Kubernetes Managed Keycloak Only

로컬 acceptance 값은 `deploy/helm/aiops/values-local.yaml`, 운영 보안 기준은 `values-production.yaml`에 있다. Managed Keycloak은 두 값 모두 단일 replica를 사용하며 HTTPS public URL, PostgreSQL TLS와 명시적 existing Secret 이름을 요구한다. 단일 replica는 Pod 재시작 후 복구와 데이터 영속성을 지원하지만 무중단 인증 HA를 보장하지 않는다.

```bash
./scripts/validate-managed-keycloak.sh
./scripts/install-managed-keycloak.sh --dry-run \
  --values deploy/helm/aiops/values-production.yaml
```

`install-managed-keycloak.sh`는 Portal을 배포하지 않고 인증 계층만 설치하는 bootstrap 도구다. 설치기는 DB pre-install hook, Keycloak rollout, Realm post-install hook, Backend OIDC ConfigMap과 discovery endpoint를 순서대로 검증한다. 전체 Portal은 위 Helm 명령으로 설치한다.

### 고가용성 인증 경계

- Bundled Managed Keycloak: 단일 replica, 설치·초기 Realm·사용자/그룹·OIDC 연결·재기동 후 영속성까지 제품 지원 범위다.
- External OIDC: 인증 HA, 확장, 다중 site와 별도 변경주기가 필요한 운영 환경의 권장 방식이다.
- Keycloak Operator, StatefulSet cluster, 분산 cache, 다중 cluster Keycloak은 현재 all-in-one 지원 범위가 아니다.
- 사용자가 `managedKeycloak.replicas`를 임의로 늘릴 수는 있지만 session/cache/failover를 검증한 공식 HA 구성으로 간주하지 않는다.

### 로컬 Keycloak 접근

`values-local.yaml`은 브라우저 접근용 `aiops-keycloak-public` NodePort Service를 `30080`으로 생성한다. Backend와 bootstrap은 기존 `aiops-keycloak` ClusterIP를 계속 사용하며 management port `9000`은 NodePort로 노출하지 않는다. 개발 Vite의 LAN origin은 `AIOPS_ADDITIONAL_PUBLIC_URLS`로 명시하며, 실제 LAN listen은 `AIOPS_FRONTEND_HOST=0.0.0.0 npm run dev`로 활성화한다.

로컬 PC의 `/etc/hosts`에 다음 항목을 한 번 등록한다. 관리자 권한이 필요하며 기존 항목이 있으면 중복으로 추가하지 않는다.

```text
127.0.0.1 auth.aiops.local
```

접근 주소는 관리자 콘솔 `http://auth.aiops.local:30080/admin/`, Realm `http://auth.aiops.local:30080/realms/aiops`이다. canonical issuer에도 `:30080`이 포함되어야 하며 포트 없는 URL과 혼용하지 않는다. NodePort는 로컬 acceptance 전용이고 운영에서는 HTTPS Ingress 또는 조직 표준 gateway를 사용한다.

## 검증

```bash
curl --fail http://127.0.0.1:30081/healthz
curl --fail http://127.0.0.1:30081/api/auth/me
curl --fail http://auth.aiops.local:30080/realms/aiops/.well-known/openid-configuration
AIOPS_RUNTIME_MODE=kubernetes ./scripts/validate-runtime-convergence.sh
./scripts/validate-local-runtime-contract.sh
```

`/actuator/**`는 Frontend reverse proxy에 노출하지 않는다. Kubernetes readiness/liveness probe와 `validate-runtime-convergence.sh`가 cluster 내부 상태를 확인한다.

## 종료와 데이터

`docker compose down`은 database volume을 보존한다. `docker compose down --volumes`는 PostgreSQL 데이터를 제거하므로 개발 초기화 외에는 사용하지 않는다. 제거 전에 `scripts/backup-postgres.sh`로 복구 가능한 dump를 생성한다.

## 현재 판정

`local,postgres`는 single-operator 개발 조합이다. all-in-one Helm과 OIDC/scope RBAC 코드는 완료됐지만 production 판정은 TLS ingress, 두 Tenant live gate, secret manager, 부하와 복구 검증 이후에만 부여한다. 상세 판정은 `../product/remaining-development-items.md`를 따른다.
