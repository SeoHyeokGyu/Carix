-- Carix `core` 스키마 — 외부 관측치와 사전 집계 (2026-08-20)
--
-- 설계 근거는 docs/specs/technical-specification.md §1.2, 완화 알고리즘은 §domain-logic.md §2.
-- 실행 정본은 이 파일이다. 적용 절차는 docs/DEVELOPMENT.md §3.2.

CREATE TABLE core.cpi_observation (
    id              uuid PRIMARY KEY,
    base_year_month character varying(6) NOT NULL,
    category        character varying(50) NOT NULL,
    cpi_value       numeric(5,2) NOT NULL,
    UNIQUE (base_year_month, category)
);
COMMENT ON TABLE core.cpi_observation IS
    'KOSIS 지출목적별 소비자물가지수. 결측 월은 보간하지 않고 행을 만들지 않는다 — 화면에서 업데이트 지연으로 표시한다.';

CREATE TABLE core.wage_stat_observation (
    id                 uuid PRIMARY KEY,
    job_family         character varying(100) NOT NULL,
    experience_bracket character varying(50) NOT NULL,
    region             character varying(100) NOT NULL,
    wage_definition    character varying(100) NOT NULL,
    wage_median        bigint NOT NULL,
    base_period        character varying(6) NOT NULL,
    stat_vintage       character varying(6) NOT NULL,
    UNIQUE (job_family, experience_bracket, region, base_period, stat_vintage)
);
COMMENT ON TABLE core.wage_stat_observation IS
    '연봉 격차 판정의 기준선인 공식 임금통계. 같은 기준기간의 개정판은 stat_vintage 로 공존하며, 조회는 base_period 별 최신 vintage 를 쓴다.';
COMMENT ON COLUMN core.wage_stat_observation.wage_median IS '연간 세전 중앙값 (원 단위)';

CREATE TABLE core.market_aggregate (
    id                      uuid PRIMARY KEY,
    source                  character varying(50) NOT NULL,
    base_year_month         character varying(6) NOT NULL,
    job_family              character varying(100) NOT NULL,
    region                  character varying(100) NOT NULL,
    experience_bracket      character varying(50) NOT NULL,
    employment_type         character varying(50),
    company_size            character varying(50),
    skill_id                uuid REFERENCES core.skill(id) ON DELETE CASCADE,
    posting_count           integer NOT NULL DEFAULT 0,
    total_aggregate_postings integer NOT NULL DEFAULT 0,
    demand_rate             numeric(5,4) NOT NULL DEFAULT 0.0000,
    salary_min              bigint,
    salary_p25              bigint,
    salary_median           bigint,
    salary_p75              bigint,
    salary_max              bigint,
    salary_missing_rate     numeric(5,2) NOT NULL DEFAULT 100.00,
    -- 이름을 명시한다. 자동 생성 이름은 63자를 넘겨 잘리고, 그러면 COMMENT 로 참조할 수 없다.
    CONSTRAINT market_aggregate_dimension_key UNIQUE NULLS NOT DISTINCT
        (source, base_year_month, job_family, region, experience_bracket,
         employment_type, company_size, skill_id)
);
COMMENT ON TABLE core.market_aggregate IS
    '원천별 행만 저장한다. 통합 지수는 표본수 가중 결합으로 계산 시점에 도출한다.';
COMMENT ON COLUMN core.market_aggregate.employment_type IS
    'NULL 은 전체 롤업(완화 Level 5), 원천이 값을 주지 않은 공고는 UNKNOWN 버킷이다. 둘을 섞지 않는다.';
COMMENT ON COLUMN core.market_aggregate.company_size IS
    'NULL 은 전체 롤업(완화 Level 2), 원천 미상은 UNKNOWN 버킷이다. 사람인은 기업규모를 주지 않으므로 전부 UNKNOWN 이다.';
COMMENT ON COLUMN core.market_aggregate.skill_id IS
    'NULL 은 스킬 무관 동료집단 전체 행이다 — 연봉 분포와 비교군 전체 공고 수를 담는다.';
COMMENT ON CONSTRAINT market_aggregate_dimension_key ON core.market_aggregate IS
    'NULLS NOT DISTINCT(PG 15+): 완화 그룹의 NULL 차원을 같은 값으로 취급해 재실행 upsert 를 멱등하게 만든다. 일반 UNIQUE 로는 롤업 행이 중복 적재된다.';

CREATE INDEX market_aggregate_lookup_idx
    ON core.market_aggregate (base_year_month, job_family, region, experience_bracket);
CREATE INDEX market_aggregate_skill_idx
    ON core.market_aggregate (skill_id) WHERE skill_id IS NOT NULL;
