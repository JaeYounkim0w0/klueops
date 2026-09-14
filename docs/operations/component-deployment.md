# Component Deployment

이 문서는 최초 설치가 완료된 Helm release에서 소스 변경을 반복 배포하는 방법을 정의한다. 새 환경은 `initial-installation.md`를 따른다.

## 변경 유형 선택

| 변경 내용 | 명령 | 변경되는 Deployment |
| --- | --- | --- |
| Java, Backend 설정, Flyway | `scripts/deploy/backend.sh` | Backend |
| Vue, CSS, nginx | `scripts/deploy/frontend.sh` | Frontend |
| Keycloak Dockerfile/version | `scripts/deploy/keycloak.sh` | Keycloak |
| 격리 kubectl 실행기 | `scripts/deploy/command-runner.sh` | Command Runner |
| 네 컴포넌트가 같은 release로 변경 | `scripts/deploy/all.sh` | Backend, Frontend, Keycloak, Command Runner |
| PostgreSQL endpoint/Secret/Helm template | 검증 후 `scripts/init/all-in-one.sh --skip-build` | Render diff에 따라 결정 |

PostgreSQL은 애플리케이션 source image가 없으므로 반복 이미지 배포 대상이 아니다.

## 공통 동작

각 배포 명령은 다음 순서로 실행된다.

1. Kubernetes context와 기존 Helm release 확인
2. namespace/release 단위 배포 lock 획득
3. 대상 컴포넌트 테스트
4. 고유 태그 이미지 빌드
5. `helm upgrade --reset-then-reuse-values`로 새 chart 기본값과 기존 운영 override를 병합하고 대상 image 값만 변경
6. `--wait --rollback-on-failure`로 Helm transaction 확인
7. 대상 rollout과 공개 endpoint 확인
8. lock 제거

테스트나 이미지 빌드가 실패하면 Helm을 실행하지 않는다. Secret 환경 변수 값과 Kubernetes Secret data는 출력하지 않는다.

## 배포 전 확인

```bash
./scripts/deploy/backend.sh --dry-run
./scripts/deploy/frontend.sh --dry-run
./scripts/deploy/keycloak.sh --dry-run
./scripts/deploy/command-runner.sh --dry-run
./scripts/deploy/all.sh --dry-run
```

dry-run은 생성할 태그와 변경할 Helm image path를 표시하지만 테스트, build, cluster mutation을 실행하지 않는다.

## 로컬 컴포넌트 배포

```bash
./scripts/deploy/backend.sh
./scripts/deploy/frontend.sh
./scripts/deploy/keycloak.sh
./scripts/deploy/command-runner.sh
```

태그를 생략하면 `dev-<UTC timestamp>-<source checksum>` 형식으로 생성한다. 따라서 같은 고정 태그를 다시 빌드하기 위한 강제 rollout이 필요하지 않고 Helm history에서 실행 image를 구분할 수 있다.

승인된 기존 이미지를 배포하려면 태그를 명시한다.

```bash
./scripts/deploy/backend.sh --skip-tests --skip-build --tag 0.1.1
```

`--skip-build`에는 `--tag`가 필수다. `latest` 태그는 허용하지 않는다.

## 전체 애플리케이션 배포

Backend, Frontend, Keycloak과 Command Runner 변경이 하나의 release 단위라면 Helm revision을 하나만 생성한다.

```bash
./scripts/deploy/all.sh
```

개별 컴포넌트 배포를 동시에 실행하지 않는다. 모든 배포 명령은 같은 namespace/release lock을 사용하므로 중복 실행은 즉시 거부된다.

## 운영 Registry 배포

배포 스크립트는 registry 인증이나 push를 자동화하지 않는다. 운영 CI에서 image를 build, scan, sign, push하고 승인된 tag 또는 digest를 전달하는 것이 원칙이다.

```bash
export AIOPS_BACKEND_IMAGE_REPOSITORY='registry.example.com/aiops/backend'
export AIOPS_LOCAL_KUBERNETES_CONTEXT='<production-context>'

docker build -t registry.example.com/aiops/backend:0.1.1 backend
docker push registry.example.com/aiops/backend:0.1.1

./scripts/deploy/backend.sh \
  --skip-tests \
  --skip-build \
  --tag 0.1.1 \
  --namespace aiops-system \
  --release aiops
```

운영에서는 CI에서 테스트가 통과했다는 증적이 있을 때만 `--skip-tests`를 사용한다. production image는 digest 고정과 signature 검증이 별도 release gate를 통과해야 한다.

## Backend와 Flyway

Backend 시작 시 Flyway가 migration을 적용한다. migration이 포함된 배포는 다음 조건을 충족해야 한다.

- 배포 전에 복구 가능한 PostgreSQL backup 생성
- expand/contract 방식의 backward-compatible schema 변경
- 이전 Backend와 새 schema가 함께 동작하는지 확인
- data migration 소요 시간과 table lock 영향 검토

application image rollback은 database schema를 되돌리지 않는다. down migration을 자동 실행하지 않는다.

## Keycloak 변경

Keycloak patch/version 변경은 일반적인 application source 배포와 구분한다. 배포 전 Keycloak DB backup, release note, provider migration과 Realm bootstrap 호환성을 검증한다. 배포 후 discovery, 관리자 로그인, Portal callback과 logout을 확인한다.

## Command Runner 변경

Command Runner는 Backend와 별도 이미지 및 Deployment다. `aiops-command-runner` Secret의 `token`은 32자 이상 난수여야 하며 Backend와 Runner에만 주입한다. 배포 후 Backend Pod에서 Runner capability API의 token 인증과 kubectl version을 확인한다. 원격 Runner가 실패할 때 Backend-local 실행으로 자동 우회하지 않는 것이 정상 동작이다.

## 실패와 Rollback

Helm apply 또는 readiness가 실패하면 `--rollback-on-failure`가 해당 transaction을 되돌린다. Helm이 성공했지만 별도 smoke test가 실패하면 원인을 확인하지 않고 자동 rollback하지 않는다. Flyway나 Keycloak schema가 이미 변경되었을 수 있기 때문이다.

```bash
helm history aiops -n aiops-system
helm get values aiops -n aiops-system
kubectl -n aiops-system describe deployment aiops-backend
kubectl -n aiops-system get events --sort-by=.lastTimestamp
```

DB 호환성을 확인한 뒤에만 이전 revision을 명시해 rollback한다.

```bash
helm rollback aiops <revision> -n aiops-system --wait --timeout 15m
```

## 배포 후 검증

```bash
kubectl -n aiops-system get deployment,pod
curl --fail http://127.0.0.1:30081/healthz
curl --fail http://127.0.0.1:30081/api/auth/me
curl --fail http://auth.aiops.local:30080/realms/aiops/.well-known/openid-configuration
AIOPS_RUNTIME_MODE=kubernetes ./scripts/validate-runtime-convergence.sh
```

로그인, 대상 기능, Job Dock, cluster 접근과 AI Analysis의 최소 smoke test를 수행하고 release revision과 image tag를 변경 기록에 남긴다.
