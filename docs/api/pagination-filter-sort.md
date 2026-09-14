# Pagination, Filter, Sort

목록 API는 기본적으로 pagination을 지원한다.

권장 query:

```text
page=0
size=20
sort=createdAt,desc
```

필요한 리소스는 `clusterId`, `namespace`, `status`, `createdAt` 기준 filter를 제공한다.

