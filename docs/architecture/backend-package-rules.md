# Backend Package Rules

Backend와 Command Runner의 Maven groupId는 `io.product`이며, 소스와 테스트의 디렉터리도 package 선언과 일치시킨다. Backend 시작 클래스는 `ProductAiopsApplication`이다.

기본 package:

```text
io.product.aiops
  domain
  application
    port
      in
      out
    service
  adapter
    in
      web
    out
      ai
      crypto
      helm
      kubernetes
      persistence
  config
```

패키지 이름을 변경한 checkout에서는 이전 클래스가 빌드 산출물에 남지 않도록 Maven `clean` 후 빌드한다.

Kubernetes 메타데이터 접두사는 `aiops.product.io`를 사용한다. 기존 클러스터의 라벨·어노테이션은 소스 변경만으로 갱신되지 않는다. 이전 접두사로 생성한 acceptance fixture가 있다면 기존 버전의 정리 절차로 제거한 뒤 새 버전의 fixture를 실행한다.
