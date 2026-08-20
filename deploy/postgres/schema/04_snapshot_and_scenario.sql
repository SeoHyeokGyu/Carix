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
    decision_signal        character varying(50) NOT NULL,
    rationale              jsonb NOT NULL,
    created_at             timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, base_year_month, data_version, rule_version)
);
COMMENT ON TABLE core.index_snapshot IS
    '같은 (user_id, base_year_month, data_version, rule_version) 재실행은 기존 행을 대체한다. 이전 버전 행은 근거 추적을 위해 남긴다.';
COMMENT ON COLUMN core.index_snapshot.purchasing_power_index IS
    '계산 불가(표본 부족·CPI 결측 등)면 NULL 이다. 0 이나 100 으로 채우지 않는다.';
COMMENT ON COLUMN core.index_snapshot.rationale IS
    '적용 규칙, 사용한 근거, 제외한 데이터와 그 사유, 원천 기준일, 배치 실행 ID.';

CREATE TABLE core.career_scenario (
    id                                  uuid PRIMARY KEY,
    user_id                             uuid NOT NULL,
    stay_expected_raise_rate            numeric(5,2) NOT NULL,
    move_target_gross_salary            bigint NOT NULL,
    move_one_time_cost                  bigint NOT NULL,
    expected_unemployment_months        integer NOT NULL DEFAULT 0
        CHECK (expected_unemployment_months BETWEEN 0 AND 6),
    is_eligible_for_unemployment_benefit boolean NOT NULL DEFAULT true,
    stay_compensation_12m               bigint NOT NULL,
    move_compensation_12m               bigint NOT NULL,
    estimated_unemployment_benefit      bigint NOT NULL DEFAULT 0,
    difference                          bigint NOT NULL,
    calculated_at                       timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE core.career_scenario IS
    '사용자당 최근 1건을 유효 시나리오로 본다. 조회는 calculated_at DESC, id DESC 순 첫 행이다.';

CREATE INDEX index_snapshot_user_month_idx
    ON core.index_snapshot (user_id, base_year_month DESC);
CREATE INDEX career_scenario_user_latest_idx
    ON core.career_scenario (user_id, calculated_at DESC, id DESC);
