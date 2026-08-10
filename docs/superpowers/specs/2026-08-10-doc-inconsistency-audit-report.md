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
