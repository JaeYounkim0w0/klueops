# Backup and Restore

운영 PostgreSQL은 application metadata, 분석·Job·command·audit 이력과 암호화된 cluster credential을 보존한다. Kubernetes resource 원문은 실시간 조회가 원칙이며 저장된 snapshot은 운영 복구의 유일한 원본으로 취급하지 않는다.

## 환경 변수

- `AIOPS_DB_HOST`: 기본 `127.0.0.1`
- `AIOPS_DB_PORT`: 기본 `5432`
- `AIOPS_DB_NAME`: 기본 `aiops`
- `AIOPS_DATASOURCE_USERNAME`: 기본 `raguser`
- `AIOPS_DATASOURCE_PASSWORD`: 필수, shell history나 문서에 기록하지 않는다.
- `AIOPS_BACKUP_DIR`: 기본 `./backups`

## 백업

```bash
./scripts/backup-postgres.sh
```

custom-format dump를 권한 `0600`으로 생성한다. 백업 종료 코드는 반드시 0이어야 하고 생성 파일은 비어 있지 않아야 한다. 운영에서는 암호화된 object storage로 이동하고 일별 7개, 주별 4개, 월별 6개를 기본 보존 기준으로 삼는다.

## 복구

복구는 기존 객체를 교체할 수 있으므로 maintenance window에서 수행한다.

```bash
AIOPS_RESTORE_CONFIRMATION='RESTORE aiops' \
  ./scripts/restore-postgres.sh ./backups/aiops-YYYYMMDDTHHMMSSZ.dump
```

복구 후 backend를 재시작하고 readiness, cluster/analysis/Job/command/audit 이력, 신규 분석 쓰기를 확인한다.

## 복구 훈련

분기마다 별도 database에 최근 dump를 복구한다. 실제 운영 DB를 훈련 대상으로 사용하지 않는다. 소요 시간, dump 크기, Flyway version, 레코드 count, 검증자를 release checklist에 기록한다.

## Keycloak Database

Keycloak `keycloak` database는 애플리케이션 `aiops` database와 별도로 백업한다. Keycloak version upgrade 전에는 반드시 custom-format backup을 생성한다.

```bash
pg_dump --format=custom --file=keycloak-YYYYMMDDTHHMMSSZ.dump --dbname=keycloak
```

복구 훈련은 새 database에 수행하고 원본 `keycloak` database를 덮어쓰지 않는다. 복구 후 Keycloak을 격리 기동하여 `aiops` Realm, `aiops-bff` client, groups mapper, 네 개 표준 그룹, 사용자 수와 credential metadata 존재 여부를 확인한다. client secret, password hash, token은 출력하거나 검증 보고서에 기록하지 않는다.

복구 승인 후 OIDC discovery/JWKS, platform administrator 로그인, operator/viewer 범위 격리, logout session 폐기를 재검증한다.

## 자동 복원력 훈련

로컬 전용 훈련은 명시적 guard와 Kubernetes context 확인 후 실행한다.

```bash
AIOPS_RESILIENCE_ENABLED=true ./scripts/validate-operational-resilience.sh
```

이 스크립트는 원본 DB를 덮어쓰지 않는다. 앱 DB와 Keycloak DB custom dump는 운영 artifact가 아닌 OS 임시 파일로 만들고, 각각 임시 database에 복구하여 Flyway 성공 migration 수와 `aiops` Realm sentinel을 비교한 뒤 dump와 database를 종료 시 항상 제거한다. 이어 Backend/Keycloak rolling restart 후 Portal 회복, bounded read p95, Ollama timeout fallback을 검증한다.

2026-09-11 최신 결과는 30회 요청/동시성 10에서 p95 14ms, 앱 DB migration 28건과 Keycloak `aiops` Realm 1건의 격리 복구로 통과했다 (`artifacts/resilience/result.json`). 이 결과는 단일 로컬 노드 증적이며 고객 환경의 PostgreSQL/IdP HA failover와 합의된 RPO/RTO 검증을 대체하지 않는다.
