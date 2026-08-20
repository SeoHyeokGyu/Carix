-- Carix `core` 스키마 — 사용자 프로필·이력 도메인 (2026-08-20)
--
-- 설계 근거는 docs/specs/technical-specification.md §1.2. 실행 정본은 이 파일이다.
-- 적용 절차와 규칙은 docs/DEVELOPMENT.md §3.2 — 배포보다 DDL 이 먼저다.
--
-- `user_id` 에 외래키를 걸지 않는다.
--   `users` 는 auth-service 가 `auth` 스키마에 Flyway 로 소유한다. 스키마를 가로지르는
--   외래키를 걸면 (1) core 의 DDL 적용이 auth-service 마이그레이션에 종속되고
--   (2) 두 서비스를 독립적으로 배포할 수 없다. 스펙 §1.2 의 `REFERENCES users(id)` 는
--   서비스 분리 이전에 쓰인 것이라 여기서는 따르지 않는다.
--   사용자 삭제 시 이 테이블들의 정리는 애플리케이션 레벨에서 수행한다
--   (docs/SECURITY.md 의 계정 삭제 요구사항).
--
-- 금액 컬럼은 암호화 문자열이다. 애플리케이션의 AttributeConverter 가 AES-256-GCM 으로
-- 넣고 뺀다 (technical-specification.md §1.1). DB 는 평문을 보지 않는다.

CREATE TABLE core.career_profile (
    id                  uuid PRIMARY KEY,
    user_id             uuid NOT NULL UNIQUE,
    job_family          character varying(100),
    years_of_experience integer,
    region              character varying(100),
    employment_type     character varying(50),
    company_size        character varying(50)
);
COMMENT ON TABLE core.career_profile IS
    '온보딩 자동 저장을 허용하려고 컬럼을 NULL 허용으로 둔다. 필수 필드 완비 검증은 온보딩 완료 제출 시 애플리케이션에서 한다.';

CREATE TABLE core.income_history (
    id                     uuid PRIMARY KEY,
    user_id                uuid NOT NULL,
    gross_salary_encrypted character varying(512) NOT NULL,
    net_salary_encrypted   character varying(512),
    effective_date         date NOT NULL,
    created_at             timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, effective_date)
);

CREATE TABLE core.expense_history (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL,
    base_year_month character varying(6) NOT NULL,
    created_at      timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, base_year_month)
);

CREATE TABLE core.expense_item (
    id                 uuid PRIMARY KEY,
    expense_history_id uuid NOT NULL REFERENCES core.expense_history(id) ON DELETE CASCADE,
    category           character varying(50) NOT NULL,
    amount_encrypted   character varying(512) NOT NULL,
    UNIQUE (expense_history_id, category)
);
COMMENT ON TABLE core.expense_item IS
    '사용자가 입력한 카테고리만 행으로 존재한다. 미입력분의 기본값 보완 여부는 행 존재 여부에서 도출하고 별도 상태 컬럼을 두지 않는다.';

CREATE TABLE core.user_skill (
    id                   uuid PRIMARY KEY,
    user_id              uuid NOT NULL,
    skill_id             uuid NOT NULL REFERENCES core.skill(id) ON DELETE CASCADE,
    months_of_experience integer NOT NULL CHECK (months_of_experience BETWEEN 1 AND 600),
    last_used_date       date NOT NULL,
    UNIQUE (user_id, skill_id)
);

CREATE INDEX career_profile_peer_group_idx
    ON core.career_profile (job_family, region, employment_type);
CREATE INDEX income_history_user_effective_idx
    ON core.income_history (user_id, effective_date DESC);
CREATE INDEX user_skill_skill_idx
    ON core.user_skill (skill_id);
