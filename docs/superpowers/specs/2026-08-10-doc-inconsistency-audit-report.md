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
| _(조사 결과 없음)_ | | | | |

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
