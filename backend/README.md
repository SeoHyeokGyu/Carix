# Carix Backend

Java 21 + Spring Boot. 인증, 도메인 로직, REST API, 데이터 접근, Spring Batch 수집·집계.

상세 설계:
- DB 스키마·규칙: [`../docs/specs/technical-specification.md`](../docs/specs/technical-specification.md)
- API 규격: [`../docs/specs/api-specification.md`](../docs/specs/api-specification.md)
- 도메인 계산: [`../docs/specs/domain-logic.md`](../docs/specs/domain-logic.md)

현재는 배포 파이프라인 검증용 최소 스캐폴드다. Spring Boot 4.1 + springdoc(OpenAPI)만 있고
`GET /api/health` 하나를 서빙한다. JPA·Flyway·Security·Batch는 다음 단계에서 추가한다.

컨텍스트 패스는 `/api`다. 엣지 Caddy가 `/api/*`를 경로 그대로 넘기므로, 헬스체크와
Swagger UI(`/api/swagger-ui`), OpenAPI 문서(`/api/v3/api-docs`)가 모두 그 아래로 들어온다.

## 빌드

```bash
./gradlew build       # 테스트 포함
./gradlew bootRun     # http://localhost:8080/api/health
```

Java 툴체인은 21로 고정돼 있다. 로컬에 JDK 21이 없으면 `settings.gradle`의 foojay resolver가
직접 내려받는다 (첫 빌드 한정). CI는 `setup-java`가 21을 제공하므로 다운로드가 없다.

Dockerfile은 컴파일하지 않는다. CI 러너가 Gradle 캐시로 만든 JAR을 layered로 풀어 담기만 한다.
그래서 `tasks.named('jar') { enabled = false }`로 plain jar를 끈다 — `build/libs/*.jar` 글롭이
두 개를 집으면 `COPY`가 실패한다.

## 로컬 개발과 DB

> 아직 DB를 쓰지 않는다. 아래는 JPA 도입 이후 적용된다.

- 로컬엔 PostgreSQL·Docker를 띄우지 않는다. **OCI dev DB에 원격 연결**한다 (`SPRING_DATASOURCE_URL` → SSH 터널 또는 방화벽 허용 주소).
- 운영 DB와 dev DB는 분리한다. 로컬 개발은 dev 스키마만 사용한다.

## 테스트 주의

- 설계 테스트 전략은 Testcontainers를 쓰나, Testcontainers는 로컬 Docker를 요구한다.
- Docker가 OCI 전용이므로, 통합 테스트는 **CI(Docker 가능 러너) 또는 OCI dev DB 대상**으로 실행한다. 로컬에서는 단위 테스트 중심으로 돌린다.
