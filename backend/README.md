# Carix Backend

Java 25 + Spring Boot. Gradle 멀티모듈 — `config-server`, `eureka`, `core-api`, `auth-service`.
`auth-service` 가 초대·매직링크 로그인·세션을 맡는다(스키마 `auth`). `core-api` 가 도메인 로직,
REST API, 데이터 접근, Spring Batch 수집·집계를 맡는다(스키마 `core`, JPA·Flyway·Security·Batch는
아직 없음 — 다음 단계).

상세 설계:
- DB 스키마·규칙: [`../docs/specs/technical-specification.md`](../docs/specs/technical-specification.md)
- API 규격: [`../docs/specs/api-specification.md`](../docs/specs/api-specification.md)
- 도메인 계산: [`../docs/specs/domain-logic.md`](../docs/specs/domain-logic.md)
- Spring Cloud 구성: [`../docs/specs/spring-cloud-architecture.md`](../docs/specs/spring-cloud-architecture.md)

`core-api` 는 아직 배포 파이프라인 검증용 최소 스캐폴드다. Spring Boot 4.1 + springdoc(OpenAPI) +
Config·Eureka 클라이언트만 있고 도메인 API는 아직 없다. JPA·Flyway·Security·Batch는 다음
단계에서 추가한다.

**구현 상태:** `auth-service` 코드 구현(초대 확인, 매직링크 발급·검증, 세션 생성·조회·폐기,
rate limit)과 배포 배선(`Dockerfile`, `docker-compose.yml` 서비스 항목, Caddy 라우트, CI 빌드
잡) 모두 완료됐다. Spring Cloud Gateway도 도입돼 요청 경로에 편입됐다(설계는 위
spring-cloud-architecture.md). `core-api` 도메인 인프라(JPA·Flyway·Security·Batch)는 다음 단계.

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 배포 워크플로 헬스체크 대상 |
| `POST /api/v1/demo/echo` | **임시.** Swagger UI에서 요청 스키마·검증·에러 포맷을 눈으로 확인하는 용도. 도메인 API 도입 시 `com.carix.demo` 패키지째 삭제 |
| `GET /api/v1/demo/thresholds` | **임시.** Config Server 연동 확인용. 값은 [`../config/spring/core-api.yml`](../config/spring/core-api.yml)에서 오며 `/actuator/refresh` 로 재배포 없이 갱신된다. 도메인 API 도입 시 삭제 |

### auth-service

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 헬스체크 |
| `POST /api/v1/auth/magic-link` | 매직링크 발송 요청. 초대 여부와 무관하게 동일 응답(계정 열거 방지) |
| `POST /api/v1/auth/verify` | 매직링크 검증, 세션 쿠키 발급 |
| `POST /api/v1/auth/logout` | 현재 세션 폐기 |
| `POST /api/v1/auth/logout-all` | 사용자의 전체 세션 폐기 |

설계 상세: [`../docs/superpowers/specs/2026-08-01-auth-service-phase1-design.md`](../docs/superpowers/specs/2026-08-01-auth-service-phase1-design.md).

이메일 실제 발송은 아직 연동되지 않았다 — `LoggingMagicLinkNotifier`가 로그로만 남긴다.
운영 전환 전 실제 provider(SES 등)를 정하고 `MagicLinkNotifier` 구현을 교체해야 한다.

로컬 기동은 `core-api`와 동일한 순서다(config-server 먼저, 그다음 `EUREKA_CLIENT_ENABLED=false`
로 단독 기동 가능). `auth-service`는 Testcontainers 없이는 로컬에서 DB 없이 뜨지 않는다 —
core-api와 달리 이미 JPA·Flyway를 쓰기 때문이다. 로컬 검증은 OCI DB 원격 연결
(DEVELOPMENT.md §3) 또는 `./gradlew :auth-service:test`(Docker 필요)로 한다.

공통 에러 응답(`com.carix.common.ErrorResponse`)은 위 API 규격 문서 §1.1 형식을 따른다.
Spring 기본값(ProblemDetail)과 형식이 달라 `GlobalExceptionHandler`에서 변환한다.

컨텍스트 패스는 `/api`다. 엣지 Caddy가 `/api/*`를 경로 그대로 넘기므로, 헬스체크와
Swagger UI(`/api/swagger-ui`), OpenAPI 문서(`/api/v3/api-docs`)가 모두 그 아래로 들어온다.

### gateway

Spring Cloud Gateway(WebFlux/reactive). 요청 경로의 단일 진입점 — `carix_caddy` 가 `core-api`
직접 대신 이제 `gateway:8080` 으로 붙는다(`core-api` 는 `8082` 로 이동).

| 경로 | 대상 |
| --- | --- |
| `/api/v1/auth/**` | `auth-service` (Eureka `lb://`) |
| `/api/v1/**`, `/api/health`, `/api/swagger-ui/**`, `/api/v3/api-docs/**` | `core-api` (Eureka `lb://`) |
| 그 외 | `frontend-node` (정적 URI, Eureka 미등록) |

세션 검증 필터는 `core` 라우트에만 적용되고 `carix.gateway.session-validation.enabled`
(`config/spring/gateway.yml`) 로 켜고 끈다. **기본값 false** — auth-service 배포 배선이
아직 없어서다. 켜기 전 auth-service 가 Eureka 에 `UP` 으로 등록돼 있는지 확인한다.

설계 상세: [`../docs/superpowers/plans/2026-08-01-gateway-introduction.md`](../docs/superpowers/plans/2026-08-01-gateway-introduction.md).

## 빌드

```bash
./gradlew build              # 전체 모듈, 테스트 포함
./gradlew :core-api:bootRun  # config-server 없이 단독 기동은 안 된다 — 아래 참조
```

`core-api` 를 단독 기동하려면 `config-server`가 먼저 떠 있어야 한다.
`spring.cloud.config.fail-fast=true` 라 Config Server 없이는 시작하지 않는다.

**일반적인 로컬 개발은 서버 config-server(8890)를 원격으로 쓴다**(DEVELOPMENT.md §5) —
아래는 서버 접근이 아예 안 될 때 쓰는 완전 오프라인 폴백이다.

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

Java 툴체인은 25로 고정돼 있다. 로컬에 JDK 25가 없으면 `settings.gradle`의 foojay resolver가
직접 내려받는다 (첫 빌드 한정). CI는 `setup-java`가 25를 제공하므로 다운로드가 없다.

Dockerfile은 컴파일하지 않는다. CI 러너가 Gradle 캐시로 만든 JAR을 layered로 풀어 담기만 한다.
그래서 `tasks.named('jar') { enabled = false }`로 plain jar를 끈다 — `build/libs/*.jar` 글롭이
두 개를 집으면 `COPY`가 실패한다.

## 로컬 개발과 DB

> `core-api`는 아직 DB를 쓰지 않는다 — JPA 도입 이후 적용된다. `auth-service`는 이미 JPA·Flyway로
> `auth` 스키마를 쓰므로, 아래 원격 DB 연결 설정이 지금 바로 필요하다.

- 로컬엔 PostgreSQL·Docker를 띄우지 않는다. **OCI의 `carix` DB에 원격 연결**한다.
  접근은 TLS + OCI Security List IP 허용목록이다
  ([`../docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md) §3 참조).
- 운영과 개발이 **같은 DB**다. 별도 dev DB는 두지 않는다(감수한 위험은 같은 문서 §3).
- `application-local.yml` 로 `eureka.client.register-with-eureka: false` 를 반드시 켠다.
  안 켜면 서버 Eureka 에 등록돼 서버 Gateway 가 이 노트북으로 운영 트래픽을 보낸다.
  절차는 [`../docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md) §3·§5.

## 테스트 주의

- **CI가 테스트를 안 돌린다 (2026-08-02부터).** `.github/workflows/deploy.yml`의 5개 빌드 잡
  전부 `./gradlew :모듈:build -x test`다. 빌드·이미지 푸시·배포는 테스트 결과와 무관하게
  진행된다. 머지 전 검증은 **전적으로 로컬 책임**이다.
- 설계 테스트 전략은 Testcontainers를 쓰나, Testcontainers는 로컬 Docker를 요구한다.
  Docker가 OCI 전용이라 통합 테스트(`auth-service`)는 로컬에서 못 돌린다 — OCI DB 대상으로
  수동 실행하거나, CI에 다시 테스트를 걸 때까지는 검증 공백으로 남는다.
- PR 단계 CI도 없다. `./gradlew build`가 자동으로 도는 지점 자체가 없다(main 푸시도 이제
  `-x test`). 사실상 이 저장소엔 자동 테스트 게이트가 없다.
