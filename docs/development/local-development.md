# Local Development

## Backend

```bash
cd backend
mvn test
mvn spring-boot:run
```

## Frontend

```bash
cd frontend
npm install
npm run dev
```

기본값은 개발 서버를 `127.0.0.1`에만 바인딩한다. 신뢰할 수 있는 로컬 네트워크에서 다른 장치 또는 PC의 LAN IP로 접근해야 할 때만 다음처럼 명시적으로 노출한다.

```bash
cd frontend
AIOPS_FRONTEND_HOST=0.0.0.0 npm run dev
```

Portal 주소는 `http://<workstation-lan-ip>:5173` 형식이다. OS 방화벽에서는 필요한 사설망 대역만 5173 포트에 접근하도록 제한한다. OIDC를 사용할 때는 아래 보안 문서에 따라 같은 origin을 Keycloak exact allowlist에도 추가해야 한다.

## Validation

```bash
./scripts/validate-backend.sh
./scripts/validate-frontend.sh
./scripts/validate-docs.sh
```
