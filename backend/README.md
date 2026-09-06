# Carix Backend

Java 25 + Spring Boot. Gradle 멀티모듈 — `config-server`, `eureka`, `core-api`, `auth-service`,
`gateway`, `collector`.
`auth-service` 가 회원가입·로그인·세션을 맡는다(스키마 `auth`). `core-api` 가 도메인 로직과
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

**구현 상태:** `auth-service` 의 배포 배선(`Dockerfile`, `docker-compose.yml` 서비스 항목,
Caddy 라우트, CI 빌드 잡)은 완료됐다. 인증 로직은 재설계 반영 전이라 아래 `auth-service`
절의 설계와 현재 코드가 다르다. Spring Cloud Gateway도 도입돼 요청 경로에 편입됐다(설계는 위
spring-cloud-architecture.md). `core-api`는 스킬 도메인이 JPA로 `core` 스키마를 쓰고 있고,
Security는 다음 단계다. `collector`는 채용공고 도메인의 JPA와 `core` 스키마를 이미 쓰고
있다(위 참조).

아래는 배포 검증·데모용 경로다. 7개 도메인 컨트롤러의 경로는 위 API 규격 문서를 참조.

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 배포 워크플로 헬스체크 대상 |
| `GET /api/v1/demo/thresholds` | **데모/검증용.** Config Server 연동 확인용. 값은 [`../config/spring/core-api.yml`](../config/spring/core-api.yml)에서 오며 `/actuator/refresh` 로 재배포 없이 동적 갱신 확인 |

### auth-service

| 경로 | 용도 |
| --- | --- |
| `GET /api/health` | 헬스체크 |
| `POST /api/v1/auth/register` | 회원가입. 가입 즉시 세션 쿠키 발급 |
| `POST /api/v1/auth/login` | 이메일·비밀번호 로그인 |
| `GET /api/v1/auth/session` | 세션 조회. `{userId, email, onboardingStep}`. 게이트웨이 필터는 `userId` 만 읽는다 |
| `POST /api/v1/auth/logout` | 현재 세션 폐기 |
| `POST /api/v1/auth/logout-all` | 사용자의 전체 세션 폐기 |

**계정이 있어야 쓴다.** 계정 없이 접근할 수 있는 경로를 두지 않는다. 가입하면 곧바로
세션을 발급해 온보딩으로 보낸다.

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

**정리 잡.** 만료 세션과 윈도가 지난 `rate_limit_bucket` 행을 일 1회 스케줄러가 지운다.
auth-service 단일 인스턴스를 전제한다 — 여러 개 띄우면 중복 실행된다. 사용자 행은 정리
대상이 아니다. 계정 없이 만들어지는 행이 없고, 삭제는 사용자가 직접 하는 기능(US-C2)이다.

**구현 상태 — 백엔드 인증은 끝났다.** 가입·로그인·세션 조회·로그아웃, 세션 토큰 해시
저장, 매직링크·초대·메일 코드 폐기가 모두 반영됐다. 프런트 화면(S-01~03)과 가드 연결이
남았다. 진행 상태는
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

| 경로 | 대상 |
| --- | --- |
| 라우트 | 경로 | 대상 | 세션 검증 |
| --- | --- | --- | --- |
| `auth` | `/api/v1/auth/**` | `auth-service` (Eureka `lb://`) | 없음 |
| `core-health` | `/api/health` | `core-api` (Eureka `lb://`) | 없음 |
| `core` | `/api/v1/**`, `/api/swagger-ui/**`, `/api/v3/api-docs/**` | `core-api` (Eureka `lb://`) | 적용 |
| `web` | 그 외 | `frontend-node` (정적 URI, Eureka 미등록) | 없음 |

세션 검증 필터는 `core` 라우트에만 붙고 `carix.gateway.session-validation.enabled`
(`config/spring/gateway.yml`) 플래그로 켜고 끕니다. 헬스체크를 별도 라우트로 뺀 이유는
배포 워크플로가 쿠키 없이 `/api/health` 를 호출하기 때문입니다 — 같은 라우트에 두면 플래그를
켜는 순간 배포가 401 로 죽습니다.

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

- **배포 CI는 테스트를 안 돌린다.** `.github/workflows/deploy.yml`의 6개 빌드 잡 중
  backend 5개(`core-api`·`eureka`·`config-server`·`gateway`·`auth-service`)는
  전부 `./gradlew :모듈:build -x test`, `frontend`는 `docker build`(테스트 스텝 없음)다.
  빌드·이미지 푸시·배포는 빠른 빌드를 위해 테스트를 건너뛴다.
- 테스트용 DB를 별도로 두지 않기로 결정하여([DEVELOPMENT.md §6](../docs/DEVELOPMENT.md#db를-쓰는-테스트는-두지-않는다) 참조), Testcontainers 의존성을 제거하고 DB 연결 없이도 컨텍스트 로드가 검증되도록 모킹/단위 테스트 방식으로 전환했다.
- **PR 단계에서 자동 검증한다.** `.github/workflows/pr-check.yml`이 PR 단계에서 전체 모듈의 테스트(`./gradlew check`)를 자동 검증하므로, 테스트가 통과된 코드만 main에 머지된다.
