# Architecture Overview

```text
Operator Browser
  │  Portal/OIDC
  ▼
Frontend (Vue 3 + unprivileged nginx)
  │  same-origin REST/SSE/WebSocket proxy
  ▼
Backend (Java 17 + Spring Boot)
  ├─ Application Use Cases → Ports → Adapters
  ├─ Fabric8 Kubernetes Client: 조회, 로그, 대화형 Pod TTY
  ├─ Spring AI → Ollama: AI Analysis와 AI Chat
  ├─ JPA/Flyway → external PostgreSQL: 설정, 이력, 세션, 감사
  └─ token-authenticated NDJSON
       ▼
     Command Runner (Java 17 + kubectl)
       └─ 일반 kubectl 명령을 격리된 프로세스로 실행

Managed Keycloak ── OIDC ──► Backend BFF
      │
      └─ 전용 database/role ──► external PostgreSQL
```

Backend는 헥사고날 아키텍처를 따르며, Kubernetes/Fabric8, Helm CLI, AI provider SDK, JPA는 adapter 내부로 격리한다.

## 배포 토폴로지

기본 All-in-one Helm release는 같은 Kubernetes namespace에 다음 네 개의 독립 Deployment를 설치한다.

| Workload | 역할 | 외부 노출 |
| --- | --- | --- |
| Frontend | Vue 정적 자산, Backend BFF/WebSocket proxy | Portal Ingress 또는 로컬 NodePort |
| Backend | REST/SSE, OIDC session, 분석·운영 orchestration | Frontend를 통해서만 접근 |
| Managed Keycloak | 로그인, 사용자와 OIDC Realm | 인증 전용 Ingress 또는 로컬 NodePort |
| Command Runner | 일반 kubectl argv 실행과 NDJSON 출력 | ClusterIP, Backend에서만 접근 |

PostgreSQL과 Ollama는 Helm release가 설치하는 workload가 아니다. Docker Desktop 로컬 프로필은 두 서비스에 `host.docker.internal`로 접근한다. 다른 환경에서는 별도 values의 PostgreSQL/Ollama 주소와 Secret으로 교체한다.

네 개 Deployment가 반드시 네 대의 물리 서버를 뜻하지는 않는다. 현재 Docker Desktop에서는 한 Kubernetes node 위에 네 Pod가 실행되며, 운영 환경에서는 scheduler가 가용 node에 배치한다.

일반 kubectl 명령은 Command Runner를 사용하고 장애 시 Backend-local 실행으로 우회하지 않는다. 대화형 `kubectl exec/attach -it`는 현재 Backend Fabric8 WebSocket 경계를 사용하며 UI와 capability API에서 두 실행 경계를 구분한다.
