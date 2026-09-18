# Carix 상세 기술 명세서 (Detailed Technical Specifications)

이 문서는 Carix 1차 MVP 구현에 필요한 데이터베이스 스키마(DDL), 보안 암호화 정책, 결정 신호 룰 엔진 매트릭스, 표준 스킬 사전 시드(Seed) 데이터를 정의합니다.

---

## 1. 데이터베이스 스키마 및 보안 암호화 정책

데이터베이스는 **PostgreSQL**을 사용하며, 모든 테이블 구조와 민감자산 정보(연봉, 소비금액)의 암호화 필드 정책을 다음과 같이 정의합니다.

### 1.1 컬럼 레벨 암호화 (Column-level Encryption) Policy
*   **대상 컬럼:** `income_history.gross_salary_encrypted`, `income_history.net_salary_encrypted`, `expense_item.amount_encrypted`
*   **알고리즘:** **AES-256-GCM** (또는 보안 강도가 검증된 AES-256-CBC)
*   **구현 방식:** 애플리케이션 레이어(Spring Boot)에서 **Spring Data JPA Converter**(`AttributeConverter`)를 구현하여 DB 적재 시 자동 암호화하고, 조회 시 복호화합니다. 평문 데이터는 DB 버퍼나 파일시스템에 그대로 기록되지 않도록 차단합니다.

### 1.2 스키마 설계 주석

> **실행 정본은 [`deploy/postgres/schema/`](../../deploy/postgres/schema/)의 `.sql` 파일이고,
> 테이블·컬럼 목록은 [ERD](erd.md)가 갖는다**(적용 절차는 [DEVELOPMENT.md §3.2](../DEVELOPMENT.md)).
> 이 절은 둘 중 어느 쪽에도 안 남는 것만 적는다 — 설계 의도, 실물과 갈린 지점, 수집되지만
> 아무도 안 읽는 컬럼.
>
> **`users` 외래키는 걸지 않는다.** `users`는 auth-service가 `auth` 스키마에 소유하고,
> `core` 테이블은 core-api가 소유한다. 스키마를 가로지르는 외래키는 두 서비스의 배포를
> 묶어 버리므로 `user_id UUID NOT NULL`만 두고 참조 무결성은 애플리케이션이 지킨다.
>
> **채용공고 수집 5개 테이블은 원 설계와 다르다.** 크롤링 실측에 맞춰 완화된 상태로
> 운영 중이며, 정확한 정의는 [`01_job_posting.sql`](../../deploy/postgres/schema/01_job_posting.sql)에 있다.
>
> | 테이블 | 원 설계 | 실물 |
> | --- | --- | --- |
> | `job_posting` | `title VARCHAR(255)`, `company_name`·`region`·`experience_min`·`employment_type`·`company_size`·`job_family` 전부 NOT NULL | `title VARCHAR(500)`, 해당 컬럼 전부 NULL 허용. `company_badge`·`position_level` 컬럼 추가 |
> | `job_posting.salary_*` | 원 단위 | **원 단위로 저장한다.** 원천 표기가 만원이라 적재 경로가 변환한다 — `JobKoreaListingEnricher`·`JobKoreaPayParser` 가 `값 < 1,000,000` 이면 `× 10,000`. 옛 만원 단위 행은 `06_fix_salary_units.sql` 이 같은 기준으로 올렸다. **읽을 때 다시 환산하지 않는다.** 휴리스틱이라 연 100만원 미만 실제 금액은 잘못 곱해지지만 채용공고 연봉에는 그런 값이 없다 |
> | `job_posting.salary_*` (사람인) | — | **사람인은 연봉을 주지 않는다.** `SaraminListingFields` 에 필드가 없어 사람인 행은 전부 `null` 이고, 그 원천의 `market_aggregate` 5수치도 전부 `null`·결측률 100% 다 |
> | `skill` | `standard_name VARCHAR(100)` 단독 UNIQUE, `category NOT NULL` | `VARCHAR(255)`, `UNIQUE (standard_name, category)`, `category` NULL 허용 |
> | `skill.category` | `'BACKEND'`·`'FRONTEND'` 등 기술 분야 | `'SKILL'`·`'JOB_CATEGORY'`·`'INDUSTRY'` — 수집 태그의 성격 구분 |
> | `skill_alias` | `alias_name VARCHAR(100) UNIQUE` | `VARCHAR(255)`, UNIQUE 없음. `SkillService`·`SkillRepository` 가 자동완성 검색에서 읽는다(`US-O5`) |
> | `raw_ingest` | 원문 보관 + 24개월 purge | **원문을 보관하지 않는다.** `payload`·`purged_at`·`schema_version` 컬럼 없음. 수집 이력만 남는다 |
> | `job_posting.content` | 공고 상세 본문 텍스트 | **컬럼 없음.** 스킬 파싱은 `raw_ingest` 원문에서 직접 한다 |
> | `job_posting.first_seen_month`·`last_seen_month` | 공고를 처음·마지막 본 월 | **상수다.** `SeedLoader` 가 `SEED_MONTH` 를 그대로 넣어 공고마다 같은 값이다. 관측 기간을 재는 데 쓸 수 없다 — 등록·마감은 `posted_start_date`·`posted_end_date` 를 쓴다(원천이 공고마다 실어 준다) |
>
> **`market_aggregate.base_year_month` 는 집계 실행 라벨이지 공고의 월이 아니다.**
> `MarketAggregateService.aggregateMarketDataForMonth` 가 `jobPostingRepository.findAll()` 을
> 월 필터 없이 집계하므로 그 라벨은 "그 달에 열려 있던 공고"를 뜻하지 않는다. 월별 시계열이
> 필요한 값은 `job_posting.posted_start_date`(등록일)로 직접 버킷해야 한다(§4.5·§4.6).
> **집계월이 하나뿐이면** `MarketValueIndexServiceImpl.resolveEffectiveYearMonth` 가 모든
> 요청월을 그 달로 접어 MVI 가 전 기간 상수가 된다 — 실재 여부는
> `SELECT DISTINCT base_year_month FROM core.market_aggregate ORDER BY 1;` 로 확인한다.
>
> **수집하지만 `core-api` 가 읽지 않는 컬럼들.** 크롤러가 채우고 어느 화면도 쓰지 않는다 —
> 지우자는 판단이 아니라, 새 기능을 설계할 때 **이미 있는 재료**임을 잃지 않으려고 적는다.
>
> | 컬럼 | 상태 |
> | --- | --- |
> | `job_posting.title`·`company_name` | 적재만 된다. 집계는 차원만 쓰고 화면은 공고 개별을 보여주지 않는다 |
> | `job_posting.position_level` | 직급 문자열. **잡코리아 원천에만 있다** — 사람인 행은 `null` 이라 절반 모수다. 경력 브래킷과 별개의 세 번째 연차 신호이지만 반쪽이라 그대로는 못 쓴다 |
> | `job_posting.company_badge` | 잡코리아 전용 배지. 사람인 `null` |
> | `job_posting.dedup_rule_version` | 중복제거 규칙 버전. 규칙을 바꿔 재집계할 때 어느 행이 옛 규칙 산물인지 가리려던 컬럼인데 읽는 코드가 없다 |
> | `cpi_observation` 전체 | `collector` 가 적재하고 **`core-api` 에 읽는 코드가 0건이다.** 품목별 CPI 기반 PPI 본 엔진이 미착수라서다(개발 계획 `a2`). 첫 소비자는 `a5` 의 1위 품목 뱃지(`US-D1`)로 잡혀 있다 |
>
> **`wage_stat_observation.wage_median` 은 경력별 실측이 아니다.** KOSIS 연령대를 경력구간으로
> 프록시한 값이고(`25~29세 → JUNIOR`, `WageStatIngestService.AGE_CODE_TO_EXPERIENCE_BRACKET`),
> API 를 호출하지 못하면 하드코딩 `FALLBACK_MEDIAN_BY_BRACKET` 로 떨어진다. `region` 은 항상
> `ALL` 이다. 연봉 격차 판정의 기준선으로는 채택된 값이지만, **이 값을 분모로 비율을 만들면 그
> 비율이 시장이 아니라 프록시 정합도를 절반쯤 잰다.**


**`career_scenario` 의 금액 컬럼**은 `income_history` 와 같은 AES-256-GCM 으로 저장한다.
평문이면 `stay_compensation_12m` 을 `stay_expected_raise_rate` 로 나눠 현재 연봉이 원
단위까지 복원되어, 연봉을 암호화한 이유가 이 테이블에서 사라진다. 금액으로 조회·정렬하는 인덱스가
없어 질의 비용은 없다.

실현 가능성은 재계산하지 않고 저장한다. 동료집단 분포는 그 시점의 공고에 의존해 나중에 다시
구하면 사용자가 실제로 본 판단이 나오지 않는다.

### 1.3 스냅샷 갱신 의미론 (Snapshot Replacement Semantics)

*   동일 `(user_id, base_year_month, data_version, rule_version)` 키의 재실행은 기존 행을 대체(upsert)하며 중복 행을 만들지 않는다.
*   사용자 입력 변경(과거 소득·소비·스킬 수정 포함) 시 새 `data_version`을 발급하고, 변경의 가장 이른 유효월부터 현재까지의 스냅샷을 새 버전으로 재계산해 저장한다.
*   조회의 짝 선택 순서: 월별로 먼저 최신 `data_version`을 고르고, 그 안에서 최신 `rule_version`(숫자 세그먼트 비교)을 고른다. 규칙 버전만 배포된 경우 다음 정기 스냅샷 갱신 때 최신 데이터 버전으로 재계산되어 반영된다. 이전 버전 행은 근거 추적을 위해 보존한다.
*   보존된 이전 버전 행은 **회차 비교 조회의 원천**이기도 하다. 같은 `(user_id, base_year_month)`의 서로 다른 `data_version` 두 행을 비교하면 사용자 입력 변경이 지수·결정 신호에 미친 영향이 시간 경과 효과와 분리된다. 이 조회는 위의 짝 선택을 우회하므로 응답이 어느 회차인지 명시해야 하며, 화면은 비교 대상 회차를 현재 값으로 표시하지 않는다.
*   시나리오는 사용자당 최근 1건을 유효 시나리오로 취급하며, `GET /api/v1/scenarios/latest`는 `calculated_at DESC, id DESC` 순 첫 행을 반환한다 (동일 시각 타이브레이커: `id`).
*   `data_version`은 **스냅샷 계산 실행 단위**로 발급한다: 사용자 입력 변경에 따른 재계산과 배치 정기 재계산(외부 데이터 갱신 — CPI, 공고 집계, 임금통계 vintage, dedup 규칙 변경 포함) 모두 새 버전을 발급받는다. 형식은 발급 시각 기반 `YYYYMMDDHHMMSS-NNN`(UTC, `NNN`은 같은 초 내 충돌 시 0부터 증가하는 3자리 시퀀스)이며, 발급은 사용자 단위 직렬화로 유일성과 단조 증가를 보장한다 — 잠글 행을 전제하지 않는 `pg_advisory_xact_lock(hashtextextended(user_id::text, 0))`을 쓴다. 온보딩 중인 사용자는 `career_profile` 행이 아직 없을 수 있고, 해시 충돌은 무관한 두 사용자를 직렬화할 뿐 정합성을 해치지 않는다. "최신 버전" 판정은 사전순 비교를 따른다. 스냅샷 `rationale`에 계산에 사용된 원천 기준일·배치 실행 ID를 기록해 외부 데이터 상태를 재현 가능하게 한다.
*   **`rationale`에 금액을 쓰지 않는다.** 이 컬럼은 평문 JSONB이고 연봉은 `income_history`에서 AES-256-GCM으로 보호된다(§1.1). 같은 값을 여기 적으면 컬럼 암호화가 무력화된다. 기록 대상은 임계값 문구·원천 기준일·배치 실행 ID·원천별 커버리지·스킬별 기여도, 그리고 **소득 격차의 구간 코드**다.
*   **소득 격차는 비율이 아니라 구간으로 적는다.** 비율(소수점)을 남기면 평문 `wage_stat_observation.wage_median`·평문 `career_profile` 동료집단과 조인해 $연봉 = 중앙값 / (1 + 격차비율)$ 로 원 단위 복원된다 — 중앙값을 안 적어도 조인으로 구해지므로 "둘 중 하나만"으로는 막히지 않는다. `SalaryGapBucket` 구간(경계 -0.05/0.05/0.15, 결정 규칙표와 같다)만 저장하고 비율 자체는 `DecisionOutcome` 안에만 둔다. 구간으로 접어도 화면 근거에서 잃는 정보는 없다 — `rulesApplied` 가 이미 같은 구간을 문구로 담고 있고 소수점 비율은 복원 정밀도만 더했다. **저장하지 않고 조회 시 재계산하는 안(案)은 기각했다** — 완화 결과가 계산 당시 프로필에 의존해 나중에 재계산하면 이력이 깨진다. 화면이 금액이 포함된 문장을 보여줘야 하면 프런트엔드가 보유한 값으로 조립한다.
*   **개방창 근거(`openingWindow`)를 기록한다** (컬럼과 함께 `c6` 에서 들어간다). 중앙 모집기간(일), 상시채용 비율, 마감일 보유 표본 수, 기준 모수의 중앙 모집기간이다. 지수 자체는 `opening_window_index` 컬럼이 갖고 여기에는 근거만 둔다. 일수와 비율이라 §1.1 암호화 경로를 열지 않는다.
*   **원천별 커버리지(`sourceCoverages`)와 스킬별 기여도(`skillContributions`)를 함께 기록한다.** 완화 단계·표본 수·결측률과 스킬별 가중치×수요 지수는 계산 시점의 프로필에 의존해 나중에 재계산하면 같은 값이 안 나온다. 저장하지 않으면 그 달의 근거가 영구히 사라지고 소급도 불가능하다. 둘 다 금액이 아니라 §1.1 암호화 경로를 열지 않는다.
*   **월 행 생성 범위.** CPI **최신월** 결측(발행 지연)은 그 월의 스냅샷 행을 만들지 않는다 — 화면은 마지막 계산월과 `업데이트 지연`을 표시한다([기능 명세 §4.3](functional-specification.md)). 그 외의 산출 불가는 행을 만들고 지수 컬럼을 `NULL`, `decision_signal`을 `INSUFFICIENT_DATA`로 기록한다. 모든 산출 불가를 행 스킵으로 처리하면 §2.2 우선순위 1이 저장될 자리를 잃는다.
*   **재계산 트리거는 트랜잭션 안의 동기 직접 호출이다.** 호출 대상은 `SnapshotRecalculationService.recalculateSnapshot(UUID userId, String fromYearMonth)` 이고 호출부는 소득 3경로·소비 1·스킬 3·프로필 1 이다. 스케줄러나 비동기 이벤트는 쓰지 않는다 — 스레드풀·재시도·실패 관측 경로를 늘리지 않기 위해서이며, 멱등성은 `pg_advisory_xact_lock` 과 `data_version` 이 이미 보증한다. 응답 지연이 실측으로 문제가 되면 그때 비동기로 옮긴다.
*   **`fromYm` 규약은 `com.carix.snapshot.YearMonths` 한 곳에 있다.** 값에 유효월이 있으면(소득·소비) 그 달, 없으면(스킬·프로필) 변경 시점의 달이다. 유효월을 앞으로 당기는 수정은 옛 달부터 다시 계산한다. 한 번의 호출이 그 월부터 현재월까지를 하나의 `data_version` 으로 다시 쓴다.
*   **스냅샷 행이 없는 것은 오류가 아니다.** 온보딩 직후나 재계산이 한 번도 돌지 않은 사용자는 행이 없는 것이 정상이고, 조회는 빈 상태를 돌려준다 — 0 이나 100 으로 채우지 않는다.
*   **운영 첫 쓰기에 세 가지가 동시에 처음 실행된다** — `@JdbcTypeCode(SqlTypes.JSON)` 의 `jsonb` 바인딩, `pg_advisory_xact_lock`, `DataVersionIssuer` 의 `MANDATORY` 전파. 통합 테스트는 H2 라 앞의 둘을 검증하지 못한다(자문 잠금이 없어 `H2DataVersionIssuerConfiguration` 으로 대체된다). 운영 `ddl-auto` 가 `none` 이라 매핑 오류는 기동이 아니라 **첫 쓰기 시점**에 터지고, **이 경로는 배치가 아니라 소득·소비·스킬·프로필 저장에 동기로 걸려** 깨지면 사용자 쓰기가 통째로 실패한다. 운영 DB 에서 한 사용자에 `recalculateSnapshot` 을 한 번 돌리면 셋이 함께 확인된다 — 실행 시점은 [문서 맵 「배포 전 실행 순서」](../README.md#남은-것)가 정한다(암호화 키 백업 다음, 세션 게이트 플래그 전환 앞).
*   **`rationale` 형상은 운영 행이 쌓이기 전까지만 자유롭다.** `jsonb` 라 지금은 DDL 변경 없이 필드를 더하거나 바꿀 수 있지만, 첫 쓰기 뒤의 같은 변경에는 마이그레이션이 붙는다.
*   `rule_version`은 결정표 배포 시 수동 증가시키는 semver다. 비교는 사전순이 아니라 `major.minor.patch` 숫자 세그먼트 비교로 정의한다 (`1.10.0` > `1.9.0`).

---

## 2. 결정 신호(Decision Signal) 판정 매트릭스

결정 엔진은 아래 **소득 격차 비율(SalaryGapRatio)**, **시장가치 지수(MVI)**, **구매력 지수(PPI)** 및 **통계 신뢰성 임계값**을 조합하여 신호를 산출합니다.

### 2.1 주요 변수 정의
*   **소득 격차 비율 ($SalaryGapRatio$):**
    $$SalaryGapRatio = \frac{\text{공식 임금통계 기준 연봉 중앙값} - \text{현재 사용자 연봉}}{\text{현재 사용자 연봉}}$$
    *(사용자 연봉이 기준 중앙값보다 낮을 때 양수가 됨. 기준선은 `wage_stat_observation`의 직종×경력구간×지역 중앙값을 사용하며, 공고 공개연봉은 결측률이 높아 참고 표시 전용)*
*   **현재 사용자 연봉:** `core.income_history`의 암호화 컬럼이 원천이며, 기준월 시점에 유효한(가장 늦은 `effective_date` $\le$ 기준월 말일) 세전 연봉이다. 결정 엔진은 이 컬럼을 직접 읽지 않고 `CurrentSalaryProvider`(구현체: `IncomeCurrentSalaryProvider`) 계약으로 받는다 — 복호화 경계를 소득 도메인 안에 둔다([API 규격 §5.1](api-specification.md)).
*   **시장가치 지수 ($MVI_{recent}$):** 스킬 수요율 변화량 기준 (100점 기준값, 구현체: `MarketValueIndexServiceImpl`)
*   **구매력 지수 ($PPI$):** 실질 구매력 수치 (100점 기준값, 구현체: `DefaultPurchasingPowerIndexProvider`). 품목별 CPI 연동 전까지 소득 등록 사용자의 최초 소득월(=100.0) 대비 소득 변동 비율을 기반으로 지수를 도출하여 스냅샷 결측을 방지한다.
*   **최소 표본 조건 ($MinSampleSize$):** 유효 공고 수 $\ge 10$개 (확정값). OCI 운영 DB 실측 결과 4,356건의 공고와 12,568행의 사전 집계가 적재되어 있어 동료집단 셀(예: DEVELOPER 직무 100~200건 이상)에서 기준을 충족한다. **적용 지점:** 원천별 × 스킬별 × 기간 창별(최근 3개월 / 이전 12개월 각각)로 판정한다. 기준을 통과한 원천만 지수 결합에 포함하고, 모든 원천이 미달인 스킬은 `표본 부족`으로 제외한다. 연봉 분포·전체 공고 수(스킬 무관 행)도 동일 기준을 따른다.

### 2.2 판정 우선순위 및 규칙표

| 우선순위 | 타겟 조건 (모두 AND 충족 시) | 결정 신호 | 화면에 표시될 근거 예시 |
| :---: | :--- | :--- | :--- |
| **1** | 데이터 부족 조건 충족 (아래 정의) | **데이터 부족** | "동료집단의 표본 수(N개)가 통계 기준치보다 부족하여 신호를 판단할 수 없습니다." |
| **2** | $SalaryGapRatio \ge 0.15$ <br>AND $MVI_{recent} \ge 110$ | **이직 탐색** | "공식 임금통계 중앙값 대비 연봉 격차가 15% 이상이고, 보유 스킬의 시장 수요가 10% 이상 상승했습니다. 적극적인 이직 탐색을 권장합니다." |
| **3** | $0.05 \le SalaryGapRatio < 0.15$ <br>AND $MVI_{recent} \ge 105$ | **협상 검토** | "연봉 격차가 5~15% 구간이며, 스킬 가치는 상승세입니다. 연봉 협상 테이블에서의 기대를 높이거나 협상 카드를 검토해 보세요." |
| **4** | $MVI_{recent} < 90$ <br>AND $PPI < 95$ | **스킬 보강 우선** | "실질 구매력 지수와 함께 보유 스킬의 채용 수요도 최근 감소세입니다. 연봉협상이나 이직보다는 대체 스킬 학습을 권장합니다." |
| **5** | $-0.05 < SalaryGapRatio < 0.05$ <br>AND $MVI_{recent} \ge 90$ | **현 상태 유지** | "현재 보상이 시장 수준에 근접해 있고 스킬 수요 역시 비교적 안정적입니다. 현 직장에서의 역량 집중이 유리한 시점입니다." |
| **기본** | 위 어느 조건에도 해당하지 않을 때 | **현 상태 유지** | "시장에 뚜렷한 보상 격차나 급격한 가치 상승 신호가 포착되지 않아 현 상태 유지를 추천합니다." |

**`데이터 부족` 발동 조건 (우선순위 1):** 다음 중 하나라도 해당할 때만 발동한다.
1. 완화 Level 5까지 적용한 최종 동료집단의 전체 공고 수(전 원천 합산)가 $N < 10$
2. 표본 기준을 통과한 계산 가능 스킬이 0개 (MVI 산출 불가)
3. CPI 누락으로 구매력 지수 산출 불가 (최신월 발행 지연은 여기 해당하지 않는다 — §1.3의 월 행 생성 범위를 따라 그 월은 행을 만들지 않는다)
4. 연봉 격차 기준선(임금통계) 미확보
5. 현재 사용자 연봉 미확보로 소득 격차 비율 산출 불가 (조건 4의 반대편이다 — 분자·분모 중 하나만 없어도 규칙 2·3·5를 평가할 수 없다)

개별 스킬의 표본 미달·단일 원천 사용은 신호를 `데이터 부족`으로 바꾸지 않고 화면 표시(제외 목록, `단일 원천` 라벨)로만 처리한다.

---

## 3. 표준 스킬 사전 및 에일리어싱(Aliasing) 정책

비정형 채용 공고 텍스트에서 스킬을 균일하게 정규화하여 시장 수요를 매핑하기 위한 Seed 데이터와 가공 규칙입니다.

### 3.1 표준 스킬 사전

**시드 정본은 [`deploy/postgres/schema/05_standard_skills_seed.sql`](../../deploy/postgres/schema/05_standard_skills_seed.sql)이다** — 100+ 스킬과 별칭이 거기 있고, 여기 목록을 두면 사본이 갈린다.

분류 체계만 여기 둔다. `core.skill.category` 는 `BACKEND`·`FRONTEND`·`MOBILE`·`DEVOPS`·`DATABASE`·`AI_ML`·`DATA_ENG`·`TOOLS` 여덟이다.

> **`skill.category` 의 실물은 이 여덟이 아닐 수 있다.** §1.2 의 차이표가 적듯 수집 태그의
> 성격 구분(`SKILL`·`JOB_CATEGORY`·`INDUSTRY`)이 같은 컬럼에 들어와 있다. 시드가 쓰는 값과
> 크롤러가 쓰는 값이 한 컬럼을 공유한다.

### 3.2 에일리어싱(Aliasing) 매칭 알고리즘

무경계 부분 문자열(Contains) 매칭은 짧은 별칭(`go`, `ts`, `gpt`)이 무관한 단어(`django` 등)에 오탐되므로 사용하지 않는다. **토큰 경계 기반 매칭**을 사용한다.

1.  **텍스트 전처리:** 상세 요강에서 소문자 통일 후, 영숫자 경계 기준으로 토큰 분리. 인접 토큰은 별칭 매칭을 위해 최대 3-토큰 시퀀스로 결합해 검사.
    *   *예: "Spring Boot 개발 경험" ➡️ 토큰 `[spring, boot, 개발, 경험]`, 시퀀스 `spring boot` 생성*
2.  **검색 매칭:** 별칭도 원문과 **동일한 전처리**(소문자화 + 영숫자 경계 토큰 분리)를 거쳐 토큰 시퀀스로 저장한다 (`node.js` ➡️ `[node, js]`, `react.js` ➡️ `[react, js]`). 매칭은 별칭 토큰 시퀀스와 원문 토큰 시퀀스의 **완전 일치**로 판정. `django` 안의 `go`는 토큰이 아니므로 매칭되지 않는다.
3.  **충돌 정책:** 3글자 이하 영문 별칭(`go`, `ts` 등)은 단독 토큰 완전 일치만 허용한다. `spring` 단독 토큰은 `Spring Boot`가 아닌 별도 표기 관측으로 두고, `spring boot` 시퀀스만 `Spring Boot`로 매핑한다. 한글 별칭은 어절 단위 일치를 기본으로 한다.
4.  선행 검증에서 표본 공고로 정밀도·재현율을 측정해 별칭 사전과 규칙을 보정한다.
