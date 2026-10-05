-- Carix `core` 스키마 — 현재 운영 반영본 (2026-08-09 최초 적용)
--
-- 채용공고 수집 도메인 5개 테이블. 실제 운영 DB(`carix`)에서 추출해 실물과 일치한다.
-- 설계 의도와 배경은 docs/specs/technical-specification.md 를 본다 — 그쪽 DDL 은 설계
-- 시점 기술이라 실물과 다른 부분이 있고, 실행 정본은 이 파일이다.
--
-- 적용: psql "postgresql://<user>@<host>:5432/carix?sslmode=require" -f 이_파일
-- 절차와 규칙은 docs/DEVELOPMENT.md §3.2.

CREATE SCHEMA IF NOT EXISTS core;

CREATE TABLE core.job_posting (
    id uuid NOT NULL,
    source character varying(20) NOT NULL,
    external_id character varying(100) NOT NULL,
    dedup_group_id uuid,
    dedup_rule_version character varying(20),
    title character varying(500) NOT NULL,
    company_name character varying(255),
    salary_min bigint,
    salary_max bigint,
    region character varying(255),
    experience_min integer,
    experience_max integer,
    employment_type character varying(100),
    company_size character varying(50),
    company_badge character varying(255),
    position_level character varying(100),
    job_family character varying(100),
    posted_start_date date,
    posted_end_date date,
    first_seen_month character varying(6),
    last_seen_month character varying(6),
    sampled_month character varying(6),
    collected_at timestamp with time zone NOT NULL
);

CREATE TABLE core.job_posting_skill (
    id uuid NOT NULL,
    job_posting_id uuid NOT NULL,
    skill_id uuid NOT NULL
);

CREATE TABLE core.raw_ingest (
    id uuid NOT NULL,
    source character varying(20) NOT NULL,
    source_item_id character varying(100) NOT NULL,
    first_seen_month character varying(6),
    last_seen_month character varying(6),
    collected_at timestamp with time zone NOT NULL
);

CREATE TABLE core.skill (
    id uuid NOT NULL,
    standard_name character varying(255) NOT NULL,
    category character varying(50),
    is_custom boolean DEFAULT true NOT NULL,
    created_by uuid
);

CREATE TABLE core.skill_alias (
    id uuid NOT NULL,
    skill_id uuid NOT NULL,
    alias_name character varying(255) NOT NULL
);

ALTER TABLE ONLY core.job_posting
    ADD CONSTRAINT job_posting_pkey PRIMARY KEY (id);

ALTER TABLE ONLY core.job_posting_skill
    ADD CONSTRAINT job_posting_skill_job_posting_id_skill_id_key UNIQUE (job_posting_id, skill_id);

ALTER TABLE ONLY core.job_posting_skill
    ADD CONSTRAINT job_posting_skill_pkey PRIMARY KEY (id);

ALTER TABLE ONLY core.job_posting
    ADD CONSTRAINT job_posting_source_external_id_key UNIQUE (source, external_id);

ALTER TABLE ONLY core.raw_ingest
    ADD CONSTRAINT raw_ingest_pkey PRIMARY KEY (id);

ALTER TABLE ONLY core.raw_ingest
    ADD CONSTRAINT raw_ingest_source_source_item_id_key UNIQUE (source, source_item_id);

ALTER TABLE ONLY core.skill_alias
    ADD CONSTRAINT skill_alias_pkey PRIMARY KEY (id);

ALTER TABLE ONLY core.skill
    ADD CONSTRAINT skill_pkey PRIMARY KEY (id);

ALTER TABLE ONLY core.skill
    ADD CONSTRAINT skill_standard_name_category_key UNIQUE (standard_name, category);

ALTER TABLE ONLY core.job_posting_skill
    ADD CONSTRAINT job_posting_skill_job_posting_id_fkey FOREIGN KEY (job_posting_id) REFERENCES core.job_posting(id) ON DELETE CASCADE;

ALTER TABLE ONLY core.job_posting_skill
    ADD CONSTRAINT job_posting_skill_skill_id_fkey FOREIGN KEY (skill_id) REFERENCES core.skill(id) ON DELETE CASCADE;

ALTER TABLE ONLY core.skill_alias
    ADD CONSTRAINT skill_alias_skill_id_fkey FOREIGN KEY (skill_id) REFERENCES core.skill(id) ON DELETE CASCADE;
