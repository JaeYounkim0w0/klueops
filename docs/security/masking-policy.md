# Masking Policy

다음 키 패턴은 민감 정보로 간주한다.

- password
- passwd
- secret
- token
- apiKey
- accessKey
- privateKey
- certificate
- credential

민감 정보는 로그, DB, AI prompt, Helm stdout/stderr에 평문으로 남기지 않는다.

