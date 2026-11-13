# Carix Backend

Java 25 + Spring Boot. Gradle 멀티모듈 — `config-server`, `eureka`, `core-api`, `auth-service`,
`gateway`, `collector`.
`auth-service` 가 회원가입·로그인·세션을 맡는다(스키마 `auth`). `core-api` 가 도메인 로직과
REST API를 맡는다 — 사용자 도메인(프로필·소득·소비·스킬)과 지수·스냅샷·시나리오가 JPA로
`core` 스키마를 쓴다. 채용공고 수집·집계는 `collector` 소관이고 같은 `core` 스키마를 공유한다
(DDL은 [`../deploy/postgres/schema/`](../deploy/postgres/schema/)).

**`core-api` 에 Spring Security 스타터를 쓰지 않는다.** 세션 검증은 게이트웨이가 하고 하류는
주입된 `X-User-Id` 를 인증된 사용자로 신뢰한다 — 수신부는 `@CurrentUserId` 와
`UserContextArgumentResolver` 이며 헤더가 없으면 401 이다. 빠진 것이 아니라 택한 구조다.

상세 설계:
- DB 스키마: [`../docs/specs/erd.md`](../docs/specs/erd.md) · 계산 규칙: [`../docs/specs/domain-logic.md`](../docs/specs/domain-logic.md)
- API 규격: [`../docs/specs/api-specification.md`](../docs/specs/api-specification.md)
- 도메인 계산: [`../docs/specs/domain-logic.md`](../docs/specs/domain-logic.md)
- Spring Cloud 구성: [`../docs/specs/spring-cloud-architecture.md`](../docs/specs/spring-cloud-architecture.md)

`core-api` 는 Spring Boot 4.1 + springdoc(OpenAPI) + Config·Eureka 클라이언트 위에
dashboard·expense·income·profile·scenario·skill 도메인 컨트롤러를 REST로 노출한다(경로·요청·응답
규격은 위 API 규격 문서). **리포지토리·서비스 계층이 연결돼 실데이터로 응답한다** — 고정값을
돌려주던 스텁 컨트롤러는 전부 철거했다. 채용공고 수집·파싱·집계는 `collector` 모듈 소관이라
`core-api` 의 REST로는 노출되지 않지만, `core-api` 는 그 결과 테이블(`job_posting`·
`market_aggregate`·`wage_stat_observation`)을 읽는다.

지수 산출 계약(`com.carix.index`)은 소득/구매력 도메인과 스킬/시장가치 도메인이 각각 구현하고
스냅샷·결정신호 엔진이 호출한다. `MarketValueIndexServiceImpl`(MVI),
`IncomeCurrentSalaryProvider`(기준월 세전 연봉), `DefaultPurchasingPowerIndexProvider`(소득지수
÷ 개인물가 지수, 품목별 CPI × 소비 가중치)가 배선돼 있다. 소득 쪽은 `EffectiveIncome`(따로 받는
고정상여를 반영한 소득)을 거치고, `PersonalInflationService` 가 지난 12개월 개인물가 상승률을
총지수와 견준다.
`com.carix.index.stub` 은 구현 빈이 없을 때만 `NOT_AVAILABLE` 을 돌려주는 폴백으로 남아 있고,
기동 로그에 어느 산출기가 스텁인지 남는다.

**구현 상태:** `auth-service` 는 인증 재설계가 반영돼 가입·로그인·세션 조회·로그아웃이 동작하고
배포 배선(`Dockerfile`, compose 서비스, Caddy 라우트, CI 빌드 잡)도 끝났다. Spring Cloud Gateway
는 요청 경로에 편입돼 있다(설계는 위 spring-cloud-architecture.md). `collector` 는 크롤러·중복제거·
정규화·`market_aggregate` 집계·임금통계 수집과 내부 API(`/internal/pipeline/**`)까지 구현돼 있다.

> 마일스톤 진행 상태(무엇이 끝났고 무엇이 남았나)는 [`../docs/README.md` 「진행 상태」](../docs/README.md#진행-상태)가
> 소관이다. 이 문서는 모듈별 구현 상태만 다룬다.

아래는 운영·검증용 경로다. 도메인 컨트롤러의 경로는 위 API 규격 문서를 참조.

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 배포 워크플로 헬스체크 대상 |
| `GET /api/v1/demo/thresholds` | **데모가 아니다 — Config Server 연동 확인 절차의 도구다** ([DEVELOPMENT.md](../docs/DEVELOPMENT.md)). 경로가 `demo` 로 시작하는 것만 오해를 부른다. 값은 [`../config/spring/core-api.yml`](../config/spring/core-api.yml)에서 오며 `/actuator/refresh` 로 재배포 없이 동적 갱신 확인 |

### auth-service

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 헬스체크 |
| `POST /api/v1/auth/register` | 회원가입. 가입 즉시 세션 쿠키 발급 |
| `POST /api/v1/auth/login` | 이메일·비밀번호 로그인 |
| `POST /api/v1/auth/guest` | 게스트 시작. 계정 없이 쓰는 24시간 세션 쿠키 발급, 유효한 세션이 있으면 그 사용자를 돌려준다 |
| `GET /api/v1/auth/session` | 세션 조회. `{userId, email, onboardingStep, guest}`(가입·로그인·게스트 시작 응답도 같은 형식, 게스트면 `email` 은 `null`). 게이트웨이 필터는 `userId` 만 읽는다 |
| `PATCH /api/v1/auth/onboarding` | 온보딩 단계 갱신 |
| `POST /api/v1/auth/onboarding/reset` | 온보딩 단계를 `PROFILE` 로 초기화 |
| `POST /api/v1/auth/logout` | 현재 세션 폐기 |
| `POST /api/v1/auth/logout-all` | 사용자의 전체 세션 폐기 |

**계정 또는 게스트 세션이 있어야 쓴다.** 세션 없이 쓸 수 있는 사용자 API 는 없다. 가입하면 곧바로
세션을 발급해 온보딩으로 보낸다. 계정 없이 둘러보려는 방문자는 게스트 시작으로 `guest` 사용자 행과
24시간 세션을 받는다 — 가입 계정과 같은 `userId` 체계를 쓴다.

**세션 토큰은 해시로 저장한다.** `session.token_hash` 에 SHA-256 hex 만 남기고 원본은
쿠키에만 있다. 세션은 30일짜리 자격증명이라 DB 유출 시 피해가 가장 크다.

**비밀번호는 BCrypt.** 최소 8자를 요구하고 문자 구성은 강제하지 않는다.
`spring-security-crypto` 의 `BCryptPasswordEncoder` 만 쓰고 Spring Security 필터 체인이나
`UserDetailsService` 는 두지 않는다 — 인증은 세션이 이미 하고 있다.

**계정 열거 방지 범위.** 로그인 실패는 없는 이메일과 비밀번호 불일치를 구분하지 않는다.
반대로 계정 등록의 이메일 중복은 알려준다 — 숨기면 사용자가 가입 실패 원인을 알 수
없다. 대신 등록에 IP 속도 제한을 걸어 이메일 목록 수집을 막는다. 제한값은
[SECURITY.md §2](../docs/SECURITY.md) 를 정본으로 한다.

**비밀번호 재설정과 이메일 소유 증명은 없다.** 이메일 발송 경로를 두지 않기 때문이다.
비밀번호를 잊으면 다른 이메일로 새 계정을 만든다. 사용자 실체가 본인과 방문자뿐이라
사칭 동기가 없고, 증명을 하려면 메일 발송이 다시 필요해진다.

**정리 잡.** 만료 세션과 창이 끝난 `rate_limit_bucket` 행을 `AuthCleanupJob` 이 매시 5분에 지운다.
현재 창은 남긴다 — 지우면 그 시간의 카운터가 다시 세져 제한이 풀린다. 속도 제한 키에는 이메일·IP 가
평문으로 들어 있어, 탈퇴한 사용자의 이메일도 창이 끝난 뒤 1시간 안에 사라진다.
auth-service 단일 인스턴스를 전제한다 — 여러 개 띄우면 중복 실행되지만 두 삭제 모두 멱등이다. 사용자 행은 이 잡의
정리 대상이 아니다. 세션이 모두 만료된 게스트는 core-api `GuestCleanupJob` 이 매시 정각에 데이터와 함께 지우고,
가입 계정의 삭제는 사용자가 직접 하는 기능(US-C2)이다.

**구현 상태 — 백엔드 인증은 끝났다.** 가입·로그인·세션 조회·로그아웃, 세션 토큰 해시
저장, 매직링크·초대·메일 코드 폐기가 모두 반영됐다. 진행 상태는
[`docs/README.md` 진행 상태](../docs/README.md#진행-상태) 절을 따른다.

로컬 기동은 `core-api`와 동일한 순서다(config-server 먼저, 그다음 `EUREKA_CLIENT_ENABLED=false`
로 단독 기동 가능). `auth-service`는 JPA·Flyway를 쓰므로 로컬 검증은 OCI DB 원격 연결
([DEVELOPMENT.md §3](../docs/DEVELOPMENT.md))으로 진행한다.

공통 에러 응답(`com.carix.common.ErrorResponse`)은 위 API 규격 문서 §1.1 형식을 따른다.
Spring 기본값(ProblemDetail)과 형식이 달라 `GlobalExceptionHandler`에서 변환한다.

컨텍스트 패스는 `/api`다. 엣지 Caddy가 `/api/*`를 경로 그대로 넘기므로, 헬스체크와
Swagger UI(`/api/swagger-ui`), OpenAPI 문서(`/api/v3/api-docs`)가 모두 그 아래로 들어온다.

### gateway

Spring Cloud Gateway(WebFlux/reactive). `carix_caddy(:80)`의 업스트림으로, 모든 애플리케이션 트래픽의 라우팅과 필터링을 총괄합니다.

| 라우트 | 경로 | 대상 | 세션 검증 |
| --- | --- | --- | --- |
| `auth` | `/api/v1/auth/**` | `auth-service` (Eureka `lb://`) | 없음 |
| `core-health` | `/api/health` | `core-api` (Eureka `lb://`) | 없음 |
| `collector-admin` | `/api/v1/admin/collector/**`, `/api/v1/admin/pipeline/**` → `/internal/pipeline/**` 로 바꿔 씀 | `collector` (Eureka `lb://`) | 없음 — collector 가 `X-Admin-Key` 로 막는다 |
| `core` | `/api/v1/**`, `/api/swagger-ui/**`, `/api/v3/api-docs/**` | `core-api` (Eureka `lb://`) | 적용 |
| `web` | 그 외 | `frontend-node` (정적 URI, Eureka 미등록) | 없음 |

세션 검증 필터는 `core` 라우트에만 붙고 `carix.gateway.session-validation.enabled`
(`config/spring/gateway.yml`) 플래그로 켜고 끕니다. 필터에 경로 예외가 없어 `session_token`
쿠키가 없으면 즉시 401 입니다.

헬스체크를 별도 라우트로 뺀 이유는 배포 워크플로가 쿠키 없이 `/api/health` 를 호출하기
때문입니다(`.github/workflows/deploy.yml`) — 같은 라우트에 두면 플래그를 켜는 순간 배포가
401 로 죽고, 자동 롤백은 `.env`(이미지 태그)만 되돌리는 반면 플래그는 `config/spring/gateway.yml`
에 있어 다음 배포도 같은 자리에서 죽습니다. `core-health` 에 `order: -15` 를 명시한 이유는
따로 있습니다 — `web`(`Path=/**`, order 0)과 같으면 `/api/health` 가 프런트엔드로 흘러 SPA 셸을
200 으로 돌려주고, `core-api` 가 죽어 있어도 헬스체크가 통과합니다.

`/api/swagger-ui/**`·`/api/v3/api-docs/**` 는 `core` 라우트에 남깁니다. 플래그를 켜면 문서도
세션을 요구하지만 같은 출처 요청이라 로그인한 개발자의 브라우저는 쿠키를 실어 보내 그대로
열리고, 쿠키 없는 접근에는 API 표면이 닫힙니다.

**플래그는 켜져 있고, 끄는 것은 롤백 수단이 아닙니다.** 끄면 필터가 `X-User-Id` 를 붙이지 않고
인바운드 헤더는 아래 필터가 벗기므로, core-api 의 사용자 API 가 전부 실패합니다 — `@CurrentUserId` 를 쓰는 곳은
`401 UNAUTHORIZED`(`UserContextArgumentResolver`), `@RequestHeader("X-User-Id")` 를 쓰는 곳은 `400` 입니다. 플래그를 바꿀 때는 `.env` 롤백으로 부족합니다 —
`config/spring/gateway.yml` 을 직접 고치고 게이트웨이 `/actuator/refresh` 를 다시 호출합니다.

인바운드 `X-User-Id` 는 `UserIdHeaderStripGlobalFilter` 가 플래그와 무관하게 전 라우트에서
벗깁니다. 하류는 이 헤더를 인증된 사용자로 신뢰하므로 출처가 게이트웨이여야 합니다.

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

- **배포 CI는 테스트를 안 돌린다.** `.github/workflows/deploy.yml`의 빌드 잡 중
  backend 6개(`core-api`·`eureka`·`config-server`·`gateway`·`auth-service`·`collector`)는
  전부 `./gradlew :모듈:build -x test`, `frontend`는 `docker build`(테스트 스텝 없음)다.
  빌드·이미지 푸시·배포는 빠른 빌드를 위해 테스트를 건너뛴다.
- core-api·collector 의 DB 테스트는 인메모리 H2 로 돌고, 실제 PostgreSQL 로 도는 테스트는 없다
  ([DEVELOPMENT.md §6](../docs/DEVELOPMENT.md#테스트-db-는-인메모리-h2-다)).
- **PR 단계 검증은 머지를 막지 않는다.** `.github/workflows/pr-check.yml`이 바뀐 모듈마다 `./gradlew :모듈:check` 를
  돌리지만, `main` 에 브랜치 보호가 없어 검사가 실패해도 머지할 수 있다. draft PR 과 `skip-ci` 라벨이 붙은 PR,
  문서(`**.md`·`docs/**`)만 바꾼 PR 은 검사 자체가 돌지 않는다.
