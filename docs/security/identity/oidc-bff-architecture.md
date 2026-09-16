# OIDC BFF Architecture

## 결정

Vue는 OAuth token을 직접 취급하지 않는다. Spring Security OAuth2 Client가 authorization code 교환, OIDC principal 검증, 세션 생명주기를 담당한다.

```text
Browser -- HttpOnly session --> Spring Boot BFF -- OIDC --> Identity Provider
                                  |
                                  +-- Spring Session JDBC --> PostgreSQL
                                  +-- account JIT provisioning --> aiops_users
```

## 보안 profile

- `local`: 개발 편의를 위한 명시적 무인증 profile이다. 운영 판정은 항상 `PILOT` 이하이다.
- `security-oidc`: OIDC login, 서버 세션, CSRF, default-deny API 보호를 활성화한다.
- 상용 실행은 `postgres,security-oidc` 조합만 허용한다.

## 설정 계약

| 환경 변수 | 의미 |
| --- | --- |
| `AIOPS_OIDC_ISSUER_URI` | OIDC issuer |
| `AIOPS_OIDC_CLIENT_ID` | confidential client ID |
| `AIOPS_OIDC_CLIENT_SECRET` | secret manager에서 주입하는 client secret |
| `AIOPS_OIDC_SCOPES` | 기본 `openid,profile,email` |
| `AIOPS_OIDC_GROUPS_CLAIM` | 기본 `groups` |
| `AIOPS_SESSION_TIMEOUT` | 기본 `30m` |
| `AIOPS_SESSION_ABSOLUTE_TIMEOUT` | 기본 `8h`, 자동 연장할 수 없는 최대 수명 |

refresh token을 요구하는 `offline_access` scope는 기본값에서 제외한다. 운영 reverse proxy는 HTTPS를 종료하고 `X-Forwarded-*` 헤더를 전달해야 한다.

## 세션 생명주기

- Spring Session idle timeout은 인증된 요청마다 sliding 방식으로 갱신한다.
- 화면이 보이고 최근 5분 안에 사용자 입력이 있었을 때만 idle 만료 10분 전부터 자동 연장할 수 있다.
- 유휴 상태에서는 자동 연장하지 않고 만료 5분 전에 남은 시간을 초 단위로 갱신하며 `세션 연장`, `로그아웃`을 제공한다. 연장 실패 사유는 팝업 안에 표시하고 absolute session timeout 이후의 시각은 반환하지 않는다.
- absolute timeout 이후에는 연장하지 않고 Keycloak 재인증을 요구한다.
- `POST /api/auth/session/extend`는 CSRF와 인증을 요구하며 갱신된 만료 정보를 반환한다.
- `POST /logout`은 application session과 CSRF cookie를 삭제한 뒤 OIDC RP-initiated logout으로 Keycloak SSO session까지 종료한다.

## 경로 소유권

- `/login`은 Vue SPA 화면이며 frontend history fallback으로 제공한다.
- `/oauth2/**`, `/login/oauth2/code/**`, `/logout`은 Spring Security 경로이며 backend로 proxy한다.
- reverse proxy가 `/login/**` 전체를 backend로 전달하면 로그인 화면을 직접 열거나 새로고침할 때 SPA가 깨지므로 금지한다.
- 개발 Vite proxy도 원본 Host와 protocol을 `X-Forwarded-*`로 전달해야 callback이 Backend 내부 주소로 생성되지 않는다.

## 오류 계약

- 401: `AUTHENTICATION_REQUIRED`
- 403: `ACCESS_DENIED`
- 비활성 계정: `ACCOUNT_DISABLED`
- 응답은 기존 Problem Detail의 requestId/correlationId 형식을 따른다.
