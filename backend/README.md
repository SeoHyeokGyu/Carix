# Carix Backend

Java 21 + Spring Boot. 인증, 도메인 로직, REST API, 데이터 접근, Spring Batch 수집·집계.

상세 설계:
- DB 스키마·규칙: [`../docs/specs/technical-specification.md`](../docs/specs/technical-specification.md)
- API 규격: [`../docs/specs/api-specification.md`](../docs/specs/api-specification.md)
- 도메인 계산: [`../docs/specs/domain-logic.md`](../docs/specs/domain-logic.md)

아직 스캐폴드 전. 다음 단계에서 Spring Boot 프로젝트(Gradle, JPA, Flyway, Security, Batch)를 초기화한다.

## 로컬 개발과 DB

- 로컬엔 PostgreSQL·Docker를 띄우지 않는다. **OCI dev DB에 원격 연결**한다 (`SPRING_DATASOURCE_URL` → SSH 터널 또는 방화벽 허용 주소).
- 운영 DB와 dev DB는 분리한다. 로컬 개발은 dev 스키마만 사용한다.

## 테스트 주의

- 설계 테스트 전략은 Testcontainers를 쓰나, Testcontainers는 로컬 Docker를 요구한다.
- Docker가 OCI 전용이므로, 통합 테스트는 **CI(Docker 가능 러너) 또는 OCI dev DB 대상**으로 실행한다. 로컬에서는 단위 테스트 중심으로 돌린다.
