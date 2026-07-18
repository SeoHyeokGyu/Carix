# Carix 상세 기술 명세서 (Detailed Technical Specifications)

이 문서는 Carix 1차 MVP 구현에 필요한 데이터베이스 스키마(DDL), 보안 암호화 정책, 결정 신호 룰 엔진 매트릭스, 표준 스킬 사전 시드(Seed) 데이터 및 외부 OpenAPI 연동 스펙을 정의합니다.

---

## 1. 데이터베이스 스키마 및 보안 암호화 정책

데이터베이스는 **PostgreSQL**을 사용하며, 모든 테이블 구조와 민감자산 정보(연봉, 소비금액)의 암호화 필드 정책을 다음과 같이 정의합니다.

### 1.1 컬럼 레벨 암호화 (Column-level Encryption) Policy
*   **대상 컬럼:** `income_history.gross_salary_encrypted`, `income_history.net_salary_encrypted`, `expense_item.amount_encrypted`
*   **알고리즘:** **AES-256-GCM** (또는 보안 강도가 검증된 AES-256-CBC)
*   **구현 방식:** 애플리케이션 레이어(Spring Boot)에서 **Spring Data JPA Converter**(`AttributeConverter`)를 구현하여 DB 적재 시 자동 암호화하고, 조회 시 복호화합니다. 평문 데이터는 DB 버퍼나 파일시스템에 그대로 기록되지 않도록 차단합니다.

### 1.2 DDL 스펙

```sql
-- 1. 인증 및 계정 영역
CREATE TABLE account (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invitation (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL, -- 'PENDING', 'ACCEPTED', 'EXPIRED'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE magic_link_token (
    id UUID PRIMARY KEY,
    account_id UUID REFERENCES account(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE session (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    session_token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 사용자 프로필 및 이력 데이터 영역
CREATE TABLE career_profile (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE REFERENCES account(id) ON DELETE CASCADE,
    job_family VARCHAR(100) NOT NULL, -- 'DEVELOPER', 'DATA', 'AI'
    years_of_experience INT NOT NULL,
    region VARCHAR(100) NOT NULL, -- 예: 'SEOUL_GANGNAM', 'GYEONGGI' 등 코드화
    employment_type VARCHAR(50) NOT NULL, -- 'FULL_TIME', 'CONTRACT'
    company_size VARCHAR(50) -- 'LARGE', 'MEDIUM', 'STARTUP' (선택값)
);

CREATE TABLE income_history (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    gross_salary_encrypted VARCHAR(512) NOT NULL, -- 세전연봉 (암호화)
    net_salary_encrypted VARCHAR(512),            -- 월 실수령액 (선택적, 암호화)
    effective_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (account_id, effective_date)
);

CREATE TABLE expense_history (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    base_year_month VARCHAR(6) NOT NULL, -- 'YYYYMM' 형식
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (account_id, base_year_month)
);

CREATE TABLE expense_item (
    id UUID PRIMARY KEY,
    expense_history_id UUID NOT NULL REFERENCES expense_history(id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL, -- 'HOUSING', 'FOOD', 'TRANSPORT', 'MEDICAL', 'EDUCATION', 'LEISURE'
    amount_encrypted VARCHAR(512) NOT NULL, -- 월 소비액 (암호화)
    UNIQUE (expense_history_id, category)
);

-- 3. 스킬 및 사용자 스킬 데이터 영역
CREATE TABLE skill (
    id UUID PRIMARY KEY,
    standard_name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL -- 'BACKEND', 'FRONTEND', 'DEVOPS', 'DATA', 'AI_ML'
);

CREATE TABLE skill_alias (
    id UUID PRIMARY KEY,
    skill_id UUID NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    alias_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE user_skill (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    months_of_experience INT NOT NULL DEFAULT 0,
    last_used_date DATE NOT NULL,
    UNIQUE (account_id, skill_id)
);

-- 4. 외부 수집 및 집계 영역
CREATE TABLE cpi_observation (
    id UUID PRIMARY KEY,
    base_year_month VARCHAR(6) NOT NULL,
    category VARCHAR(50) NOT NULL,
    cpi_value DECIMAL(5,2) NOT NULL,
    UNIQUE (base_year_month, category)
);

CREATE TABLE job_posting (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL UNIQUE, -- 고용24 구인공고 ID (wantedAuthNo)
    title VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    salary_min BIGINT, -- 정규화된 연간 세전최소 (원 단위, 결측 가능)
    salary_max BIGINT, -- 정규화된 연간 세전최대 (원 단위, 결측 가능)
    region VARCHAR(100) NOT NULL,
    experience_min INT NOT NULL DEFAULT 0,
    experience_max INT DEFAULT 99,
    job_family VARCHAR(100) NOT NULL,
    content TEXT, -- 공고 상세 본문 텍스트 (스킬 파싱용)
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE market_aggregate (
    id UUID PRIMARY KEY,
    base_year_month VARCHAR(6) NOT NULL,
    job_family VARCHAR(100) NOT NULL,
    region VARCHAR(100) NOT NULL,
    experience_bracket VARCHAR(50) NOT NULL, -- 'JUNIOR'(0~3), 'MID'(4~7), 'SENIOR'(8+)
    skill_id UUID NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    posting_count INT NOT NULL DEFAULT 0,
    total_aggregate_postings INT NOT NULL DEFAULT 0, -- 비교군 전체 공고 수
    demand_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000, -- 수요율 (posting_count / total_aggregate_postings)
    salary_median BIGINT, -- 해당 스킬 공고의 공개연봉 중앙값
    salary_missing_rate DECIMAL(5,2) NOT NULL DEFAULT 100.00, -- 연봉 결측률 (%)
    UNIQUE (base_year_month, job_family, region, experience_bracket, skill_id)
);
```

---

## 2. 결정 신호(Decision Signal) 판정 매트릭스

결정 엔진은 아래 **소득 격차 비율(SalaryGapRatio)**, **시장가치 지수(MVI)**, **구매력 지수(PPI)** 및 **통계 신뢰성 임계값**을 조합하여 신호를 산출합니다.

### 2.1 주요 변수 정의
*   **소득 격차 비율 ($SalaryGapRatio$):**
    $$SalaryGapRatio = \frac{\text{동료집단 공개연봉 중앙값} - \text{현재 사용자 연봉}}{\text{현재 사용자 연봉}}$$
    *(사용자 연봉이 동료집단 중앙값보다 낮을 때 양수가 됨)*
*   **시장가치 지수 ($MVI_{recent}$):** 스킬 수요율 변화량 기준 (100점 기준값)
*   **구매력 지수 ($PPI$):** 실질 구매력 수치 (100점 기준값)
*   **최소 표본 조건 ($MinSampleSize$):** 동료집단 유효 공고 수 $\ge 10$개

### 2.2 판정 우선순위 및 규칙표

| 우선순위 | 타겟 조건 (모두 AND 충족 시) | 결정 신호 | 화면에 표시될 근거 예시 |
| :---: | :--- | :--- | :--- |
| **1** | $MinSampleSize < 10$ 또는 CPI 정보 누락 | **데이터 부족** | "동료집단의 표본 수(N개)가 통계 기준치보다 부족하여 신호를 판단할 수 없습니다." |
| **2** | $SalaryGapRatio \ge 0.15$ <br>AND $MVI_{recent} \ge 110$ | **이직 탐색** | "현재 보상이 동료집단 중앙값보다 15% 이상 낮고, 보유 스킬의 시장 수요가 10% 이상 상승했습니다. 적극적인 이직 탐색을 권장합니다." |
| **3** | $0.05 \le SalaryGapRatio < 0.15$ <br>AND $MVI_{recent} \ge 105$ | **협상 검토** | "현재 보상이 시장 중앙값보다 소폭 낮으며, 스킬 가치는 상승세입니다. 연봉 협상 테이블에서의 기대를 높이거나 협상 카드를 검토해 보세요." |
| **4** | $MVI_{recent} < 90$ <br>AND $PPI < 95$ | **스킬 보강 우선** | "실질 구매력 지수와 함께 보유 스킬의 채용 수요도 최근 감소세입니다. 연봉협상이나 이직보다는 대체 스킬 학습을 권장합니다." |
| **5** | $-0.05 < SalaryGapRatio < 0.05$ <br>AND $MVI_{recent} \ge 90$ | **현 상태 유지** | "현재 보상이 시장 수준에 근접해 있고 스킬 수요 역시 비교적 안정적입니다. 현 직장에서의 역량 집중이 유리한 시점입니다." |
| **기본** | 위 어느 조건에도 해당하지 않을 때 | **현 상태 유지** | "시장에 뚜렷한 보상 격차나 급격한 가치 상승 신호가 포착되지 않아 현 상태 유지를 추천합니다." |

---

## 3. 표준 스킬 사전 및 에일리어싱(Aliasing) 정책

비정형 채용 공고 텍스트에서 스킬을 균일하게 정규화하여 시장 수요를 매핑하기 위한 Seed 데이터와 가공 규칙입니다.

### 3.1 분야별 표준 스킬 Seed 리스트 (개발자/데이터/AI)

*   **백엔드(Backend):**
    *   `Java` (별칭: `java`, `자바`)
    *   `Spring Boot` (별칭: `spring`, `springboot`, `스프링`, `스프링부트`)
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
1.  **텍스트 전처리:** 채용공고의 상세 요강 필드에서 줄바꿈 제거, 모든 영문자를 소문자로 통일(`toLowerCase`), 모든 공백 및 특수문자 제거.
    *   *예: "Spring Boot 개발 경험" ➡️ `springboot개발경험`*
2.  **검색 매칭:** 스킬 사전에 등록된 `alias_name`의 공백 제거 형태가 전처리된 텍스트에 포함(Contains)되어 있는지 검사.
    *   `springboot개발경험`에 `springboot`가 포함되므로 ➡️ **`Spring Boot`** 스킬 감지 및 매핑 성공.

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

---

### 4.2 고용24 (구 워크넷) 채용정보 OpenAPI

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
