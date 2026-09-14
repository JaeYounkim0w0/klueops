# Portal Public URL And Client Access

## 목적

Portal 주소가 환경마다 달라질 때 Backend callback, Keycloak client, browser 접근 주소가 서로 어긋나지 않도록 하나의 공개 URL 계약으로 관리한다. 사용자 PC의 IP가 아니라 사용자가 브라우저 주소창에 입력하는 Portal origin을 등록한다.

## 공개 URL 계약

| 설정 | 의미 | 예시 |
|---|---|---|
| `AIOPS_PORTAL_PUBLIC_URL` | 사용자가 접근하는 Portal origin | `https://aiops.example.com` |
| `AIOPS_OIDC_PUBLIC_ISSUER` | 브라우저가 접근하는 OIDC issuer | `https://auth.example.com/realms/aiops` |
| `AIOPS_ADDITIONAL_PUBLIC_URLS` | 전환 기간 또는 개발용 추가 Portal origin의 쉼표 구분 목록 | `http://127.0.0.1:5173,https://aiops-dr.example.com` |
| `AIOPS_PUBLIC_HOST` | 로컬 NodePort 설치에서 Portal URL을 간단히 파생할 host/IP | `<workstation-lan-ip>` |

URL은 scheme, host, port까지 포함한 절대 주소를 사용하고 끝의 `/`는 생략한다. 운영 모드에서는 Portal과 issuer 모두 HTTPS여야 한다. redirect URI와 web origin에는 wildcard를 사용하지 않는다.

## 환경별 등록

### 로컬 또는 사설망 NodePort

```bash
export AIOPS_PUBLIC_HOST='<workstation-lan-ip>'
./scripts/init/all-in-one.sh --dry-run
./scripts/init/all-in-one.sh --skip-build --skip-tests
```

위 설정은 기본 Portal을 `http://<workstation-lan-ip>:30081`로 만들고 Managed Keycloak의 callback, web origin, post logout URL에 같은 origin을 등록한다.

각 클라이언트 PC에는 Keycloak hostname만 등록한다.

```text
<workstation-lan-ip> auth.aiops.local
```

클라이언트의 자체 IP는 Keycloak에 등록하지 않는다. 방화벽은 필요한 사설망에서 `30080`, `30081`만 허용하고 개발 Vite가 필요한 경우에만 `5173`을 추가한다.

### 여러 Portal 주소를 함께 운영

```bash
export AIOPS_PORTAL_PUBLIC_URL='https://aiops.example.com'
export AIOPS_OIDC_PUBLIC_ISSUER='https://auth.example.com/realms/aiops'
export AIOPS_ADDITIONAL_PUBLIC_URLS='https://aiops-old.example.com,https://aiops-dr.example.com'
export AIOPS_PRODUCTION_MODE=true
./scripts/init/all-in-one.sh --dry-run --values deploy/helm/aiops/values-production.yaml
./scripts/init/all-in-one.sh --skip-build --skip-tests --values deploy/helm/aiops/values-production.yaml
```

추가 URL은 주소 전환이나 DR 검증 기간에만 유지하고 사용이 끝나면 제거한다. URL 수를 불필요하게 늘리면 허용된 OAuth redirect 표면도 함께 커진다.

### 외부 Keycloak 또는 외부 OIDC

외부 인증 서버를 사용하는 경우 Portal 설치기가 client 설정을 변경하지 않는다. 인증 서버 관리자에게 다음 exact URL을 등록하도록 요청한다.

```text
Redirect URI:        {portal-origin}/login/oauth2/code/aiops
Web origin:          {portal-origin}
Post logout redirect:{portal-origin}/login
```

issuer discovery 문서의 `issuer` 값은 `AIOPS_OIDC_PUBLIC_ISSUER`와 정확히 일치해야 한다.

## 기존 설치의 URL 변경 절차

1. DNS가 새 Portal과 Keycloak endpoint를 올바른 주소로 해석하는지 확인한다.
2. TLS 인증서의 SAN에 새 DNS 이름이 포함됐는지 확인한다.
3. 위 환경 변수를 새 주소로 설정한다.
4. `all-in-one.sh --dry-run`으로 HTTP 운영 URL, 누락된 Secret, Helm render 오류를 먼저 차단한다.
5. 소스나 이미지 변경이 없다면 `--skip-build --skip-tests`로 URL 설정만 Helm upgrade한다.
6. Managed Keycloak bootstrap 완료와 Backend/Frontend rollout 완료를 확인한다.
7. 로그인, callback 복귀, 새로고침, 로그아웃, WebSocket Pod 터미널을 새 Portal URL에서 검증한다.
8. 전환 기간 종료 후 이전 URL을 `AIOPS_ADDITIONAL_PUBLIC_URLS`에서 제거한다.

## 검증

```bash
curl --fail "${AIOPS_PORTAL_PUBLIC_URL}/healthz"
curl --fail "${AIOPS_OIDC_PUBLIC_ISSUER}/.well-known/openid-configuration"
./scripts/acceptance/validate-production-transport.sh
```

로그인 후 browser 주소가 다른 host로 되돌아가거나 `redirect_uri` 오류가 발생하면 Portal origin, forwarded host, Keycloak exact redirect URI를 순서대로 비교한다. WebSocket 기능만 실패하면 `/ws/`의 `Upgrade`와 `Connection` 전달 여부를 확인한다.
