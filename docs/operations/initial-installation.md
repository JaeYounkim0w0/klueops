# Initial Installation

이 문서는 새 Kubernetes 환경에 AIOps 패키지를 처음 설치할 때 사용한다. 반복적인 소스 배포는 `component-deployment.md`를 따른다.

## 설치 범위

All-in-one 설치에는 Spring Boot Backend, Vue/nginx Frontend, Managed Keycloak, 격리 Command Runner와 Keycloak DB/Realm bootstrap Job이 포함된다. PostgreSQL server와 Ollama server는 포함되지 않으며 접근 가능한 외부 endpoint가 필요하다.

```text
scripts/init/
  postgresql.sh   외부 DB 연결과 bootstrap Secret 준비
  keycloak.sh     Keycloak Secret 검사와 최적화 이미지 준비
  backend.sh      Portal DB/암호화 Secret과 Backend 이미지 준비
  frontend.sh     nginx BFF proxy와 Frontend 이미지 준비
  command-runner.sh  내부 token Secret과 격리 Runner 이미지 준비
  all-in-one.sh   준비 단계를 연결하고 Helm을 한 번 설치
```

각 스크립트의 `--help`에서 지원 옵션을 확인할 수 있다. `--dry-run`은 Docker build, Kubernetes 변경, PostgreSQL 변경을 수행하지 않는다.

## 사전 조건

- Docker Desktop Kubernetes와 현재 context `docker-desktop`
- Docker, Helm 3, kubectl, curl, OpenSSL
- 외부 PostgreSQL 17
- host 또는 외부 endpoint에서 실행되는 Ollama
- 로컬 `/etc/hosts`의 `127.0.0.1 auth.aiops.local`

다른 Kubernetes context를 의도적으로 사용할 때만 예상 context를 명시한다.

```bash
export AIOPS_LOCAL_KUBERNETES_CONTEXT='<expected-context>'
```

## Secret 계약

다음 Secret은 값이 아닌 이름과 key만 문서화한다.

| Secret | 필수 key | 목적 |
| --- | --- | --- |
| `aiops-portal-db` | `username`, `password` | Portal runtime DB 계정 |
| `aiops-portal-master-key` | `master-key` | kubeconfig 등 credential 암호화 |
| `aiops-postgresql-bootstrap` | `username`, `password` | 최초 DB/Role 생성용 임시 고권한 계정 |
| `aiops-keycloak-db` | `username`, `password` | Keycloak 전용 runtime DB 계정 |
| `aiops-keycloak-admin` | `username`, `password` | Keycloak master 관리자 |
| `aiops-keycloak-client` | `client-secret` | Portal confidential OIDC client |
| `aiops-initial-platform-admin` | `username`, `password` | 최초 제품 platform administrator |
| `aiops-command-runner` | `token` | Backend와 Runner 사이의 내부 인증 |

`backend.sh`는 Portal DB Secret이 없으면 `AIOPS_PORTAL_DB_USERNAME`, `AIOPS_PORTAL_DB_PASSWORD`로 생성한다. master key가 없으면 32-byte 난수를 생성한다. `postgresql.sh`는 bootstrap Secret이 없으면 `AIOPS_POSTGRES_BOOTSTRAP_USERNAME`, `AIOPS_POSTGRES_BOOTSTRAP_PASSWORD`로 생성한다. 환경 변수 값은 로그에 출력하지 않는다.

Keycloak runtime/admin/client/초기 사용자 Secret은 설치 전에 운영자가 Secret manager 또는 `kubectl`로 생성해야 한다.

## 구성 확인

로컬 기본 설정은 `deploy/helm/aiops/values-local.yaml`이다.

- PostgreSQL: `host.docker.internal:5432` (Docker Desktop 로컬 프로필)
- Portal DB: `aiops`
- Ollama: `http://host.docker.internal:11434`
- Portal: `http://127.0.0.1:30081`
- Keycloak: `http://auth.aiops.local:30080`

환경과 다르면 values를 복사해 별도 파일에서 endpoint와 Secret 이름을 변경한다. 비밀번호와 token은 values에 넣지 않는다.

LAN acceptance는 workstation IP를 values에 고정하지 않고 다음 환경 변수로 지정한다.

```bash
export AIOPS_PUBLIC_HOST='<workstation-lan-ip>'
./scripts/init/all-in-one.sh --dry-run
./scripts/init/all-in-one.sh
```

`AIOPS_PUBLIC_HOST`는 Portal NodePort URL과 callback allowlist를 파생한다. DNS/TLS 기반 운영 설치는 `AIOPS_PORTAL_PUBLIC_URL`, `AIOPS_OIDC_PUBLIC_ISSUER`, `AIOPS_PRODUCTION_MODE=true`를 명시해야 하며 HTTP 값이면 설치 전에 중단한다.

환경별 Portal URL 등록, 기존 설치의 주소 변경, 원격 클라이언트 hosts와 방화벽 기준은 [Portal Public URL And Client Access](public-url-and-client-access.md)를 따른다.

## 구성요소별 준비

구성 요소를 개별 확인할 때 다음 명령을 사용한다.

```bash
./scripts/init/postgresql.sh --dry-run
./scripts/init/keycloak.sh --dry-run
./scripts/init/backend.sh --dry-run
./scripts/init/frontend.sh --dry-run
./scripts/init/command-runner.sh --dry-run
```

실행 모드에서는 PostgreSQL 연결 확인, Secret 검사, 테스트와 이미지 빌드를 수행한다. PostgreSQL probe Pod가 60초 안에 시작하지 못하면 초기화를 중단한다. 구성요소별 명령은 준비 단계이며 Kubernetes application resource 전체 설치는 `all-in-one.sh`가 담당한다.

## 전체 설치

먼저 변경 없는 dry-run을 수행한다.

```bash
./scripts/init/all-in-one.sh --dry-run
```

필요한 bootstrap 값을 현재 shell session에만 주입하고 설치한다.

```bash
export AIOPS_PORTAL_DB_USERNAME='<portal-db-user>'
export AIOPS_PORTAL_DB_PASSWORD='<portal-db-password>'
export AIOPS_POSTGRES_BOOTSTRAP_USERNAME='<postgres-bootstrap-user>'
export AIOPS_POSTGRES_BOOTSTRAP_PASSWORD='<postgres-bootstrap-password>'

./scripts/init/all-in-one.sh
```

이미지와 테스트가 이미 승인된 경우에만 재사용 옵션을 함께 지정한다.

```bash
./scripts/init/all-in-one.sh --skip-tests --skip-build
```

기존 `./scripts/install-local-all-in-one.sh`도 같은 동작을 제공하지만 새 자동화에서는 `scripts/init/all-in-one.sh`를 사용한다.

## 이미지 구조

### Backend

`backend/Dockerfile`은 Maven 3.9.11/Temurin JDK 17 build stage에서 dependency를 cache하고 jar를 만든다. runtime stage에는 Temurin 17 JRE와 jar만 포함하고 UID/GID `999:999`로 실행한다. JVM은 container memory의 최대 75%를 사용하며 readiness endpoint를 healthcheck로 호출한다.

### Frontend

`frontend/Dockerfile`은 Node 22 Alpine에서 lockfile 기반 `npm ci`와 Vue production build를 실행한다. runtime에는 Node와 source를 넣지 않고 unprivileged nginx 1.27, `dist`, nginx 설정만 포함한다. UID 101과 port 8080을 사용한다.

### Keycloak

`keycloak/Dockerfile`은 Keycloak 26.7.3 builder에서 PostgreSQL provider, health, metrics를 `kc.sh build`로 최적화한다. runtime은 동일 patch version과 최적화 결과만 사용하고 UID 1000으로 실행한다. Deployment는 `start --optimized`를 사용한다.

### Command Runner

`command-runner/Dockerfile`은 Maven/Temurin JDK 17에서 별도 Spring Boot jar를 빌드하고 Temurin 17 JRE runtime에 checksum 검증한 고정 `kubectl`을 설치한다. UID/GID `999:999`, read-only root filesystem과 제한된 `/tmp`로 실행한다. ClusterIP와 32자 이상 내부 token만 사용하며 Backend 장애 우회용 local runner로 자동 전환하지 않는다.

### PostgreSQL

제품 전용 PostgreSQL Dockerfile은 없다. bootstrap hook은 공식 `postgres:17` 이미지를 일시적으로 사용하지만 데이터베이스 서버는 외부 PostgreSQL이다. 애플리케이션 DB와 Keycloak DB는 같은 server를 사용할 수 있어도 database와 runtime role을 분리한다.

## 설치 완료 검증

```bash
kubectl -n aiops-system get deployment,pod,service
curl --fail http://127.0.0.1:30081/healthz
curl --fail http://127.0.0.1:30081/api/auth/me
curl --fail http://auth.aiops.local:30080/realms/aiops/.well-known/openid-configuration
AIOPS_RUNTIME_MODE=kubernetes ./scripts/validate-runtime-convergence.sh
```

Backend, Frontend, Managed Keycloak, Command Runner 네 Deployment가 모두 Ready이고 세 HTTP 검사와 Backend에서 Runner capability 호출이 성공해야 초기 설치를 완료로 판단한다.
