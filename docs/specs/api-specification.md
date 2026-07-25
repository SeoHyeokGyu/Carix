# Carix REST API Endpoints Specification

이 문서는 React(Vite + Node SSR) 프론트엔드와 Spring Boot 백엔드 간에 통신할 핵심 REST API 규격을 정의합니다. 모든 API 요청과 응답은 JSON 포맷을 기준으로 합니다. 소득·지출 금액은 인증된 본인에게만 반환하며, 로그·분석 이벤트·집계 데이터에는 노출하지 않습니다.

---

## 1. 공통 규격

### 1.1 기본 경로 및 인증
*   **Base URL:** `/api/v1`
*   **인증 방식:** HTTP Only + Secure 쿠키에 담긴 불투명(opaque) 세션 토큰. 서버는 `session` 테이블에서 토큰을 검증한다 (서버 세션 단일 방식 — JWT·`Authorization` 헤더는 사용하지 않음)
*   **에러 응답 포맷 (HTTP 4xx/5xx):**
    ```json
    {
      "code": "INVALID_INPUT",
      "message": "입력값 형식이 올바르지 않습니다.",
      "timestamp": "2026-07-18T17:00:00Z",
      "details": {
        "grossSalary": "음수 금액은 허용되지 않습니다."
      }
    }
    ```

---

## 2. 인증 API (Authentication)

### 2.1 매직링크 발송 요청
*   **Endpoint:** `POST /api/v1/auth/magic-link`
*   **Request Body:**
    ```json
    {
      "email": "user@example.com"
    }
    ```
*   **Response (200 OK):**
    ```json
    {
      "message": "인증 링크가 발송되었습니다.",
      "sentAt": "2026-07-18T17:00:00Z"
    }
    ```
    *(계정 열거 방지를 위해 초대되지 않은 메일이라도 동일한 200 OK 응답을 반환합니다)*

### 2.2 매직링크 검증 및 로그인
*   **Endpoint:** `POST /api/v1/auth/verify`
*   **Request Body:**
    ```json
    {
      "email": "user@example.com",
      "token": "a1b2c3d4e5f6g7h8..."
    }
    ```
*   **Response (200 OK):**
    ```json
    {
      "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "user@example.com",
      "isOnboardingComplete": false
    }
    ```

---

## 3. 프로필 및 온보딩 API

### 3.1 커리어 프로필 조회
*   **Endpoint:** `GET /api/v1/profile`
*   **Response (200 OK):**
    ```json
    {
      "jobFamily": "DEVELOPER",
      "yearsOfExperience": 5,
      "region": "SEOUL_GANGNAM",
      "employmentType": "FULL_TIME",
      "companySize": "MEDIUM"
    }
    ```

### 3.2 커리어 프로필 수정 (온보딩 포함)
*   **Endpoint:** `PATCH /api/v1/profile`
*   온보딩 중 자동 저장을 위해 **부분 필드 저장을 허용**한다 (보낸 필드만 갱신). 필수 필드(주 직무, 총 경력, 지역, 고용 형태) 완비 검증은 온보딩 검토 단계 완료 제출 시 수행한다.
*   **Request Body (전체 예시):**
    ```json
    {
      "jobFamily": "DEVELOPER",
      "yearsOfExperience": 5,
      "region": "SEOUL_GANGNAM",
      "employmentType": "FULL_TIME",
      "companySize": "MEDIUM"
    }
    ```
*   **Response (200 OK):** (조회 응답과 동일)

---

## 4. 소득 및 지출 관리 API

### 4.1 소득 이력 조회
*   **Endpoint:** `GET /api/v1/income`
*   **Response (200 OK):**
    ```json
    [
      {
        "id": "e812d8a4-0cfb-4a5f-9dfb-12d88c91350a",
        "grossSalary": 60000000,
        "netSalary": 4200000,
        "effectiveDate": "2026-01-01"
      },
      {
        "id": "fa91b5c4-2dfb-402a-bf39-44ef8c91361c",
        "grossSalary": 55000000,
        "netSalary": 3900000,
        "effectiveDate": "2025-01-01"
      }
    ]
    ```

### 4.2 소득 정보 등록
*   **Endpoint:** `POST /api/v1/income`
*   **Request Body:**
    ```json
    {
      "grossSalary": 65000000,
      "netSalary": 4500000,
      "effectiveDate": "2026-07-01"
    }
    ```
*   **Response (201 Created):** 등록 완료된 단건 반환

---

## 5. 대시보드 및 지표 API

### 5.1 대시보드 지표 요약 (Summary)
*   **Endpoint:** `GET /api/v1/dashboard/summary`
*   **Response (200 OK):**
    ```json
    {
      "purchasingPowerIndex": {
        "value": 94.2,
        "changeFromLastMonth": -1.2,
        "status": "DECREASED",
        "estimateLabel": "PARTIALLY_ESTIMATED"
      },
      "marketValueIndex": {
        "value": 117.5,
        "changeFromLastMonth": 3.8,
        "status": "INCREASED"
      },
      "decisionSignal": {
        "code": "SEEK_JOB",
        "label": "이직 탐색",
        "rulesApplied": [
          "연봉 격차 15% 이상",
          "보유 스킬 시장수요 상승세"
        ],
        "rationale": "보유 중인 스킬의 가치는 상승 추세이며, 현재 연봉 6,000만 원은 공식 임금통계 중앙값 7,200만 원 대비 격차 20%입니다."
      },
      "metadata": {
        "baseDate": "2026-07-01",
        "dataVersion": "20260701093000-000",
        "ruleVersion": "1.2.0",
        "updateDelayed": false,
        "sources": [
          { "source": "WORK24", "sampleSize": 84 },
          { "source": "SARAMIN", "sampleSize": 40 }
        ],
        "sampleSize": 124,
        "salaryMissingRate": 42.5,
        "peerGroup": {
          "fallbackLevel": 3,
          "appliedConditions": "서울 전체 / 경력 MID / 회사규모 무관",
          "excluded": [
            { "skill": "CustomSkillX", "reason": "CUSTOM_SKILL_NO_STATS" }
          ]
        },
        "wageStat": {
          "wageDefinition": "초과급여 제외, 상여 포함",
          "statVintage": "202606"
        },
        "expenseWeightStatus": "PARTIALLY_ESTIMATED"
      }
    }
    ```
    *(`changeFromLastMonth`는 지수 포인트(p) 차이다. 백분율 변화가 아니다)*

### 5.2 동료집단 연봉 분포
*   **Endpoint:** `GET /api/v1/dashboard/salary-distribution`
*   결합 분포와 percentile은 원천별 5수치 요약으로 재구성할 수 없으므로, **동료집단 조건의 원 공고(`job_posting`)를 직접 집계**해 계산한다: `dedup_group_id` 단위로 원천 간 중복을 제거하고 유효 공개연봉 행만 사용. `market_aggregate`의 원천별 5수치는 배치 검증·참고용이며 이 응답의 산출원이 아니다.
*   표본 부족 시에도 `200 OK`로 상태와 표본 수를 반환한다 (204 금지 — 화면은 `표본 부족`과 표본 수를 표시해야 함):
    ```json
    { "status": "INSUFFICIENT_SAMPLE", "sampleSize": 4, "minRequired": 10 }
    ```
*   **Response (200 OK):**
    ```json
    {
      "baseline": { "source": "WAGE_STAT", "median": 72000000, "wageDefinition": "초과급여 제외, 상여 포함" },
      "postingReference": {
        "min": 45000000, "p25": 58000000, "median": 65000000, "p75": 74000000, "max": 95000000,
        "sampleSize": 71, "missingRate": 42.5
      },
      "userPosition": { "grossSalary": 60000000, "percentile": 35 }
    }
    ```
    *(공고 분포는 참고 표시 전용, 판정 기준선은 `baseline` — 상세 기술 명세 §2.1 규약)*

### 5.3 지수 트렌드 차트 데이터
*   **Endpoint:** `GET /api/v1/dashboard/trends`
*   **Response (200 OK):**
    ```json
    {
      "trends": [
        {
          "yearMonth": "2026-01",
          "purchasingPowerIndex": 100.0,
          "marketValueIndex": 100.0,
          "incomeIndex": 100.0,
          "personalPriceIndex": 100.0,
          "nominalIncome": 5000000,
          "realIncome": 5000000
        },
        {
          "yearMonth": "2026-02",
          "purchasingPowerIndex": 99.1,
          "marketValueIndex": 102.3,
          "incomeIndex": 100.0,
          "personalPriceIndex": 100.9,
          "nominalIncome": 5000000,
          "realIncome": 4955000
        }
      ]
    }
    ```
    *(소득 vs 물가 차트는 `incomeIndex`와 `personalPriceIndex` 두 지수 계열(기준월 100)로 그린다. 원화 금액과 지수를 같은 축에 겹치지 않는다. `purchasingPowerIndex = incomeIndex ÷ personalPriceIndex × 100`. 파생 금액(`realIncome` 등)은 원 단위로 계산 후 천 원 단위 반올림해 표시한다)*

### 5.5 스킬별 수요 상세
*   **Endpoint:** `GET /api/v1/dashboard/skills`
*   대시보드 스킬수요 탭용. 보유 스킬별 수요 지수와 산출 근거를 반환.
*   **Response (200 OK):**
    ```json
    {
      "skills": [
        {
          "skillId": "…", "standardName": "Spring Boot",
          "demandIndex": 123.4,
          "recentDemandRate": 0.0812, "baselineDemandRate": 0.0658,
          "sources": [
            { "source": "WORK24", "demandIndex": 120.1, "sampleSize": 84 },
            { "source": "SARAMIN", "demandIndex": 130.2, "sampleSize": 40 }
          ],
          "weight": 0.715,
          "status": "OK"
        },
        { "skillId": "…", "standardName": "CustomSkillX", "status": "CUSTOM_SKILL_NO_STATS" },
        { "skillId": "…", "standardName": "Airflow", "status": "INSUFFICIENT_SAMPLE", "sampleSize": 3 }
      ],
      "coverage": "보유 스킬 3개 중 1개 반영 (가중치 100% 재정규화)"
    }
    ```

### 5.4 월별 스냅샷 이력
*   **Endpoint:** `GET /api/v1/dashboard/history`
*   S-11 화면용. 월별 스냅샷 목록을 최신순으로 반환.
*   **Response (200 OK):**
    ```json
    {
      "snapshots": [
        {
          "baseYearMonth": "202607",
          "purchasingPowerIndex": 94.2,
          "marketValueIndex": 117.5,
          "decisionSignal": "SEEK_JOB",
          "dataVersion": "20260701093000-000",
          "ruleVersion": "1.2.0",
          "rationale": { "rulesApplied": ["..."], "excluded": ["..."], "baseDate": "2026-07-01" }
        }
      ]
    }
    ```

---

## 6. 시나리오 시뮬레이터 API

### 6.1 잔류 vs 이직 시뮬레이션 계산
*   **Endpoint:** `POST /api/v1/scenarios/simulate`
*   **Request Body:**
    ```json
    {
      "stayExpectedRaiseRate": 3.5,
      "moveTargetGrossSalary": 72000000,
      "moveOneTimeCost": 3000000,
      "expectedUnemploymentMonths": 2,
      "isEligibleForUnemploymentBenefit": true
    }
    ```
    *(`expectedUnemploymentMonths`: 0~6. `isEligibleForUnemploymentBenefit` 기본값 true, false면 실업급여 0으로 계산하고 `실업급여 미반영` 안내 표시. 공식은 [도메인 정의서 §4](domain-logic.md)를 따릅니다)*
*   **Response (200 OK):**
    ```json
    {
      "stayCompensation12m": 62100000,
      "moveCompensation12m": 60960000,
      "estimatedUnemploymentBenefit": 3960000,
      "difference": -1140000,
      "type": "ESTIMATED_OPPORTUNITY_COST",
      "typeLabel": "추정 기회비용",
      "warningNote": "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다."
    }
    ```
    *(예시 산출: 잔류 = 60,000,000 × 1.035 = 62,100,000. 이직 = 72,000,000 × 10/12 + 실업급여 3,960,000 − 3,000,000 = 60,960,000. 차이 = −1,140,000)*
*   **결과 타입 매핑 (고정):**

| 조건 | `type` | `typeLabel` |
| --- | --- | --- |
| `difference >= 0` | `ESTIMATED_GAIN` | 추정 추가보상 |
| `difference < 0` | `ESTIMATED_OPPORTUNITY_COST` | 추정 기회비용 |

*   계산 성공 시 입력 가정과 결과를 사용자별 최근 시나리오(`CareerScenario`)로 저장합니다.

### 6.2 최근 시나리오 조회
*   **Endpoint:** `GET /api/v1/scenarios/latest`
*   **Response (200 OK):** 6.1의 요청 필드와 응답 필드를 합친 단건. 저장된 시나리오가 없으면 `204 No Content`. `calculated_at DESC, id DESC` 순 첫 행 반환.
    ```json
    {
      "inputs": {
        "stayExpectedRaiseRate": 3.5,
        "moveTargetGrossSalary": 72000000,
        "moveOneTimeCost": 3000000,
        "expectedUnemploymentMonths": 2,
        "isEligibleForUnemploymentBenefit": true
      },
      "result": {
        "stayCompensation12m": 62100000,
        "moveCompensation12m": 60960000,
        "estimatedUnemploymentBenefit": 3960000,
        "difference": -1140000,
        "type": "ESTIMATED_OPPORTUNITY_COST",
        "typeLabel": "추정 기회비용",
        "warningNote": "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다."
      },
      "calculatedAt": "2026-07-18T17:00:00Z"
    }
    ```

---

## 7. 지출·스킬·온보딩·계정 API

### 7.1 지출 이력
*   `GET /api/v1/expenses` — 기준월별 지출 기록 목록. 각 항목: `baseYearMonth`, `items[{category, amount, status}]`
*   `PUT /api/v1/expenses/{baseYearMonth}` — 해당 기준월 지출 등록·수정 (6개 카테고리 부분 입력 허용). 미입력 카테고리는 기본 가중치로 보간 (혼합 규칙: [도메인 정의서 §1.1-4](domain-logic.md))
*   상태는 **카테고리별** `status`(`USER_DEFINED` / `DEFAULT_CPI`)로 반환하고, 월 요약 `weightStatus`는 `USER_DEFINED` / `PARTIALLY_ESTIMATED` / `PROPAGATED` / `DEFAULT_CPI`

### 7.2 스킬
*   `GET /api/v1/skills/search?q={query}` — 표준 스킬 사전 검색 (별칭 포함 자동완성)
*   `GET /api/v1/user-skills` — 보유 스킬 목록 (`skillId`, `standardName`, `isCustom`, `monthsOfExperience`, `lastUsedDate`)
*   `POST /api/v1/user-skills` — 스킬 추가. `monthsOfExperience`는 1~600 범위만 허용. 사전에 없으면 `isCustom: true`로 생성하며 이때 `category` 입력 필수 (`BACKEND`/`FRONTEND`/`DEVOPS`/`DATA`/`AI_ML`/`ETC`). 커스텀 스킬은 생성자 전용으로 검색·자동완성에 노출하지 않고, 마지막 사용자가 삭제하면 함께 삭제한다. 동일 정규화 스킬 중복 시 `409 Conflict`
*   `PUT /api/v1/user-skills/{id}` / `DELETE /api/v1/user-skills/{id}` — 수정·삭제

### 7.3 온보딩 상태
*   `GET /api/v1/onboarding` — `{ "currentStep": "EXPENSE", "completed": false }` (`users.onboarding_step` 기반)
*   `PATCH /api/v1/onboarding` — 단계 진행 갱신. `REVIEW` → `COMPLETED` 전환 시 첫 지수 계산을 트리거

### 7.4 소득 이력 수정·삭제
*   `PUT /api/v1/income/{id}` / `DELETE /api/v1/income/{id}` — 유효 시작일 중복 시 `409 Conflict`. 변경 시 영향 유효월부터 스냅샷 재계산 트리거

### 7.5 세션·사용자
*   `POST /api/v1/auth/logout` — 현재 세션 폐기
*   `POST /api/v1/auth/logout-all` — 전체 세션 폐기
*   `DELETE /api/v1/users` — 사용자·개인 데이터 삭제. Request Body에 확인용 `{"email": "..."}` 필수, 불일치 시 `400`
