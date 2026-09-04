# Carix 상세 기술 명세서 (Detailed Technical Specifications)

이 문서는 Carix 1차 MVP 구현에 필요한 데이터베이스 스키마(DDL), 보안 암호화 정책, 결정 신호 룰 엔진 매트릭스, 표준 스킬 사전 시드(Seed) 데이터를 정의합니다.

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
> | `skill_alias` | `alias_name VARCHAR(100) UNIQUE` | `VARCHAR(255)`, UNIQUE 없음. 별칭 78개가 스킬 24개에 붙어 있으나 읽는 코드가 아직 없다 |
> | `raw_ingest` | 원문 보관 + 24개월 purge | **원문을 보관하지 않는다.** `payload`·`purged_at`·`schema_version` 컬럼 없음. 수집 이력만 남는다 |
> | `job_posting.content` | 공고 상세 본문 텍스트 | **컬럼 없음.** 스킬 파싱은 `raw_ingest` 원문에서 직접 한다 |

```sql
-- 1. 인증 및 사용자 영역
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,  -- BCrypt 60자. 알고리즘 교체 여지를 두고 100
    onboarding_step VARCHAR(20) NOT NULL DEFAULT 'PROFILE', -- 'PROFILE', 'INCOME', 'EXPENSE', 'SKILLS', 'REVIEW', 'COMPLETED'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_credentials_pair_chk
        CHECK ((email IS NULL AND password_hash IS NULL)
            OR (email IS NOT NULL AND password_hash IS NOT NULL))
);
-- 사용자 생성 시점: 회원가입 시 한 번. 계정 없이 만들어지는 사용자 행은 없다.
-- 이메일 소유를 증명하지 않으므로 확인 상태 컬럼도 두지 않는다 (확인 메일을 보낼 경로가 없다).

CREATE TABLE session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,  -- SHA-256 hex. 원본 토큰은 저장하지 않는다
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 세션은 30일짜리 자격증명이라 DB 유출 시 피해가 가장 크다. 해시만 저장하고,
-- 조회는 제시된 토큰을 해싱해 대조한다.
-- 만료 행은 일 1회 스케줄러가 정리한다 (auth-service 단일 인스턴스 전제).

CREATE TABLE rate_limit_bucket (
    id UUID PRIMARY KEY,
    bucket_key VARCHAR(255) NOT NULL, -- '{scope}:{key}' 형식, 예: 'email:user@x.com', 'ip:1.2.3.4'
    window_start TIMESTAMP WITH TIME ZONE NOT NULL, -- 시 단위로 truncate된 고정 윈도 시작 시각
    request_count INT NOT NULL DEFAULT 1,
    UNIQUE (bucket_key, window_start)
);
-- 가입·로그인 속도 제한(SECURITY.md §2)에 사용하는 고정 윈도 카운터.
-- 윈도가 지난 행은 세션 정리와 같은 스케줄러가 함께 지운다.

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
    source VARCHAR(50) NOT NULL, -- 'KOSIS_CPI', 'JOBKOREA', 'SARAMIN', 'WAGE_STAT'
    source_item_id VARCHAR(100) NOT NULL, -- 원천 내 원문 식별자
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
    wage_definition VARCHAR(100) NOT NULL, -- 임금 정의 메타데이터 ('ANNUAL_GROSS_MEDIAN': 연간 세전 중위임금)
    wage_median BIGINT NOT NULL, -- 연간 세전 중앙값 (원)
    base_period VARCHAR(6) NOT NULL, -- 통계 기준기간 'YYYYMM'
    stat_vintage VARCHAR(6) NOT NULL, -- 발표 시점 (통계 개정 구분)
    UNIQUE (job_family, experience_bracket, region, wage_definition, base_period, stat_vintage)
);
-- 같은 기준기간의 개정판은 stat_vintage로 공존한다. 조회는 base_period별 최신 stat_vintage를 사용한다.

CREATE TABLE job_posting (
    id UUID PRIMARY KEY,
    source VARCHAR(50) NOT NULL, -- 'JOBKOREA', 'SARAMIN'
    external_id VARCHAR(100) NOT NULL, -- 원천 공고 ID
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
    source VARCHAR(50) NOT NULL, -- 'JOBKOREA', 'SARAMIN' — 원천별 행만 저장, 통합 지수는 계산 시점 도출
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
*   보존된 이전 버전 행은 **회차 비교 조회의 원천**이기도 하다. 같은 `(user_id, base_year_month)`의 서로 다른 `data_version` 두 행을 비교하면 사용자 입력 변경이 지수·결정 신호에 미친 영향이 시간 경과 효과와 분리된다. 이 조회는 위의 짝 선택을 우회하므로 응답이 어느 회차인지 명시해야 하며, 화면은 비교 대상 회차를 현재 값으로 표시하지 않는다.
*   시나리오는 사용자당 최근 1건을 유효 시나리오로 취급하며, `GET /api/v1/scenarios/latest`는 `calculated_at DESC, id DESC` 순 첫 행을 반환한다 (동일 시각 타이브레이커: `id`).
*   `data_version`은 **스냅샷 계산 실행 단위**로 발급한다: 사용자 입력 변경에 따른 재계산과 배치 정기 재계산(외부 데이터 갱신 — CPI, 공고 집계, 임금통계 vintage, dedup 규칙 변경 포함) 모두 새 버전을 발급받는다. 형식은 발급 시각 기반 `YYYYMMDDHHMMSS-NNN`(UTC, `NNN`은 같은 초 내 충돌 시 0부터 증가하는 3자리 시퀀스)이며, 발급은 사용자 단위 직렬화로 유일성과 단조 증가를 보장한다 — 잠글 행을 전제하지 않는 `pg_advisory_xact_lock(hashtextextended(user_id::text, 0))`을 쓴다. 온보딩 중인 사용자는 `career_profile` 행이 아직 없을 수 있고, 해시 충돌은 무관한 두 사용자를 직렬화할 뿐 정합성을 해치지 않는다. "최신 버전" 판정은 사전순 비교를 따른다. 스냅샷 `rationale`에 계산에 사용된 원천 기준일·배치 실행 ID를 기록해 외부 데이터 상태를 재현 가능하게 한다.
*   **`rationale`에 금액을 쓰지 않는다.** 이 컬럼은 평문 JSONB이고 연봉은 `income_history`에서 AES-256-GCM으로 보호된다(§1.1). 같은 값을 여기 적으면 컬럼 암호화가 무력화된다. 비율·임계값 문구·원천 기준일·배치 실행 ID만 기록한다. **소득 격차 비율과 임금통계 중앙값을 함께 적는 것도 금지한다** — $연봉 = 중앙값 / (1 + 격차비율)$ 로 정확히 복원되므로 둘 중 하나만 남긴다. 화면이 금액이 포함된 문장을 보여줘야 하면 프런트엔드가 보유한 값으로 조립한다.
*   **월 행 생성 범위.** CPI **최신월** 결측(발행 지연)은 그 월의 스냅샷 행을 만들지 않는다 — 화면은 마지막 계산월과 `업데이트 지연`을 표시한다([기능 명세 §4.3](functional-specification.md)). 그 외의 산출 불가는 행을 만들고 지수 컬럼을 `NULL`, `decision_signal`을 `INSUFFICIENT_DATA`로 기록한다. 모든 산출 불가를 행 스킵으로 처리하면 §2.2 우선순위 1이 저장될 자리를 잃는다.
*   `rule_version`은 결정표 배포 시 수동 증가시키는 semver다. 비교는 사전순이 아니라 `major.minor.patch` 숫자 세그먼트 비교로 정의한다 (`1.10.0` > `1.9.0`).

---

## 2. 결정 신호(Decision Signal) 판정 매트릭스

결정 엔진은 아래 **소득 격차 비율(SalaryGapRatio)**, **시장가치 지수(MVI)**, **구매력 지수(PPI)** 및 **통계 신뢰성 임계값**을 조합하여 신호를 산출합니다.

### 2.1 주요 변수 정의
*   **소득 격차 비율 ($SalaryGapRatio$):**
    $$SalaryGapRatio = \frac{\text{공식 임금통계 기준 연봉 중앙값} - \text{현재 사용자 연봉}}{\text{현재 사용자 연봉}}$$
    *(사용자 연봉이 기준 중앙값보다 낮을 때 양수가 됨. 기준선은 `wage_stat_observation`의 직종×경력구간×지역 중앙값을 사용하며, 공고 공개연봉은 결측률이 높아 참고 표시 전용)*
*   **현재 사용자 연봉:** `core.income_history`의 암호화 컬럼이 원천이며, 기준월 시점에 유효한(가장 늦은 `effective_date` $\le$ 기준월 말일) 세전 연봉이다. 결정 엔진은 이 컬럼을 직접 읽지 않고 `CurrentSalaryProvider`(Member A 소유) 계약으로 받는다 — 복호화 경계를 소득 도메인 안에 둔다([개발 계획 §5-E](../plans/carix-dev-plan.md)).
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
3. CPI 누락으로 구매력 지수 산출 불가 (최신월 발행 지연은 여기 해당하지 않는다 — §1.3의 월 행 생성 범위를 따라 그 월은 행을 만들지 않는다)
4. 연봉 격차 기준선(임금통계) 미확보
5. 현재 사용자 연봉 미확보로 소득 격차 비율 산출 불가 (조건 4의 반대편이다 — 분자·분모 중 하나만 없어도 규칙 2·3·5를 평가할 수 없다)

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
