# Carix 데이터베이스 ERD (Entity-Relationship Diagram)

이 문서는 Carix 데이터베이스의 테이블 간 관계 및 각 테이블의 엔티티 구조를 시각화한 ERD입니다.

> **2026-08-09 확장:** `job_posting`에 `company_badge`, `position_level` 컬럼 추가. 근거는
> [2026-08-09 채용공고 크롤링 데이터 영속화 설계](../superpowers/specs/2026-08-09-job-posting-persistence-design.md) —
> 실측 크롤링 데이터를 원래 스키마에 넣어보다가 ERD에 자리가 없어 버려지던 필드를 확인하고 확장했다.
>
> **2026-08-09 운영 현황:** `job_posting`/`job_posting_skill`/`skill`/`skill_alias`/`raw_ingest`
> 5개 테이블 전부 원격 공유 Postgres(`carix` DB, `core` 스키마)에 실제로 만들어져 있고,
> 잡코리아·사람인 크롤링 결과 800건 + 스킬 699개 + raw_ingest 410건이 영구 적재돼 있다.
> **2026-08-19 스키마 관리 방식:** core-api에서 Flyway를 제거했다. `core` 스키마는
> 이 문서와 [상세 기술 명세 §1](technical-specification.md)의 DDL을 정본으로 삼아 `psql`로
> 직접 적용한다(절차는 [DEVELOPMENT.md §3.2](../DEVELOPMENT.md)). 마이그레이션 파일 관리
> 부담이 이득보다 크다는 판단이며, `auth` 스키마는 Flyway를 그대로 쓴다. 스키마를 바꾸면
> 적용 이력이 자동으로 남지 않으므로 아래 운영 현황 절에 직접 기록한다.
>
> **2026-08-19 데이터 정정:** 첫 적재가 원천 정보를 다 쓰지 못해 `raw_ingest` 원문으로
> 재파싱했다. 공개연봉 36 -> 317건, `job_family` 0 -> 800건(DEVELOPER 567 / OTHER 125 /
> AI 70 / DATA 38), 스킬에서 인성 역량과 직무 카테고리를 분리(스킬 699 -> 683, 연결
> 4300 -> 3860, `is_custom` 전건 false). 재실행 수단은
> `com.carix.jobposting.backfill.JobPostingBackfill`이다.

```mermaid
erDiagram
    users ||--o| career_profile : has
    users ||--o{ magic_link_token : uses
    users ||--o{ session : establishes
    users ||--o{ income_history : records
    users ||--o{ expense_history : logs
    users ||--o{ user_skill : possesses
    users ||--o{ skill : creates
    users ||--o{ index_snapshot : generates
    users ||--o{ career_scenario : simulates

    expense_history ||--|{ expense_item : details

    skill ||--o{ skill_alias : aliased_by
    skill ||--o{ user_skill : references
    skill ||--o{ job_posting_skill : referenced_by
    skill ||--o{ market_aggregate : referenced_by

    job_posting ||--o{ job_posting_skill : has

    users {
        uuid id PK
        varchar email
        varchar onboarding_step
        timestamp created_at
        timestamp updated_at
    }

    invitation {
        uuid id PK
        varchar email
        varchar status
        timestamp expires_at
        timestamp created_at
    }

    magic_link_token {
        uuid id PK
        uuid user_id FK
        varchar token_hash
        timestamp expires_at
        timestamp used_at
    }

    session {
        uuid id PK
        uuid user_id FK
        varchar session_token
        timestamp expires_at
        timestamp created_at
    }

    career_profile {
        uuid id PK
        uuid user_id FK
        varchar job_family
        int years_of_experience
        varchar region
        varchar employment_type
        varchar company_size
    }

    income_history {
        uuid id PK
        uuid user_id FK
        varchar gross_salary_encrypted
        varchar net_salary_encrypted
        date effective_date
        timestamp created_at
    }

    expense_history {
        uuid id PK
        uuid user_id FK
        varchar base_year_month
        timestamp created_at
    }

    expense_item {
        uuid id PK
        uuid expense_history_id FK
        varchar category
        varchar amount_encrypted
    }

    skill {
        uuid id PK
        varchar standard_name
        varchar category
        boolean is_custom
        uuid created_by FK
    }

    skill_alias {
        uuid id PK
        uuid skill_id FK
        varchar alias_name
    }

    user_skill {
        uuid id PK
        uuid user_id FK
        uuid skill_id FK
        int months_of_experience
        date last_used_date
    }

    raw_ingest {
        uuid id PK
        varchar source
        varchar source_item_id
        jsonb payload
        timestamp purged_at
        varchar schema_version
        varchar first_seen_month
        varchar last_seen_month
        timestamp collected_at
    }

    cpi_observation {
        uuid id PK
        varchar base_year_month
        varchar category
        decimal cpi_value
    }

    wage_stat_observation {
        uuid id PK
        varchar job_family
        varchar experience_bracket
        varchar region
        varchar wage_definition
        bigint wage_median
        varchar base_period
        varchar stat_vintage
    }

    job_posting {
        uuid id PK
        varchar source
        varchar external_id
        uuid dedup_group_id
        varchar dedup_rule_version
        varchar title
        varchar company_name
        bigint salary_min
        bigint salary_max
        varchar region
        int experience_min
        int experience_max
        varchar employment_type
        varchar company_size
        varchar company_badge
        varchar position_level
        varchar job_family
        text content
        date posted_start_date
        date posted_end_date
        varchar first_seen_month
        varchar last_seen_month
        timestamp collected_at
    }

    job_posting_skill {
        uuid id PK
        uuid job_posting_id FK
        uuid skill_id FK
    }

    market_aggregate {
        uuid id PK
        varchar source
        varchar base_year_month
        varchar job_family
        varchar region
        varchar experience_bracket
        varchar employment_type
        varchar company_size
        uuid skill_id FK
        int posting_count
        int total_aggregate_postings
        decimal demand_rate
        bigint salary_min
        bigint salary_p25
        bigint salary_median
        bigint salary_p75
        bigint salary_max
        decimal salary_missing_rate
    }

    index_snapshot {
        uuid id PK
        uuid user_id FK
        varchar base_year_month
        varchar data_version
        varchar rule_version
        decimal purchasing_power_index
        decimal market_value_index
        varchar decision_signal
        jsonb rationale
        timestamp created_at
    }

    career_scenario {
        uuid id PK
        uuid user_id FK
        decimal stay_expected_raise_rate
        bigint move_target_gross_salary
        bigint move_one_time_cost
        int expected_unemployment_months
        boolean is_eligible_for_unemployment_benefit
    }
```
