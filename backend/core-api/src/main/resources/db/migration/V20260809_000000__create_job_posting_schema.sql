CREATE TABLE skill (
    id UUID PRIMARY KEY,
    standard_name VARCHAR(255) NOT NULL,
    category VARCHAR(50),
    is_custom BOOLEAN NOT NULL DEFAULT true,
    created_by UUID,
    UNIQUE (standard_name, category)
);

CREATE TABLE skill_alias (
    id UUID PRIMARY KEY,
    skill_id UUID NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    alias_name VARCHAR(255) NOT NULL
);

CREATE TABLE job_posting (
    id UUID PRIMARY KEY,
    source VARCHAR(20) NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    dedup_group_id UUID,
    dedup_rule_version VARCHAR(20),
    title VARCHAR(500) NOT NULL,
    company_name VARCHAR(255),
    salary_min BIGINT,
    salary_max BIGINT,
    region VARCHAR(255),
    experience_min INT,
    experience_max INT,
    employment_type VARCHAR(100),
    company_size VARCHAR(50),
    company_badge VARCHAR(255),
    position_level VARCHAR(100),
    job_family VARCHAR(100),
    content TEXT,
    posted_start_date DATE,
    posted_end_date DATE,
    first_seen_month VARCHAR(6),
    last_seen_month VARCHAR(6),
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (source, external_id)
);

CREATE TABLE job_posting_skill (
    id UUID PRIMARY KEY,
    job_posting_id UUID NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    UNIQUE (job_posting_id, skill_id)
);

CREATE TABLE raw_ingest (
    id UUID PRIMARY KEY,
    source VARCHAR(20) NOT NULL,
    source_item_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    purged_at TIMESTAMP WITH TIME ZONE,
    schema_version VARCHAR(20),
    first_seen_month VARCHAR(6),
    last_seen_month VARCHAR(6),
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (source, source_item_id)
);
