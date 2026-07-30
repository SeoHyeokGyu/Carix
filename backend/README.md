# Carix Backend

Java 21 + Spring Boot. Gradle 멀티모듈 — `config-server`, `eureka`, `core-api`.
`core-api` 가 인증, 도메인 로직, REST API, 데이터 접근, Spring Batch 수집·집계를 맡는다.

상세 설계:
- DB 스키마·규칙: [`../docs/specs/technical-specification.md`](../docs/specs/technical-specification.md)
- API 규격: [`../docs/specs/api-specification.md`](../docs/specs/api-specification.md)
- 도메인 계산: [`../docs/specs/domain-logic.md`](../docs/specs/domain-logic.md)
- Spring Cloud 구성: [`../docs/specs/spring-cloud-architecture.md`](../docs/specs/spring-cloud-architecture.md)

`core-api` 는 아직 배포 파이프라인 검증용 최소 스캐폴드다. Spring Boot 4.1 + springdoc(OpenAPI) +
Config·Eureka 클라이언트만 있고 도메인 API는 아직 없다. JPA·Flyway·Security·Batch는 다음
단계에서 추가한다.

**전환 예정:** 앞에 Spring Cloud Gateway 가 놓이고(계획 B), `core-api` 에서
`auth-service`·`batch-worker` 가 더 갈라진다(계획 C).

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 배포 워크플로 헬스체크 대상 |
| `POST /api/v1/demo/echo` | **임시.** Swagger UI에서 요청 스키마·검증·에러 포맷을 눈으로 확인하는 용도. 도메인 API 도입 시 `com.carix.demo` 패키지째 삭제 |
| `GET /api/v1/demo/thresholds` | **임시.** Config Server 연동 확인용. 값은 [`../config/spring/core-api.yml`](../config/spring/core-api.yml)에서 오며 `/actuator/refresh` 로 재배포 없이 갱신된다. 도메인 API 도입 시 삭제 |

공통 에러 응답(`com.carix.common.ErrorResponse`)은 위 API 규격 문서 §1.1 형식을 따른다.
Spring 기본값(ProblemDetail)과 형식이 달라 `GlobalExceptionHandler`에서 변환한다.

컨텍스트 패스는 `/api`다. 엣지 Caddy가 `/api/*`를 경로 그대로 넘기므로, 헬스체크와
Swagger UI(`/api/swagger-ui`), OpenAPI 문서(`/api/v3/api-docs`)가 모두 그 아래로 들어온다.

## 빌드

```bash
./gradlew build              # 전체 모듈, 테스트 포함
./gradlew :core-api:bootRun  # config-server 없이 단독 기동은 안 된다 — 아래 참조
```

`core-api` 를 단독 기동하려면 `config-server`(포트 8888)가 먼저 떠 있어야 한다.
`spring.cloud.config.fail-fast=true` 라 Config Server 없이는 시작하지 않는다.

```bash
# config-server 의 기본 검색 경로는 서버 마운트(/etc/carix-repo/config/spring)다.
# 로컬엔 없으므로 저장소의 config/spring 을 직접 가리켜야 한다 — 안 하면
# config-server 는 뜨지만 빈 환경을 내려주고, core-api 는 임계값 0.0/0.0 으로 조용히 뜬다.
CONFIG_SEARCH_LOCATIONS=file:$(pwd)/../config/spring ./gradlew :config-server:bootRun &

# config-server 가 완전히 뜬 뒤 core-api 를 띄운다 (순서가 바뀌면 core-api 가
# fail-fast 로 죽는다). curl -sf http://localhost:8888/actuator/health 로 확인 가능.
CONFIG_SERVER_URL=http://localhost:8888 EUREKA_CLIENT_ENABLED=false \
  ./gradlew :core-api:bootRun --args='--server.port=8080'
# http://localhost:8080/api/health
```

`EUREKA_CLIENT_ENABLED=false` 는 클라이언트를 아예 끈다 — `eureka` 를 안 띄워도 등록 시도나
에러 로그 없이 뜬다 (2026-07-30 로컬 실증).

Java 툴체인은 21로 고정돼 있다. 로컬에 JDK 21이 없으면 `settings.gradle`의 foojay resolver가
직접 내려받는다 (첫 빌드 한정). CI는 `setup-java`가 21을 제공하므로 다운로드가 없다.

Dockerfile은 컴파일하지 않는다. CI 러너가 Gradle 캐시로 만든 JAR을 layered로 풀어 담기만 한다.
그래서 `tasks.named('jar') { enabled = false }`로 plain jar를 끈다 — `build/libs/*.jar` 글롭이
두 개를 집으면 `COPY`가 실패한다.

## 로컬 개발과 DB

> 아직 DB를 쓰지 않는다. 아래는 JPA 도입 이후 적용된다.

- 로컬엔 PostgreSQL·Docker를 띄우지 않는다. **OCI의 `carix` DB에 원격 연결**한다.
  접근은 Headscale tailnet 이며 노트북에는 Tailscale 앱만 설치한다
  ([ADR 0008](../docs/adr/0008-db-access-via-tailscale.md)).
- 운영과 개발이 **같은 DB**다. 별도 dev DB는 두지 않는다 ([ADR 0007](../docs/adr/0007-single-database-for-prod-and-dev.md)).
- `application-local.yml` 로 `eureka.client.register-with-eureka: false` 를 반드시 켠다.
  안 켜면 서버 Eureka 에 등록돼 서버 Gateway 가 이 노트북으로 운영 트래픽을 보낸다.
  절차는 [`../docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md) §3·§5.

## 테스트 주의

- 설계 테스트 전략은 Testcontainers를 쓰나, Testcontainers는 로컬 Docker를 요구한다.
- Docker가 OCI 전용이므로, 통합 테스트는 **배포 워크플로(Docker 제공 러너) 또는 OCI dev DB 대상**으로 실행한다. 로컬에서는 단위 테스트 중심으로 돌린다.
- PR 단계 CI가 없다. `./gradlew build` 가 자동으로 도는 지점은 main 푸시뿐이므로, 머지 전 검증은 로컬에서 직접 한다.
