# Carix 상세 기술 명세서 (Detailed Technical Specifications)

이 문서는 Carix 1차 MVP 구현에 필요한 데이터베이스 스키마(DDL), 보안 암호화 정책, 결정 신호 룰 엔진 매트릭스, 표준 스킬 사전 시드(Seed) 데이터 및 외부 OpenAPI 연동 스펙을 정의합니다.

> **계보 주의 (2026-07-25 / 2026-08-08):** 아래 §4의 고용24 OpenAPI 연동 스펙은
> 실측으로 폐기가 확정됐다(개인회원 API 호출 차단). 사람인도 공식 오픈 API가 아니라
> 스크래핑으로 전환됐다. 최신 원천 구성·연동 방식은
> [외부 원천 API 연동 명세](external-api-integration.md)를 따른다. §1의 스키마 예시에
> 나오는 `source` 값 `'WORK24'`도 실제 구현에서는 쓰이지 않는다 — 실제 값은
> `'JOBKOREA'`/`'SARAMIN'`이다.

---

## 1. 데이터베이스 스키마 및 보안 암호화 정책

데이터베이스는 **PostgreSQL**을 사용하며, 모든 테이블 구조와 민감자산 정보(연봉, 소비금액)의 암호화 필드 정책을 다음과 같이 정의합니다.

### 1.1 컬럼 레벨 암호화 (Column-level Encryption) Policy
*   **대상 컬럼:** `income_history.gross_salary_encrypted`, `income_history.net_salary_encrypted`, `expense_item.amount_encrypted`
*   **알고리즘:** **AES-256-GCM** (또는 보안 강도가 검증된 AES-256-CBC)
*   **구현 방식:** 애플리케이션 레이어(Spring Boot)에서 **Spring Data JPA Converter**(`AttributeConverter`)를 구현하여 DB 적재 시 자동 암호화하고, 조회 시 복호화합니다. 평문 데이터는 DB 버퍼나 파일시스템에 그대로 기록되지 않도록 차단합니다.

### 1.2 DDL 스펙

> **실행 정본은 [`deploy/postgres/schema/`](../../deploy/postgres/schema/)의 `.sql` 파일이다.**
> 아래 DDL은 설계 의도를 서술한 것이고, 실제 적용은 그 파일들로 한다
> (절차는 [DEVELOPMENT.md §3.2](../DEVELOPMENT.md)). 아래와 실물이 다른 지점이 있으니
> 새 테이블을 설계할 때 참고한다.
>
> **`users` 외래키는 걸지 않는다.** `users`는 auth-service가 `auth` 스키마에 소유하고,
> `core` 테이블은 core-api가 소유한다. 스키마를 가로지르는 외래키는 두 서비스의 배포를
> 묶어 버리므로 `user_id UUID NOT NULL`만 두고 참조 무결성은 애플리케이션이 지킨다.
> 아래 `REFERENCES users(id)`는 서비스 분리 이전에 쓰인 표기다.
>
> **채용공고 수집 5개 테이블은 아래 서술과 다르다.** 크롤링 실측에 맞춰 완화된 상태로
> 운영 중이며, 정확한 정의는 [`01_job_posting.sql`](../../deploy/postgres/schema/01_job_posting.sql)에 있다.
>
> | 테이블 | 아래 서술 | 실제 |
> | --- | --- | --- |
> | `job_posting` | `title VARCHAR(255)`, `company_name`·`region`·`experience_min`·`employment_type`·`company_size`·`job_family` 전부 NOT NULL | `title VARCHAR(500)`, 해당 컬럼 전부 NULL 허용. `company_badge`·`position_level` 컬럼 추가 |
> | `job_posting.salary_*` | 원 단위 | **만원 단위** (원천 표기 그대로) |
> | `skill` | `standard_name VARCHAR(100)` 단독 UNIQUE, `category NOT NULL` | `VARCHAR(255)`, `UNIQUE (standard_name, category)`, `category` NULL 허용 |
> | `skill.category` | `'BACKEND'`·`'FRONTEND'` 등 기술 분야 | `'SKILL'`·`'JOB_CATEGORY'`·`'INDUSTRY'` — 수집 태그의 성격 구분 |
> | `skill_alias` | `alias_name VARCHAR(100) UNIQUE` | `VARCHAR(255)`, UNIQUE 없음 (미사용, 0행) |
> | `raw_ingest` | `payload` NULL 허용(원문 purge), `schema_version NOT NULL` | `payload NOT NULL`, `schema_version` NULL 허용 |

```sql
-- 1. 인증 및 사용자 영역
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    onboarding_step VARCHAR(20) NOT NULL DEFAULT 'PROFILE', -- 'PROFILE', 'INCOME', 'EXPENSE', 'SKILLS', 'REVIEW', 'COMPLETED'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invitation (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL, -- 'PENDING', 'ACCEPTED', 'EXPIRED'
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 사용자 삭제 시 동일 이메일의 invitation 행도 애플리케이션 레벨에서 함께 삭제한다 (개인 데이터 잔존 방지).

CREATE TABLE magic_link_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);
-- 사용자 생성 시점: 초대된 이메일이 매직링크를 처음 요청하는 시점에 users가 없으면 생성한다.
-- 토큰은 항상 user_id에 바인딩되며, 초대되지 않은 이메일은 사용자·토큰을 만들지 않고 동일 응답만 반환한다.

CREATE TABLE session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rate_limit_bucket (
    id UUID PRIMARY KEY,
    bucket_key VARCHAR(255) NOT NULL, -- '{scope}:{key}' 형식, 예: 'email:user@x.com', 'ip:1.2.3.4'
    window_start TIMESTAMP WITH TIME ZONE NOT NULL, -- 시 단위로 truncate된 고정 윈도 시작 시각
    request_count INT NOT NULL DEFAULT 1,
    UNIQUE (bucket_key, window_start)
);
-- 매직링크 요청 속도 제한(SECURITY.md §2: 이메일당 5회/시간, IP당 20회/시간)에 사용하는 고정 윈도 카운터.

-- 2. 사용자 프로필 및 이력 데이터 영역
CREATE TABLE career_profile (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    job_family VARCHAR(100), -- 'DEVELOPER', 'DATA', 'AI'
    years_of_experience INT,
    region VARCHAR(100), -- 예: 'SEOUL_GANGNAM', 'GYEONGGI' 등 코드화
    employment_type VARCHAR(50), -- 'FULL_TIME', 'CONTRACT'
    company_size VARCHAR(50) -- 'LARGE', 'MEDIUM', 'STARTUP' (선택값)
);
-- 온보딩 자동 저장(부분 필드)을 허용하기 위해 컬럼은 NULL 허용이다.
-- 필수 필드(job_family, years_of_experience, region, employment_type)의 완비 검증은
-- 온보딩 완료 제출(REVIEW → COMPLETED) 시 애플리케이션 레벨에서 수행한다.

CREATE TABLE income_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gross_salary_encrypted VARCHAR(512) NOT NULL, -- 세전연봉 (암호화)
    net_salary_encrypted VARCHAR(512),            -- 월 실수령액 (선택적, 암호화)
    effective_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, effective_date)
);

CREATE TABLE expense_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    base_year_month VARCHAR(6) NOT NULL, -- 'YYYYMM' 형식
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, base_year_month)
);

CREATE TABLE expense_item (
    id UUID PRIMARY KEY,
    expense_history_id UUID NOT NULL REFERENCES expense_history(id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL, -- 'HOUSING', 'FOOD', 'TRANSPORT', 'MEDICAL', 'EDUCATION', 'LEISURE'
    amount_encrypted VARCHAR(512) NOT NULL, -- 월 소비액 (암호화)
    UNIQUE (expense_history_id, category)
);
-- 카테고리별 상태(USER_DEFINED/DEFAULT_CPI)는 저장하지 않는다. 사용자가 입력한 카테고리만 행으로 존재하며,
-- 상태·보간값·월 요약 라벨은 계산 시 행 존재 여부로부터 도출한다 (도메인 정의서 §1.1).

-- 3. 스킬 및 사용자 스킬 데이터 영역
CREATE TABLE skill (
    id UUID PRIMARY KEY,
    standard_name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL, -- 'BACKEND', 'FRONTEND', 'DEVOPS', 'DATA', 'AI_ML', 'ETC' (커스텀 스킬은 생성 시 필수 입력)
    is_custom BOOLEAN NOT NULL DEFAULT false, -- 사용자 정의 스킬 여부
    created_by UUID REFERENCES users(id) ON DELETE SET NULL -- 사용자 정의 스킬 생성자 (표준 스킬은 NULL)
);
-- 사용자 정의 스킬(is_custom = true)은 시장 집계·지수 계산에서 제외하고 `통계 미제공`으로 표시한다.
-- 커스텀 스킬 정리: 해당 스킬을 참조하는 마지막 user_skill 삭제 또는 생성자 사용자 삭제 시,
-- 애플리케이션 레벨 트랜잭션으로 skill 행을 함께 삭제한다. created_by의 ON DELETE SET NULL은
-- 커스텀 스킬이 표준 사전으로 승격(is_custom = false)된 경우에만 의미를 가진다.

CREATE TABLE skill_alias (
    id UUID PRIMARY KEY,
    skill_id UUID NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    alias_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE user_skill (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    months_of_experience INT NOT NULL CHECK (months_of_experience BETWEEN 1 AND 600),
    last_used_date DATE NOT NULL,
    UNIQUE (user_id, skill_id)
);

-- 4. 외부 수집 및 집계 영역
CREATE TABLE raw_ingest (
    id UUID PRIMARY KEY,
    source VARCHAR(50) NOT NULL, -- 'KOSIS_CPI', 'WORK24', 'SARAMIN', 'WAGE_STAT'
    source_item_id VARCHAR(100) NOT NULL, -- 원천 내 원문 식별자
    payload JSONB, -- 원문 응답. 보존 24개월 초과 시 NULL 처리(원문만 삭제), 행·메타데이터는 유지
    purged_at TIMESTAMP WITH TIME ZONE, -- 원문 삭제 시각 (NULL = 원문 보존 중)
    schema_version VARCHAR(20) NOT NULL,
    first_seen_month VARCHAR(6) NOT NULL,
    last_seen_month VARCHAR(6) NOT NULL,
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source, source_item_id)
);

CREATE TABLE cpi_observation (
    id UUID PRIMARY KEY,
    base_year_month VARCHAR(6) NOT NULL,
    category VARCHAR(50) NOT NULL,
    cpi_value DECIMAL(5,2) NOT NULL,
    UNIQUE (base_year_month, category)
);

CREATE TABLE wage_stat_observation (
    id UUID PRIMARY KEY,
    job_family VARCHAR(100) NOT NULL,
    experience_bracket VARCHAR(50) NOT NULL,
    region VARCHAR(100) NOT NULL,
    wage_definition VARCHAR(100) NOT NULL, -- 임금 정의 (초과급여·상여 포함 여부)
    wage_median BIGINT NOT NULL, -- 연간 세전 중앙값 (원)
    base_period VARCHAR(6) NOT NULL, -- 통계 기준기간 'YYYYMM'
    stat_vintage VARCHAR(6) NOT NULL, -- 발표 시점 (통계 개정 구분)
    UNIQUE (job_family, experience_bracket, region, base_period, stat_vintage)
);
-- 같은 기준기간의 개정판은 stat_vintage로 공존한다. 조회는 base_period별 최신 stat_vintage를 사용한다.

CREATE TABLE job_posting (
    id UUID PRIMARY KEY,
    source VARCHAR(50) NOT NULL, -- 'WORK24', 'SARAMIN'
    external_id VARCHAR(100) NOT NULL, -- 원천 공고 ID (고용24 wantedAuthNo 등)
    dedup_group_id UUID, -- 원천 간 중복 공고 그룹 (회사명+제목+게시기간 휴리스틱)
    dedup_rule_version VARCHAR(20), -- 중복 매칭 규칙 버전
    title VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    salary_min BIGINT, -- 정규화된 연간 세전최소 (원 단위, 결측 가능)
    salary_max BIGINT, -- 정규화된 연간 세전최대 (원 단위, 결측 가능)
    region VARCHAR(100) NOT NULL,
    experience_min INT NOT NULL DEFAULT 0,
    experience_max INT DEFAULT 99,
    employment_type VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN', -- 'FULL_TIME', 'CONTRACT', 원천 미상은 'UNKNOWN'
    company_size VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN', -- 'LARGE', 'MEDIUM', 'STARTUP', 원천 미상은 'UNKNOWN'
    job_family VARCHAR(100) NOT NULL,
    content TEXT, -- 공고 상세 본문 텍스트 (스킬 파싱용)
    posted_start_date DATE, -- 게시 시작일 (dedup 휴리스틱·월 귀속용)
    posted_end_date DATE, -- 게시 종료일 (결측 시 last_seen 기반 추정)
    first_seen_month VARCHAR(6) NOT NULL, -- 수집에서 처음 관측된 월
    last_seen_month VARCHAR(6) NOT NULL, -- 마지막 관측 월
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source, external_id)
);
-- 월별 집계 귀속: 게시기간(posted_start_date~posted_end_date)이 해당 월과 겹치는 공고를 그 월에 귀속한다.
-- 게시일 결측 시 first_seen_month~last_seen_month로 대체한다. 두 달에 걸친 공고는 각 월에 1회씩 귀속되고 월 내 이중 집계는 없다.

CREATE TABLE job_posting_skill (
    id UUID PRIMARY KEY,
    job_posting_id UUID NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    UNIQUE (job_posting_id, skill_id)
);

CREATE TABLE market_aggregate (
    id UUID PRIMARY KEY,
    source VARCHAR(50) NOT NULL, -- 'WORK24', 'SARAMIN' (원천별 행만 저장, 통합 지수는 계산 시점 도출)
    base_year_month VARCHAR(6) NOT NULL,
    job_family VARCHAR(100) NOT NULL,
    region VARCHAR(100) NOT NULL,
    experience_bracket VARCHAR(50) NOT NULL, -- 'JUNIOR'(0~3), 'MID'(4~7), 'SENIOR'(8+)
    employment_type VARCHAR(50), -- NULL = 전체 롤업 (완화 Level 5 대응). 원천 미상 공고는 'UNKNOWN' 버킷으로 구분 — NULL과 혼용 금지
    company_size VARCHAR(50), -- NULL = 전체 롤업 (완화 Level 2 대응). 원천 미상은 'UNKNOWN' 버킷
    skill_id UUID REFERENCES skill(id) ON DELETE CASCADE, -- NULL = 스킬 무관 동료집단 전체 행 (연봉 분포·전체 공고 수 용)
    posting_count INT NOT NULL DEFAULT 0,
    total_aggregate_postings INT NOT NULL DEFAULT 0, -- 비교군 전체 공고 수
    demand_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000, -- 수요율 (posting_count / total_aggregate_postings)
    salary_min BIGINT, -- 해당 스킬 공고의 공개연봉 분포 (참고 병기용)
    salary_p25 BIGINT,
    salary_median BIGINT,
    salary_p75 BIGINT,
    salary_max BIGINT,
    salary_missing_rate DECIMAL(5,2) NOT NULL DEFAULT 100.00, -- 연봉 결측률 (%)
    UNIQUE NULLS NOT DISTINCT (source, base_year_month, job_family, region, experience_bracket, employment_type, company_size, skill_id)
);
-- NULLS NOT DISTINCT (PostgreSQL 15+): NULL 차원(완화 그룹·스킬 무관 행)을 동일 값으로 취급해
-- 재실행 시 upsert 멱등성을 보장한다. NULL을 구분하는 일반 UNIQUE로는 완화 그룹 행이 중복 적재된다.
-- 공고 공개연봉 분포는 참고 표시 전용이다. 연봉 격차 판정 기준선은 wage_stat_observation의 공식 임금통계 중앙값을 사용한다.

-- 5. 지수 스냅샷 및 시나리오 영역
CREATE TABLE index_snapshot (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    base_year_month VARCHAR(6) NOT NULL, -- 'YYYYMM'
    data_version VARCHAR(50) NOT NULL,   -- 사용자 입력 + 외부 데이터 상태를 식별하는 버전
    rule_version VARCHAR(50) NOT NULL,   -- 결정 신호 규칙표 버전
    purchasing_power_index DECIMAL(6,2), -- 계산 불가 시 NULL (표본 부족 등)
    market_value_index DECIMAL(6,2),
    decision_signal VARCHAR(50) NOT NULL, -- 'NEGOTIATE', 'SEEK_JOB', 'STAY', 'UPSKILL', 'INSUFFICIENT_DATA'
    rationale JSONB NOT NULL, -- 적용 규칙, 근거, 제외 데이터 사유, 원천 기준일
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, base_year_month, data_version, rule_version)
);

CREATE TABLE career_scenario (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stay_expected_raise_rate DECIMAL(5,2) NOT NULL,
    move_target_gross_salary BIGINT NOT NULL,
    move_one_time_cost BIGINT NOT NULL,
    expected_unemployment_months INT NOT NULL DEFAULT 0, -- 0~6
    is_eligible_for_unemployment_benefit BOOLEAN NOT NULL DEFAULT true, -- 실업급여 수급 요건 충족 가정 (false면 급여 0으로 계산)
    stay_compensation_12m BIGINT NOT NULL,
    move_compensation_12m BIGINT NOT NULL,
    estimated_unemployment_benefit BIGINT NOT NULL DEFAULT 0,
    difference BIGINT NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 1.3 스냅샷 갱신 의미론 (Snapshot Replacement Semantics)

*   동일 `(user_id, base_year_month, data_version, rule_version)` 키의 재실행은 기존 행을 대체(upsert)하며 중복 행을 만들지 않는다.
*   사용자 입력 변경(과거 소득·소비·스킬 수정 포함) 시 새 `data_version`을 발급하고, 변경의 가장 이른 유효월부터 현재까지의 스냅샷을 새 버전으로 재계산해 저장한다.
*   조회의 짝 선택 순서: 월별로 먼저 최신 `data_version`을 고르고, 그 안에서 최신 `rule_version`(숫자 세그먼트 비교)을 고른다. 규칙 버전만 배포된 경우 다음 정기 스냅샷 갱신 때 최신 데이터 버전으로 재계산되어 반영된다. 이전 버전 행은 근거 추적을 위해 보존한다.
*   시나리오는 사용자당 최근 1건을 유효 시나리오로 취급하며, `GET /api/v1/scenarios/latest`는 `calculated_at DESC, id DESC` 순 첫 행을 반환한다 (동일 시각 타이브레이커: `id`).
*   `data_version`은 **스냅샷 계산 실행 단위**로 발급한다: 사용자 입력 변경에 따른 재계산과 배치 정기 재계산(외부 데이터 갱신 — CPI, 공고 집계, 임금통계 vintage, dedup 규칙 변경 포함) 모두 새 버전을 발급받는다. 형식은 발급 시각 기반 `YYYYMMDDHHMMSS-NNN`(UTC, `NNN`은 같은 초 내 충돌 시 0부터 증가하는 3자리 시퀀스)이며, 발급은 사용자 단위 직렬화(행 잠금)로 유일성과 단조 증가를 보장한다. "최신 버전" 판정은 사전순 비교를 따른다. 스냅샷 `rationale`에 계산에 사용된 원천 기준일·배치 실행 ID를 기록해 외부 데이터 상태를 재현 가능하게 한다.
*   `rule_version`은 결정표 배포 시 수동 증가시키는 semver다. 비교는 사전순이 아니라 `major.minor.patch` 숫자 세그먼트 비교로 정의한다 (`1.10.0` > `1.9.0`).

---

## 2. 결정 신호(Decision Signal) 판정 매트릭스

결정 엔진은 아래 **소득 격차 비율(SalaryGapRatio)**, **시장가치 지수(MVI)**, **구매력 지수(PPI)** 및 **통계 신뢰성 임계값**을 조합하여 신호를 산출합니다.

### 2.1 주요 변수 정의
*   **소득 격차 비율 ($SalaryGapRatio$):**
    $$SalaryGapRatio = \frac{\text{공식 임금통계 기준 연봉 중앙값} - \text{현재 사용자 연봉}}{\text{현재 사용자 연봉}}$$
    *(사용자 연봉이 기준 중앙값보다 낮을 때 양수가 됨. 기준선은 `wage_stat_observation`의 직종×경력구간×지역 중앙값을 사용하며, 공고 공개연봉은 결측률이 높아 참고 표시 전용)*
*   **시장가치 지수 ($MVI_{recent}$):** 스킬 수요율 변화량 기준 (100점 기준값)
*   **구매력 지수 ($PPI$):** 실질 구매력 수치 (100점 기준값)
*   **최소 표본 조건 ($MinSampleSize$):** 유효 공고 수 $\ge 10$개 (후보값). **적용 지점:** 원천별 × 스킬별 × 기간 창별(최근 3개월 / 이전 12개월 각각)로 판정한다. 기준을 통과한 원천만 지수 결합에 포함하고, 모든 원천이 미달인 스킬은 `표본 부족`으로 제외한다. 연봉 분포·전체 공고 수(스킬 무관 행)도 동일 기준을 따른다.

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
3. CPI 누락으로 구매력 지수 산출 불가
4. 연봉 격차 기준선(임금통계) 미확보

개별 스킬의 표본 미달·단일 원천 사용은 신호를 `데이터 부족`으로 바꾸지 않고 화면 표시(제외 목록, `단일 원천` 라벨)로만 처리한다.

---

## 3. 표준 스킬 사전 및 에일리어싱(Aliasing) 정책

비정형 채용 공고 텍스트에서 스킬을 균일하게 정규화하여 시장 수요를 매핑하기 위한 Seed 데이터와 가공 규칙입니다.

### 3.1 분야별 표준 스킬 Seed 리스트 (개발자/데이터/AI)

*   **백엔드(Backend):**
    *   `Java` (별칭: `java`, `자바`)
    *   `Spring Boot` (별칭: `spring boot`, `springboot`, `스프링 부트`, `스프링부트`) — 단독 `spring`·`스프링`은 §3.2 충돌 정책에 따라 `Spring Boot`로 매핑하지 않음
    *   `Python` (별칭: `python`, `파이썬`)
    *   `Go` (별칭: `go`, `golang`, `고랭`)
    *   `Node.js` (별칭: `node`, `nodejs`, `노드`)
*   **프론트엔드(Frontend):**
    *   `TypeScript` (별칭: `typescript`, `ts`, `타입스크립트`)
    *   `React` (별칭: `react`, `react.js`, `리액트`)
    *   `Next.js` (별칭: `nextjs`, `next.js`)
*   **데이터 & 인프라(Data & Infra):**
    *   `PostgreSQL` (별칭: `postgresql`, `postgres`)
    *   `MySQL` (별칭: `mysql`)
    *   `AWS` (별칭: `amazon web service`, `aws`)
    *   `Kubernetes` (별칭: `k8s`, `kubernetes`, `쿠버네티스`)
    *   `Apache Airflow` (별칭: `airflow`, `에어플로우`)
*   **인공지능 & 머신러닝(AI & ML):**
    *   `PyTorch` (별칭: `pytorch`, `파이토치`)
    *   `TensorFlow` (별칭: `tensorflow`, `텐서플로우`)
    *   `LLM` (별칭: `large language model`, `gpt`, `llm`, `거대언어모델`)

### 3.2 에일리어싱(Aliasing) 매칭 알고리즘

무경계 부분 문자열(Contains) 매칭은 짧은 별칭(`go`, `ts`, `gpt`)이 무관한 단어(`django` 등)에 오탐되므로 사용하지 않는다. **토큰 경계 기반 매칭**을 사용한다.

1.  **텍스트 전처리:** 상세 요강에서 소문자 통일 후, 영숫자 경계 기준으로 토큰 분리. 인접 토큰은 별칭 매칭을 위해 최대 3-토큰 시퀀스로 결합해 검사.
    *   *예: "Spring Boot 개발 경험" ➡️ 토큰 `[spring, boot, 개발, 경험]`, 시퀀스 `spring boot` 생성*
2.  **검색 매칭:** 별칭도 원문과 **동일한 전처리**(소문자화 + 영숫자 경계 토큰 분리)를 거쳐 토큰 시퀀스로 저장한다 (`node.js` ➡️ `[node, js]`, `react.js` ➡️ `[react, js]`). 매칭은 별칭 토큰 시퀀스와 원문 토큰 시퀀스의 **완전 일치**로 판정. `django` 안의 `go`는 토큰이 아니므로 매칭되지 않는다.
3.  **충돌 정책:** 3글자 이하 영문 별칭(`go`, `ts` 등)은 단독 토큰 완전 일치만 허용한다. `spring` 단독 토큰은 `Spring Boot`가 아닌 별도 표기 관측으로 두고, `spring boot` 시퀀스만 `Spring Boot`로 매핑한다. 한글 별칭은 어절 단위 일치를 기본으로 한다.
4.  선행 검증에서 표본 공고로 정밀도·재현율을 측정해 별칭 사전과 규칙을 보정한다.

---

## 4. KOSIS / 고용24 OpenAPI 연동 명세

### 4.1 KOSIS 소비자물가지수 API
*   **요청 EndPoint:** `http://kosis.kr/openapi/Param/statisticsParameterDP.do`
*   **HTTP Method:** `GET`
*   **필수 요청 파라미터:**

| 파라미터 | 값 (Carix 설정값) | 설명 |
| :--- | :--- | :--- |
| `method` | `getList` | 데이터 목록 조회 고정 |
| `apiKey` | `[발급받은 인증키]` | KOSIS 발급 키 |
| `orgId` | `101` | 통계청 기관 코드 |
| `tblId` | `DT_1J20003` | 지출목적별 소비자물가지수 테이블 ID |
| `format` | `json` | 응답 포맷 (JSON 고정) |
| `prdSe` | `M` | 기간 주기 (M: 월 단위) |
| `startPrdDe` | `YYYYMM` | 조회 시작 연월 |
| `endPrdDe` | `YYYYMM` | 조회 종료 연월 |

*   **주요 KOSIS 지출목적 분류 코드 매핑 (Carix 카테고리 매핑용):**
    *   `01` 식료품 및 비주류음료 ➡️ **식비**
    *   `04` 주택, 수도, 전기, 연료 ➡️ **주거·공공요금**
    *   `06` 보건 ➡️ **의료**
    *   `07` 교통 ➡️ **교통**
    *   `10` 교육 ➡️ **교육·자기계발**
    *   `09` 오락 및 문화 / `12` 기타 ➡️ **여가·기타**
    *   N:1 병합 카테고리(`09`+`12`)의 CPI 변화율은 KOSIS 공식 지출목적별 가중치를 사용한 **가중 평균**으로 결합한다. 병합에 사용한 가중치와 기준 시점은 설정 파일에 기록한다.

---

### 4.2 고용24 (구 워크넷) 채용정보 OpenAPI

> **주의:** 아래 엔드포인트·파라미터는 구 워크넷 문서 기준이다. 현행 고용24는
> `work24.go.kr/cm/openApi/call/wk/...` 계열 엔드포인트와 XML 고정 응답으로
> 이전되었으므로, 선행 검증에서 최신 공식 규격으로 이 절을 교체한다.

#### ① 채용 목록 API (기본 정보 수집)
*   **요청 EndPoint:** `http://openapi.work.go.kr/opi/opi/opia/wantedApi.do`
*   **HTTP Method:** `GET`
*   **요청 파라미터:**

| 파라미터 | 값 (Carix 설정값) | 설명 |
| :--- | :--- | :--- |
| `authKey` | `[발급받은 인증키]` | 고용24/워크넷 인증키 |
| `callTp` | `L` | 목록 조회 (List 고정) |
| `returnType` | `XML` (또는 JSON) | 기본 응답 포맷 |
| `startPage` | `1` | 페이지 인덱스 |
| `display` | `100` | 한 번에 가져올 공고 수 |
| `occupation` | `024` | SW/IT 개발자 직종 대분류 코드 |

#### ② 채용 상세 API (상세 요강 & 스킬 파싱용)
*   **요청 EndPoint:** 목록에서 취득한 공고 ID (`wantedAuthNo`)를 이용해 상세 본문 수집
*   **요청 파라미터:**

| 파라미터 | 값 (Carix 설정값) | 설명 |
| :--- | :--- | :--- |
| `authKey` | `[발급받은 인증키]` | 고용24/워크넷 인증키 |
| `callTp` | `D` | 상세 조회 (Detail 고정) |
| `wantedAuthNo` | `[공고 ID]` | 목록 API에서 추출한 공고 식별키 |
| `infoSvc` | `VALIDATION` | 상세 정보 수집 플래그 |

> 위 파라미터는 작성 시점 문서 기준이며, 구현 전 선행 검증에서 최신 공식 문서·샘플 응답으로 재확인한다.

---

### 4.3 사람인 오픈 API (스킬 수요 원천 2)

*   고용24의 개발직군 커버리지 부족을 보완하는 두 번째 공고 원천. IT 직군 공고 목록·상세를 수집해 `job_posting`에 `source = 'SARAMIN'`으로 적재하고, 원천 간 중복은 `dedup_group_id`로 매칭한다.
*   비상업 이용 조건, 호출 제한, 필드 규격은 구현 전 선행 검증에서 확인 후 이 절에 확정 기재한다. 검증 결과가 나쁘면 어댑터만 비활성화한다.

### 4.4 임금통계 API (연봉 격차 기준선)

*   임금직무정보시스템 또는 KOSIS 직종별 임금 통계를 수집해 `wage_stat_observation`에 적재한다. 직종×경력구간×지역 중앙값이 연봉 격차 판정의 기준선이다.
*   임금 정의(초과급여·상여 포함 여부)를 `wage_definition`에 기록하고 화면에 명시한다.
*   API 제공 여부와 필드 규격은 선행 검증에서 확인한다. 임금직무정보시스템 API 미제공 시 KOSIS 직종별 임금으로 대체한다.
