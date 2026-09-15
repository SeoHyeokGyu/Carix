# Carix 데이터베이스 ERD (Entity-Relationship Diagram)

이 문서는 Carix 데이터베이스의 테이블 간 관계 및 각 테이블의 엔티티 구조를 시각화한 ERD입니다.

```mermaid
erDiagram
    users ||--o| career_profile : has
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
        varchar password_hash "BCrypt"
        varchar onboarding_step
        timestamp created_at
        timestamp updated_at
    }

    session {
        uuid id PK
        uuid user_id FK
        varchar token_hash "SHA-256, 원본 미저장"
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
        varchar source
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
        varchar scenario_name
        varchar current_gross_salary_encrypted
        decimal stay_expected_raise_rate
        varchar move_target_gross_salary_encrypted
        bigint move_one_time_cost
        int expected_unemployment_months
        boolean is_eligible_for_unemployment_benefit
        varchar stay_compensation_12m_encrypted
        varchar move_compensation_12m_encrypted
        varchar estimated_unemployment_benefit_encrypted
        varchar difference_encrypted
        varchar feasibility_status
        int feasibility_percentile "표본 부족이면 NULL"
        int feasibility_sample_size
        timestamp calculated_at
    }
```

## 운영 스키마 적용 이력

`core` 스키마는 Flyway 를 쓰지 않아 적용 이력이 자동으로 남지 않는다
([DEVELOPMENT.md §3.2](../DEVELOPMENT.md)). 운영 DB 에 무엇을 적용했는지 여기 남긴다.

| 적용일 | 대상 | 내용 |
| :--- | :--- | :--- |
| 2026-09-06 | `core.career_scenario` | 금액 여섯 컬럼을 `bigint` → `varchar(512)` 암호문으로 바꾸고 `scenario_name`·`feasibility_*` 를 더했다. 0행이라 `DROP` 후 정본 정의로 재생성했다. `career_scenario_user_latest_idx` 도 함께 다시 만들었다 |
| 2026-09-15 | `core.wage_stat_observation` | 출처 컬럼 `source varchar(20) NOT NULL DEFAULT 'UNKNOWN'` 을 더했다. 기존 360행은 기본값으로 `UNKNOWN` 이 됐다 — 컬럼 도입 이전 적재분이라 KOSIS 실측인지 폴백 상수인지 알 수 없다 |

> **`core.wage_stat_observation.source` 는 쓰기 경로까지 들어왔다.** 수집이 연도별로 출처를
> 기록하고, KOSIS 실측만 기존 행을 덮는다 — 폴백 상수는 기존 행을 건드리지 않는다. 위 360행은
> 컬럼 도입 이전 적재분이라 **재수집 전까지 `UNKNOWN` 으로 남는다.**
>
> **폴백 재적재로는 승격되지 않는다.** KOSIS 경로는 `stat_vintage` 가 `base_period + "08"` 로
> 결정적이라 재수집이 같은 좌표를 덮어 `UNKNOWN` → `KOSIS` 승격이 일어난다. 반면 기본
> 기준연도의 폴백 경로는 `stat_vintage` 가 실행 시점의 `yyyyMM` 이고, `stat_vintage` 가
> 유니크 키 안에 있어 달이 바뀌면 **승격이 아니라 행이 는다.** 그렇게 늘어난 폴백 행의
> `stat_vintage` 는 실측(`202508`)보다 커서 `findLatestBaseline`(`base_period DESC,
> stat_vintage DESC`)이 그것을 고른다 — 승격 규칙의 강등 가드는 좌표가 달라 **도달하지
> 않는다.** 읽기 경로에 출처 우선순위를 넣는 것이 `b13` 소관이다.
