# Carix Backend

Java 25 + Spring Boot. Gradle 멀티모듈 — `config-server`, `eureka`, `core-api`, `auth-service`,
`gateway`, `collector`.
`auth-service` 가 초대·매직링크 로그인·세션을 맡는다(스키마 `auth`). `core-api` 가 도메인 로직과
REST API를 맡는다 — 스킬 도메인부터 JPA로 `core` 스키마에 붙어 있다. 채용공고 도메인
(`com.carix.jobposting`)과 `core` 스키마는 `collector` 소관이며, 이미 JPA로 원격 DB에 연결돼
있다(`core` 스키마 DDL은 [`../deploy/postgres/schema/`](../deploy/postgres/schema/)에 있음). `core-api`의 Security는 아직 없음 — 다음 단계.

상세 설계:
- DB 스키마·규칙: [`../docs/specs/technical-specification.md`](../docs/specs/technical-specification.md)
- API 규격: [`../docs/specs/api-specification.md`](../docs/specs/api-specification.md)
- 도메인 계산: [`../docs/specs/domain-logic.md`](../docs/specs/domain-logic.md)
- Spring Cloud 구성: [`../docs/specs/spring-cloud-architecture.md`](../docs/specs/spring-cloud-architecture.md)

`core-api` 는 아직 배포 파이프라인 검증용 최소 스캐폴드다. Spring Boot 4.1 + springdoc(OpenAPI) +
Config·Eureka 클라이언트에 더해 dashboard·expense·income·profile·reference·scenario·skill
7개 도메인 컨트롤러가 REST로 노출돼 있다(경로·요청·응답 규격은 위 API 규격 문서 참조) — 다만
리포지토리·서비스 계층은 아직 연결되지 않은 스텁이다. 채용공고 도메인의 JPA 엔티티·리포지토리·
시드 로더·크롤러는 `collector` 모듈에 별도로 이미 추가됐으며, `core-api`의 REST로는 노출되지
않는다. `core-api`의 Security는 다음 단계에서 추가한다.

지수 산출 계약(`com.carix.index`)은 소득/구매력 도메인과 스킬/시장가치 도메인이 각각 구현하고
스냅샷·결정신호 엔진이 호출한다. `PurchasingPowerIndexProvider`·`MarketValueIndexProvider` 와
결과 타입(`PpiResult`, `MviResult`)만 정의돼 있고 구현은 아직 없다 — 구현 빈이 없는 동안
`com.carix.index.stub` 의 스텁이 `NOT_AVAILABLE` 을 돌려준다(기동 로그에 어느 산출기가 스텁인지
남는다). 실제 구현을 빈으로 등록하면 스텁은 물러난다.

**구현 상태:** `auth-service` 코드 구현(초대 확인, 매직링크 발급·검증, 세션 생성·조회·폐기,
rate limit)과 배포 배선(`Dockerfile`, `docker-compose.yml` 서비스 항목, Caddy 라우트, CI 빌드
잡) 모두 완료됐다 — 다만 로그인이 실제로 동작하려면 아래 `auth-service` 절의 잔여 항목이
남아 있다. Spring Cloud Gateway도 도입돼 요청 경로에 편입됐다(설계는 위
spring-cloud-architecture.md). `core-api`는 스킬 도메인이 JPA로 `core` 스키마를 쓰고 있고,
Security는 다음 단계다. `collector`는 채용공고 도메인의 JPA와 `core` 스키마를 이미 쓰고
있다(위 참조).

아래는 배포 검증·데모용 경로다. 7개 도메인 컨트롤러의 경로는 위 API 규격 문서를 참조.

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 배포 워크플로 헬스체크 대상 |
| `POST /api/v1/demo/echo` | **데모/검증용.** Swagger UI에서 요청 스키마·검증·에러 포맷을 확인하는 엔드포인트 |
| `GET /api/v1/demo/thresholds` | **데모/검증용.** Config Server 연동 확인용. 값은 [`../config/spring/core-api.yml`](../config/spring/core-api.yml)에서 오며 `/actuator/refresh` 로 재배포 없이 동적 갱신 확인 |

### auth-service

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 헬스체크 |
| `POST /api/v1/auth/magic-link` | 매직링크 발송 요청. 초대 여부와 무관하게 동일 응답(계정 열거 방지) |
| `POST /api/v1/auth/verify` | 매직링크 검증, 세션 쿠키 발급 |
| `POST /api/v1/auth/logout` | 현재 세션 폐기 |
| `POST /api/v1/auth/logout-all` | 사용자의 전체 세션 폐기 |
| `POST /internal/invitations` | 베타 초대 등록. 이미 초대된 이메일은 건너뛴다 |
| `GET /internal/invitations` | 초대 목록 조회 |

`/internal/**` 은 게이트웨이가 라우팅하지 않는다(`/api/v1/auth/**` 와 `/api/v1/**` 만 백엔드로
간다). 외부에서 도달하지 않으며, 클러스터 안에서는 `X-Internal-Api-Key` 헤더를
`carix.auth.internal-api-key` 와 대조한다. 키가 비어 있으면 전부 403 이다(fail closed).
값은 `AUTH_INTERNAL_API_KEY` 환경변수로만 넣는다.

**공개 URL 로는 부를 수 없다.** `https://<host>/api/internal/...` 은 게이트웨이의 "그 외" 규칙에
걸려 `frontend-node` 로 가고, SPA 폴백이 200 과 HTML 을 돌려준다 — auth-service 응답이 아니다.
`auth-service` 는 포트를 공개하지 않고(`expose` 만) 이미지에 `curl` 도 없으므로, compose 네트워크에
클라이언트 컨테이너를 붙여 부른다. 네트워크 이름은 `docker network ls | grep carix` 로 확인한다.

```bash
# 초대 등록
docker run --rm --network carix_default curlimages/curl:latest \
  -X POST http://auth-service:8081/api/internal/invitations \
  -H "X-Internal-Api-Key: $AUTH_INTERNAL_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"emails":["someone@example.com"]}'

# 초대 목록 확인
docker run --rm --network carix_default curlimages/curl:latest \
  http://auth-service:8081/api/internal/invitations \
  -H "X-Internal-Api-Key: $AUTH_INTERNAL_API_KEY"
```

**매직링크 메일 발송** — SMTP 로 보낸다. Gmail SMTP 를 전제로 설정했지만 provider 에 묶인 코드는
없다(`spring.mail.*` 만 바꾸면 옮겨간다). 도메인이 없어도 되고, 발신 도메인이 `gmail.com` 이라
SPF·DKIM 을 직접 세울 필요가 없다.

발송이 켜지는 조건은 `spring.mail.host` 와 `carix.auth.mail.base-url`·`from` 이 모두 있는 것이다.
하나라도 비면 `LoggingMagicLinkNotifier` 로 떨어지고 **실제 메일은 나가지 않는다**(기동 로그에 이유가
남는다). Gmail 은 계정 비밀번호가 아니라 앱 비밀번호를 받으며 2단계 인증을 켠 계정에서만 발급된다.
무료 계정 한도는 하루 500통이다. 필요한 환경변수는 [`../.env.example`](../.env.example) 참조.

발송 실패는 호출부로 던지지 않는다. 던지면 초대된 이메일만 5xx 가 나서 초대 여부가 응답으로
드러난다 — 계정 열거 방지가 깨진다. 실패는 ERROR 로그로만 남는다. 로그의 수신자 주소는
마스킹하고(`b***@example.com`) 토큰은 남기지 않는다 — 링크 전체가 곧 자격증명이다
([SECURITY.md §4](../docs/SECURITY.md)).

`LoggingMagicLinkNotifier` 로 떨어진 상태는 원본 토큰이 로그에 남으므로 운영 발송 경로가 아니다.
기동 로그의 WARN 이 그 상태를 알린다.

**잔여 항목**

- 발송·검증·세션·Rate Limit 흐름 테스트가 없다. `src/test` 에는 자동설정 회귀 테스트(Flyway·Mail),
  초대 등록 경로 테스트, 메일 발송기 단위 테스트만 있다.

로컬 기동은 `core-api`와 동일한 순서다(config-server 먼저, 그다음 `EUREKA_CLIENT_ENABLED=false`
로 단독 기동 가능). `auth-service`는 JPA·Flyway를 쓰므로 로컬 검증은 OCI DB 원격 연결
([DEVELOPMENT.md §3](../docs/DEVELOPMENT.md))으로 진행한다.

공통 에러 응답(`com.carix.common.ErrorResponse`)은 위 API 규격 문서 §1.1 형식을 따른다.
Spring 기본값(ProblemDetail)과 형식이 달라 `GlobalExceptionHandler`에서 변환한다.

컨텍스트 패스는 `/api`다. 엣지 Caddy가 `/api/*`를 경로 그대로 넘기므로, 헬스체크와
Swagger UI(`/api/swagger-ui`), OpenAPI 문서(`/api/v3/api-docs`)가 모두 그 아래로 들어온다.

### gateway

Spring Cloud Gateway(WebFlux/reactive). `carix_caddy(:80)`의 업스트림으로, 모든 애플리케이션 트래픽의 라우팅과 필터링을 총괄합니다.

| 경로 | 대상 |
| --- | --- |
| `/api/v1/auth/**` | `auth-service` (Eureka `lb://`) |
| `/api/v1/**`, `/api/health`, `/api/swagger-ui/**`, `/api/v3/api-docs/**` | `core-api` (Eureka `lb://`) |
| 그 외 | `frontend-node` (정적 URI, Eureka 미등록) |

세션 검증 필터는 `core` 라우트에 적용되며 `carix.gateway.session-validation.enabled`
(`config/spring/gateway.yml`) 설정 플래그로 켜고 끌 수 있습니다.

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
# fail-fast 설정으로 부팅에 실패합니다). curl -sf http://localhost:8888/actuator/health 로 확인 가능.
CONFIG_SERVER_URL=http://localhost:8888 EUREKA_CLIENT_ENABLED=false \
  ./gradlew :core-api:bootRun --args='--server.port=8080'
# http://localhost:8080/api/health
```

`EUREKA_CLIENT_ENABLED=false` 는 클라이언트를 아예 끈다 — `eureka` 를 안 띄워도 등록 시도나
에러 로그 없이 뜬다.

Java 툴체인은 25로 고정돼 있다. 로컬에 JDK 25가 없으면 `settings.gradle`의 foojay resolver가
직접 내려받는다 (첫 빌드 한정). CI는 `setup-java`가 25를 제공하므로 다운로드가 없다.

Dockerfile은 컴파일하지 않는다. CI 러너가 Gradle 캐시로 만든 JAR을 layered로 풀어 담기만 한다.
그래서 `tasks.named('jar') { enabled = false }`로 plain jar를 끈다 — `build/libs/*.jar` 글롭이
두 개를 집으면 `COPY`가 실패한다.

## 로컬 개발과 DB

> `auth-service`는 이미 JPA·Flyway로 `auth` 스키마를 쓰므로, 아래 원격 DB 연결 설정이
> 지금 바로 필요하다.

- 로컬엔 PostgreSQL·Docker를 띄우지 않는다. **OCI의 `carix` DB에 원격 연결**한다.
  접근은 TLS + OCI Security List IP 허용목록이다
  ([`../docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md) §3 참조).
- 운영과 개발이 **같은 DB**다. 별도 dev DB는 두지 않는다(감수한 위험은 같은 문서 §3).
- `application-local.yml` 로 `eureka.client.register-with-eureka: false` 를 반드시 켠다.
  안 켜면 서버 Eureka 에 등록돼 서버 Gateway 가 이 노트북으로 운영 트래픽을 보낸다.
  절차는 [`../docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md) §3·§5.

## 테스트 주의

- **배포 CI는 테스트를 안 돌린다.** `.github/workflows/deploy.yml`의 6개 빌드 잡 중
  backend 5개(`core-api`·`eureka`·`config-server`·`gateway`·`auth-service`)는
  전부 `./gradlew :모듈:build -x test`, `frontend`는 `docker build`(테스트 스텝 없음)다.
  빌드·이미지 푸시·배포는 빠른 빌드를 위해 테스트를 건너뛴다.
- 테스트용 DB를 별도로 두지 않기로 결정하여([DEVELOPMENT.md §6](../docs/DEVELOPMENT.md#db를-쓰는-테스트는-두지-않는다) 참조), Testcontainers 의존성을 제거하고 DB 연결 없이도 컨텍스트 로드가 검증되도록 모킹/단위 테스트 방식으로 전환했다.
- **PR 단계에서 자동 검증한다.** `.github/workflows/pr-check.yml`이 PR 단계에서 전체 모듈의 테스트(`./gradlew check`)를 자동 검증하므로, 테스트가 통과된 코드만 main에 머지된다.
