-- Carix `core` 스키마 — 지수 스냅샷과 시나리오 (2026-08-20)
--
-- 설계 근거는 docs/specs/technical-specification.md §1.2·§1.3.
-- 실행 정본은 이 파일이다. 적용 절차는 docs/DEVELOPMENT.md §3.2.
--
-- `user_id` 에 외래키를 걸지 않는 이유는 02_user_domain.sql 머리말과 같다.

CREATE TABLE core.index_snapshot (
    id                     uuid PRIMARY KEY,
    user_id                uuid NOT NULL,
    base_year_month        character varying(6) NOT NULL,
    data_version           character varying(50) NOT NULL,
    rule_version           character varying(50) NOT NULL,
    purchasing_power_index numeric(6,2),
    market_value_index     numeric(6,2),
    opening_window_index   numeric(6,2),
    decision_signal        character varying(50) NOT NULL,
    rationale              jsonb NOT NULL,
    created_at             timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, base_year_month, data_version, rule_version)
);
COMMENT ON TABLE core.index_snapshot IS
    '같은 (user_id, base_year_month, data_version, rule_version) 재실행은 기존 행을 대체한다. 이전 버전 행은 근거 추적을 위해 남긴다.';
COMMENT ON COLUMN core.index_snapshot.purchasing_power_index IS
    '계산 불가(표본 부족·CPI 결측 등)면 NULL 이다. 0 이나 100 으로 채우지 않는다.';
COMMENT ON COLUMN core.index_snapshot.opening_window_index IS
    '채용 활발도. 동료집단 공고 비중의 최근 3개월 ÷ 이전 12개월(4~15개월 전) × 100. 계산 불가면 NULL 이다.';
COMMENT ON COLUMN core.index_snapshot.rationale IS
    '적용 규칙, 사용한 근거, 제외한 데이터와 그 사유, 원천 기준일, 배치 실행 ID.';

CREATE TABLE core.career_scenario (
    id                                   uuid PRIMARY KEY,
    user_id                              uuid NOT NULL,
    scenario_name                        varchar(100),
    current_gross_salary_encrypted       varchar(512),
    stay_expected_raise_rate             numeric(5,2),
    move_target_gross_salary_encrypted   varchar(512),
    move_one_time_cost                   bigint,
    expected_unemployment_months         integer DEFAULT 0
        CHECK (expected_unemployment_months BETWEEN 0 AND 6),
    is_eligible_for_unemployment_benefit boolean DEFAULT true,
    stay_compensation_12m_encrypted      varchar(512),
    move_compensation_12m_encrypted      varchar(512),
    estimated_unemployment_benefit_encrypted varchar(512),
    difference_encrypted                 varchar(512),
    feasibility_status                   varchar(30),
    feasibility_percentile               integer,
    feasibility_sample_size              integer,
    engine_version                       varchar(10) NOT NULL DEFAULT '1',
    is_draft                             boolean NOT NULL DEFAULT false,
    payload_encrypted                    text,
    calculated_at                        timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE core.career_scenario IS
    '사용자가 명시적으로 저장한 시나리오 목록(is_draft = false, calculated_at DESC, id DESC)과 사용자당 초안 1건(is_draft = true). engine_version 1 은 이전 방식으로 저장한 행이고 엔진 1 컬럼을 쓴다. 2 는 payload_encrypted 만 쓰고 엔진 1 컬럼은 NULL 이다.';
COMMENT ON COLUMN core.career_scenario.stay_compensation_12m_encrypted IS
    '금액 컬럼은 income_history 와 같은 AES-256-GCM 으로 저장한다. 평문이면 stay_expected_raise_rate 와 나눠 현재 연봉이 원 단위로 복원돼, 연봉을 암호화한 이유가 이 테이블에서 사라진다.';
COMMENT ON COLUMN core.career_scenario.payload_encrypted IS
    '엔진 2 의 {request, result} JSON 을 income_history 와 같은 AES-256-GCM 으로 암호화해 저장한다. 요청에 연봉·성과급이, 결과에 세후 월 소득이 들어 있어 평문으로 두지 않는다. 초안은 result 가 없다.';
COMMENT ON COLUMN core.career_scenario.feasibility_percentile IS
    '목표 연봉의 동료집단 분포 내 백분위. 표본이 부족해 산출하지 못했으면 NULL 이고 feasibility_status 가 사유를 말한다. 분포는 시점에 의존해 나중에 재계산하면 그때의 판단이 나오지 않으므로 저장한다.';
COMMENT ON COLUMN core.career_scenario.move_one_time_cost IS
    '사용자가 입력한 부대비용이라 연봉 복원에 쓰이지 않는다. 평문으로 둔다.';

CREATE INDEX index_snapshot_user_month_idx
    ON core.index_snapshot (user_id, base_year_month DESC);
CREATE INDEX career_scenario_user_latest_idx
    ON core.career_scenario (user_id, calculated_at DESC, id DESC);
CREATE UNIQUE INDEX career_scenario_user_draft_idx
    ON core.career_scenario (user_id) WHERE is_draft;
