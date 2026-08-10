# 문서 불일치 점검 결과 보고서

- 날짜: 2026-08-10
- 설계 문서: [`2026-08-10-doc-inconsistency-audit-design.md`](2026-08-10-doc-inconsistency-audit-design.md)

## 요약

(마지막 태스크에서 채움)

## 1. 데이터/백엔드

| 문서:라인 | 문제 | 근거 | 분류 | 상태 |
| --- | --- | --- | --- | --- |
| `backend/README.md:5,15,110` | "core-api는 아직 DB를 쓰지 않는다"/"JPA·Flyway·Security·Batch는 아직 없음"이라고 서술하나, 채용공고 도메인은 이미 JPA로 원격 DB를 쓴다. | `backend/core-api/build.gradle`에 `spring-boot-starter-data-jpa`·`spring-boot-starter-flyway`·`flyway-database-postgresql`·`postgresql` 의존성이 있고, `com.carix.jobposting.JobPosting`/`JobPostingSkill`/`Skill`/`SkillAlias`/`RawIngest` 5개가 실제 `@Entity`이며 `JobPostingRepository`/`SkillRepository`/`RawIngestRepository`가 있다. `docker-compose.yml`의 `core-api` 서비스에 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`가 배선돼 있고, `erd.md`의 "2026-08-09 운영 현황" 절이 이 경로로 800건의 공고·699개 스킬·410건의 raw_ingest가 원격 DB에 실제로 적재됐다고 기록한다. → `backend/README.md`의 3곳(모듈 개요, core-api 스캐폴드 설명, "로컬 개발과 DB" 절)을 "JPA·Flyway는 채용공고 도메인에 한해 이미 원격 DB에 연결됨(마이그레이션 SQL은 저장소에 없음), Security·Batch는 아직 없음"으로 수정. | 단순 | 수정됨 |
| `docs/specs/domain-logic.md:24`, `docs/specs/external-api-integration.md:167` | 기본 소비 가중치 시드(`config/default-expense-weights.yml`)를 "Flyway 마이그레이션으로 적재"/"Flyway로 재적재"한다고 서술하나, core-api 저장소에는 Flyway 마이그레이션 디렉터리가 없고 이 파일을 읽는 코드도 없다. | `backend/core-api/src/main/resources` 아래 `db/migration` 디렉터리가 존재하지 않음(find로 확인). `grep -rn "default-expense-weights" backend/`가 아무 코드 결과도 반환하지 않아, 현재 이 yml을 로드하는 구현이 전혀 없음. → 두 문서 모두 "Flyway 마이그레이션으로/Flyway로" 문구를 "적재/재적재 방식은 구현 시 정한다 — core-api 저장소에는 Flyway 마이그레이션 디렉터리가 없다"로 수정. | 단순 | 수정됨 |
| `docs/specs/erd.md:110` | `skill` 테이블에 `created_by FK` 컬럼이 문서화돼 있으나, 실제 엔티티 `com.carix.jobposting.Skill`에는 이 필드가 없다(`id`/`standard_name`/`category`/`is_custom` 4개 컬럼만 매핑). | `Skill.java` 전체를 읽어 확인 — `@Column` 매핑이 4개뿐이고 `created_by`/`createdBy` 문자열이 클래스에 전혀 없음. 이 사실 자체(엔티티 파일에 필드가 없다는 것)는 원격 DB 없이 저장소 코드만으로 확정됨. 다만 분류는 원격 DB 접속 없이 확정 불가 — (a) erd.md가 실제 없는 컬럼을 있다고 잘못 서술한 것(단순 사실 오류, erd.md 수정 대상)인지, (b) 물리 테이블엔 이 컬럼이 존재하고(과거 마이그레이션에서 생성) 엔티티 클래스가 아직 이 컬럼을 매핑하지 않은 것(구현이 설계를 못 따라간 것, erd.md는 그대로 두고 엔티티를 확장해야 할 사안)인지 저장소 안에서는 구분할 근거가 없다. `SeedLoader`가 이 4필드 엔티티로 699건의 skill 행을 성공적으로 저장했다는 사실(erd.md "2026-08-09 운영 현황" 기록)은 물리 테이블에서 `created_by`가 nullable이거나 컬럼 자체가 없어도 성립하므로 어느 쪽인지 가려주지 못한다. 마이그레이션 SQL이 저장소에서 제거된 상태(PR #58)라 원본 스키마 정의를 재확인할 수도 없다. → 제품/구현 방향 결정이 필요한 사안으로 판단해 erd.md는 수정하지 않음. | 구조적 | 보류 |
| `docs/specs/erd.md:132` | `raw_ingest` 테이블에 `purged_at` 컬럼이 문서화돼 있으나, 실제 엔티티 `com.carix.jobposting.RawIngest`에는 이 필드가 없다(`id`/`source`/`source_item_id`/`payload`/`schema_version`/`first_seen_month`/`last_seen_month`/`collected_at` 8개 컬럼만 매핑). | `RawIngest.java` 전체를 읽어 확인 — `@Column` 매핑이 8개뿐이고 `purged_at`/`purgedAt` 문자열이 클래스에 전혀 없음. 필드 부재 자체는 저장소 코드만으로 확정되나, 분류 근거는 위 `erd.md:110` 행과 동일하다 — `data-architecture.md` §3.5가 "보존 기간 24개월, 초과분은 원문만 삭제"라는 정책을 명시하고 있어 `purged_at`이 그 정책의 착지점(아직 구현 안 된 설계)일 가능성이 있고, 반대로 erd.md가 애초에 만들어진 적 없는 컬럼을 서술했을 가능성도 배제할 수 없다. 원격 DB 접속 없이는 물리 컬럼 존재 여부를 확인할 수 없고, 마이그레이션 SQL도 저장소에 없어 원본 스키마 정의를 재확인할 수 없다. → erd.md 수정 없이 보류. | 구조적 | 보류 |

## 2. API/워크플로우

| 문서:라인 | 문제 | 근거 | 분류 | 상태 |
| --- | --- | --- | --- | --- |
| `docs/specs/api-specification.md:§1.1` | 문서의 모든 "Response" JSON 예시가 응답 본문 그대로인 것처럼 서술하나, 실제 core-api 컨트롤러는 성공 응답을 공통 봉투(envelope)로 한 번 감싸 반환한다. | `com.carix.common.ApiResult`가 `success`/`data`/`message`/`timestamp` 4필드 레코드이고, `DashboardController`·`ExpenseController`·`IncomeController`·`SkillController`·`ReferenceController`·`ScenarioController`·`ProfileController` 7개 컨트롤러 전부가 `ResponseEntity<ApiResult<T>>`를 반환한다(각 파일에서 직접 확인). `ResponseBodyAdvice`/`@ControllerAdvice` 언랩 로직은 저장소에 없음(grep 결과 없음) — 즉 실제 HTTP 응답 본문은 문서의 JSON 예시를 `data` 필드 값으로 한 번 더 감싼 형태다. 에러 포맷(§1.1의 4xx/5xx 예시)은 `GlobalExceptionHandler`가 `ErrorResponse`를 언랩 상태로 그대로 반환해 문서와 일치 — 성공 응답에만 이 누락이 있다. → §1.1에 "성공 응답 포맷 (HTTP 2xx)" 절을 추가해 이후 모든 Response 예시가 `data` 필드 값만 보여준다는 것을 명시. | 단순 | 수정됨 |
| `docs/specs/api-specification.md:§5.1,§5.2,§5.5` | `GET /dashboard/summary`·`/salary-distribution`·`/skills` 3개 엔드포인트에 `jobFamily`/`yearsOfExperience`/`region`/`companySize` 4개 선택적 쿼리 파라미터("가상 프로필 탐색용")가 실제로 존재하나 문서에 전혀 서술되지 않았다. | `DashboardController.getDashboardSummary`/`getSalaryDistribution`/`getSkillDemands` 3개 메서드 모두 동일한 4개 `@RequestParam(required = false)` 파라미터를 받고, `@Parameter(description = "가상 탐색용 ...")`로 Swagger 문서화까지 돼 있다. `grep -rn "가상 프로필\|가상 탐색" docs/specs/*.md`가 이 3개 메서드의 주석을 제외하면 아무 데도 걸리지 않아, `docs/specs/` 전체에 이 기능을 설명하는 문서가 하나도 없음을 확인. → 3개 절에 Query Parameters 절을 추가. | 단순 | 수정됨 |
| `docs/specs/api-specification.md:§6.1,§6.2,§6.3` | 시나리오 API 응답 예시에 실제 응답에 존재하는 `feasibility`(실현 가능성) 객체가 빠져 있고, §6.2·§6.3의 `inputs`/`result`는 실제로 §6.1의 요청·응답 레코드 전체를 그대로 담아 `scenarioName`(및 `result` 안의 `id`/`scenarioName`)이 중복 노출되는데 문서 예시는 이를 생략해 필드 수가 실제보다 적다. | `SimulationResponse` 레코드(`backend/core-api/.../scenario/dto/SimulationResponse.java`)의 마지막 컴포넌트가 `FeasibilityDetail feasibility`(`feasibilityScore`/`targetPercentile`/`difficultyGrade`/`feedbackMessage`)이고 `ScenarioController.simulateScenarios`가 항상 이 값을 채워 반환한다. `LatestScenarioResponse`(`.../scenario/dto/LatestScenarioResponse.java`)는 `inputs: SimulationRequest`(7개 필드, `scenarioName` 포함)와 `result: SimulationResponse`(10개 필드, `id`·`scenarioName`·`feasibility` 포함) 전체를 그대로 품고, `ScenarioController.getLatestScenario`/`getScenarios`가 `new SimulationRequest("A사 오퍼 제안", ...)`·`new SimulationResponse(id, "A사 오퍼 제안", ..., calculateFeasibility(targetSalary))`로 두 필드를 채워 호출한다 — 코드상 중복 노출이 실제로 일어남. → §6.1·6.2·6.3 응답 예시에 `feasibility` 객체를 추가하고, §6.2·6.3의 `inputs`/`result`에 누락된 `scenarioName`/`id`/`scenarioName`을 채운 뒤 중복 사유를 설명 각주로 덧붙임. | 단순 | 수정됨 |
| `docs/specs/application-workflow.md:§1(다이어그램),§3(시퀀스)` | §1 flowchart의 `API [Spring Boot API 서버]` 서브그래프가 `AC[AuthController]`·`PC[ProfileController]`·`DC[DashboardController]`·`SC[ScenarioController]`를 하나의 Spring Boot 서버로 묶어 그리고, §3 시퀀스 다이어그램도 Gateway 없이 단일 `participant API as Spring API`로 `/api/v1/auth/magic-link`부터 `/api/v1/scenarios/simulate`까지 전부 보낸다. 그러나 `docs/specs/api-specification.md` §1.1·§2는 "전환 후 모든 요청은 Spring Cloud Gateway를 거친다"·"소유 서비스: auth-service. core-api가 아니다 — core-api에 이 절의 엔드포인트를 재구현하지 않는다"라고 명시해, Auth가 core-api와 분리된 별도 서비스임을 전제한다. | `docs/specs/spring-cloud-architecture.md` §2 서비스 구성표는 `auth-service`(8081)와 `core-api`(8082)를 별도 서비스로 나열하고 문서 상단에 "계획 B(Gateway 도입, 요청경로 편입 완료)"가 "구현 완료 — 2026-08-02"라고 기록한다. 다만 같은 문서 상단이 Gateway의 세션 검증 필터가 `carix.gateway.session-validation.enabled=false`(기본값)라 아직 꺼져 있고, 서버 배포·Eureka 등록 확인 전에 켜면 core-api로 가는 모든 요청이 401/503이 된다고 명시하며, `api-specification.md` §1.1 자체도 이를 "**전환 후**" 거치는 경로로 표현해 실사용 시점이 아니라 목표 상태임을 시사한다. 즉 application-workflow.md의 두 다이어그램이 Gateway·auth-service 분리를 반영하지 않은 것은 사실이지만, 이 문서가 "전환 전(현재 배선 비활성 상태)"을 그린 것인지 "전환 후(목표 아키텍처)"를 그려야 하는데 갱신이 안 된 것인지는 저장소 안에서 확정할 수 없다 — 이는 어느 시점을 문서화할지에 대한 소유자 판단이 필요한 사안이라 application-workflow.md는 수정하지 않았다. 클러스터 3(아키텍처/인프라, Task 4)이 spring-cloud-architecture.md를 직접 다루므로 Task 6에서 두 태스크의 판단을 조율할 필요가 있다. | 구조적 | 보류 |
| `docs/specs/api-specification.md:398,511,519` | 세 문장 모두 영속성(persistence) 계층이 있어야 성립하는 분기 동작을 현재형으로 서술한다 — L398 "저장된 시나리오가 없으면 `204 No Content`. `calculated_at DESC, id DESC` 순 첫 행 반환"(§6.2 `GET /scenarios/latest`), L511 "동일 정규화 스킬 중복 시 `409 Conflict`"(§7.2 `POST /user-skills`), L519 "유효 시작일 중복 시 `409 Conflict`"(§7.4 `PUT`/`DELETE /income/{id}`). 그러나 이 세 엔드포인트 모두 리포지토리가 연결돼 있지 않아 해당 분기 코드 자체가 존재하지 않는다. | `ScenarioController.getLatestScenario()`는 조건문 없이 항상 하드코딩된 `id`·`targetSalary`로 200 OK 단건을 반환한다 — "저장된 시나리오가 없으면" 분기도 `calculated_at DESC, id DESC` 정렬도 코드에 없어 204는 현재 도달 불가능하다. `SkillController.createUserSkill()`은 `request`의 필드를 그대로 담아 항상 201로 성공 응답을 만들 뿐 기존 스킬과의 중복 여부를 비교하는 코드가 없다. `IncomeController.updateIncome()`/`deleteIncome()`도 마찬가지로 `id`와 요청값을 그대로 돌려주거나 무조건 성공 메시지를 반환할 뿐 다른 이력과의 유효 시작일 충돌을 검사하지 않는다. `grep -rln "Repository\|@Autowired\|private final.*Service" backend/core-api/src/main/java/com/carix/{dashboard,expense,income,skill,reference,scenario,profile}/*.java`가 결과 없음(0건) — 즉 도메인 컨트롤러 7개 전부 리포지토리·서비스 계층 주입이 아직 없는 동일한 스캐폴드 단계라, 이 세 문장뿐 아니라 구조적으로 "persistence 의존 분기는 전부 미도달"이라는 동일 패턴이다. 이는 finding 1~3(스텁 상태에서도 매 호출마다 실제로 관측되는 응답 shape 차이)과 달리 매 호출 관측 가능한 shape 차이가 아니라, 아직 오지 않은 입력 조합(중복 제출, 빈 이력)에서만 드러나는 차이이며, `api-specification.md`가 목표 동작(리포지토리 연결 이후의 최종 계약)을 서술한 것으로 보고 그대로 둘지, 아니면 "미구현" 주석을 붙여 현재 상태를 반영할지는 API 계약 문서의 성격(목표 스펙 vs 현재 상태 스펙)에 대한 오너 판단이 필요하다 — finding 4(Gateway/auth 분리, 목표 아키텍처 vs 현재 배선)와 동일한 논리 구조. | 구조적 | 보류 |

## 3. 아키텍처/인프라

| 문서:라인 | 문제 | 근거 | 분류 | 상태 |
| --- | --- | --- | --- | --- |
| _(조사 결과 없음)_ | | | | |

## 4. 프론트엔드

| 문서:라인 | 문제 | 근거 | 분류 | 상태 |
| --- | --- | --- | --- | --- |
| _(조사 결과 없음)_ | | | | |

## 5. 메타/최상위 (교차 검증)

| 문서:라인 | 문제 | 근거 | 분류 | 상태 |
| --- | --- | --- | --- | --- |
| _(조사 결과 없음)_ | | | | |
