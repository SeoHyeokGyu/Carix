# Carix REST API Endpoints Specification

이 문서는 React(Vite + Node SSR) 프론트엔드와 Spring Boot 백엔드 간에 통신할 핵심 REST API 규격을 정의합니다. 모든 API 요청과 응답은 JSON 포맷을 기준으로 합니다. 소득·지출 금액은 인증된 본인에게만 반환하며, 로그·분석 이벤트·집계 데이터에는 노출하지 않습니다.

---

## 1. 공통 규격

### 1.1 기본 경로 및 인증
*   **Base URL:** `/api/v1`
*   **경유:** 전환 후 모든 요청은 Spring Cloud Gateway 를 거친다. Gateway 가 세션을 검증해
    `X-User-Id` 헤더를 붙이므로 하류 서비스는 세션을 직접 다루지 않는다
    ([Spring Cloud 아키텍처](spring-cloud-architecture.md))
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
*   **성공 응답 포맷 (HTTP 2xx):** core-api의 모든 성공 응답은 아래 공통 포맷으로 한 번 감싸 반환한다.
    **이 문서의 각 API 절 "Response" JSON 예시는 이 중 `data` 필드의 값만 보여준다** — 실제 응답
    본문은 그 값을 아래 포맷으로 한 번 더 감싼 형태다.
    ```json
    {
      "success": true,
      "data": { "...": "각 API 절의 Response 예시가 여기 들어간다" },
      "message": "요청이 성공적으로 처리되었습니다.",
      "timestamp": "2026-07-18T17:00:00Z"
    }
    ```
    *(`data`는 반환할 데이터가 없는 응답(예: 삭제 API)에서는 필드 자체가 생략된다. `message`는 API별로
    다른 안내 문구를 담을 수 있다)*

---

## 2. 인증 API (Authentication)

> **소유 서비스: `auth-service`.** core-api가 아니다 — [spring-cloud-architecture.md](spring-cloud-architecture.md) §1의
> 서비스 분리 결정에 따라 인증은 auth-service가 전담한다. core-api에 이 절의 엔드포인트를
> 재구현하지 않는다.

### 2.1 회원가입
*   **Endpoint:** `POST /api/v1/auth/register`
*   **Request Body:**
    ```json
    {
      "email": "user@example.com",
      "password": "최소 8자"
    }
    ```
*   **Response (200 OK):** §2.3 과 같은 형식. 세션 쿠키를 발급한다.
*   **오류:** 이메일 중복 `409 EMAIL_TAKEN` / 비밀번호 8자 미만 `400`
*   이메일 중복은 숨기지 않는다 — 숨기면 사용자가 가입 실패 원인을 알 수 없다. 대신 IP당 20회/시간 제한으로 이메일 목록 수집을 막는다.
*   이메일 소유를 증명하지 않는다. 확인 메일을 보낼 경로가 없기 때문이다.

### 2.2 로그인
*   **Endpoint:** `POST /api/v1/auth/login`
*   **Request Body:**
    ```json
    {
      "email": "user@example.com",
      "password": "..."
    }
    ```
*   **Response (200 OK):** §2.3 과 같은 형식. 세션 쿠키를 새로 발급한다.
*   **오류:** 없는 이메일과 비밀번호 불일치를 **구분하지 않고** 같은 `401`을 반환한다 (계정 열거 방지).
*   **속도 제한:** 이메일당 10회/시간, IP당 30회/시간. IP만 걸면 분산 대입에 뚫린다.
*   비밀번호 재설정은 제공하지 않는다. 메일 발송 경로가 없기 때문이다.

### 2.3 세션 조회
*   **Endpoint:** `GET /api/v1/auth/session`
*   게이트웨이 세션 검증 필터와 프런트엔드가 함께 쓴다. 필터는 `userId`만 읽는다.
*   **Response (200 OK):**
    ```json
    {
      "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "user@example.com",
      "onboardingStep": "PROFILE"
    }
    ```
*   **오류:** 세션 없음 또는 만료 `401`

### 2.4 로그아웃
*   **Endpoint:** `POST /api/v1/auth/logout` — 현재 세션만 폐기
*   **Endpoint:** `POST /api/v1/auth/logout-all` — 해당 사용자의 전 세션 폐기
*   둘 다 세션 쿠키를 만료시킨다.

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
*   **Query Parameters (모두 선택, 가상 프로필 탐색용) — `MVP 범위 밖`:** 대응 스토리·화면이 없어 파라미터만 MVP 에서 제외한다 ([개발 계획 §5-G](../plans/carix-dev-plan.md)). 엔드포인트 자체는 MVP 포함이다.
    *   `jobFamily` (String, optional): 가상 탐색용 주 직무
    *   `yearsOfExperience` (Integer, optional): 가상 탐색용 경력 년수
    *   `region` (String, optional): 가상 탐색용 지역구
    *   `companySize` (String, optional): 가상 탐색용 회사 규모
    *   (하나라도 지정하면 로그인 사용자의 실제 데이터 대신 해당 가상 프로필 조건 기준 응답을 반환한다)
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
        "rationale": "보유 중인 스킬의 가치는 상승 추세이며, 공식 임금통계 중앙값 대비 연봉 격차가 20%입니다."
      },
      "metadata": {
        "baseDate": "2026-07-01",
        "dataVersion": "20260701093000-000",
        "ruleVersion": "1.2.0",
        "updateDelayed": false,
        "sources": [
          { "source": "SARAMIN", "sampleSize": 124 }
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
          "wageDefinition": "ANNUAL_GROSS_MEDIAN",
          "statVintage": "202606"
        },
        "expenseWeightStatus": "PARTIALLY_ESTIMATED"
      }
    }
    ```
    *(`changeFromLastMonth`는 지수 포인트(p) 차이다. 백분율 변화가 아니다)*

### 5.2 동료집단 연봉 분포
*   **Endpoint:** `GET /api/v1/dashboard/salary-distribution`
*   **Query Parameters (모두 선택, 가상 프로필 탐색용) — `MVP 범위 밖`:** `jobFamily`, `yearsOfExperience`, `region`, `companySize` — 의미와 동작은 §5.1과 동일. 파라미터만 MVP 에서 제외한다 ([개발 계획 §5-G](../plans/carix-dev-plan.md)).
*   결합 분포와 percentile은 원천별 5수치 요약으로 재구성할 수 없으므로, **동료집단 조건의 원 공고(`job_posting`)를 직접 집계**해 계산한다: `dedup_group_id` 단위로 원천 간 중복을 제거하고 유효 공개연봉 행만 사용. `market_aggregate`의 원천별 5수치는 배치 검증·참고용이며 이 응답의 산출원이 아니다.
*   표본 부족 시에도 `200 OK`로 상태와 표본 수를 반환한다 (204 금지 — 화면은 `표본 부족`과 표본 수를 표시해야 함):
    ```json
    { "status": "INSUFFICIENT_SAMPLE", "sampleSize": 4, "minRequired": 10 }
    ```
*   **Response (200 OK):**
    ```json
    {
      "baseline": { "source": "WAGE_STAT", "median": 72000000, "wageDefinition": "ANNUAL_GROSS_MEDIAN" },
      "postingReference": {
        "min": 45000000, "p25": 58000000, "median": 65000000, "p75": 74000000, "max": 95000000,
        "sampleSize": 71, "missingRate": 42.5
      },
      "userPosition": { "grossSalary": 60000000, "percentile": 35 },
      "peerGroup": {
        "fallbackLevel": 3,
        "appliedConditions": "서울 전체 / 경력 MID / 회사규모 무관"
      }
    }
    ```
    *(공고 분포는 참고 표시 전용, 판정 기준선은 `baseline` — 상세 기술 명세 §2.1 규약)*

### 5.3 지수 트렌드 차트 데이터
*   **Endpoint:** `GET /api/v1/dashboard/trends`
*   **Query Parameters:**
    *   `baseYearMonth` (String, optional): 인덱스 기준 월 (YYYY-MM). 지정하지 않으면 사용자의 첫 소득 이력 발생 월을 기준으로 자동 지정됨.
    *   `durationMonths` (Integer, optional): 조회할 개원 수 (기본값: 12).
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
*   추이분석 스킬수요 탭용. 보유 스킬별 수요 지수와 산출 근거를 반환.
*   **Query Parameters (모두 선택, 가상 프로필 탐색용) — `MVP 범위 밖`:** `jobFamily`, `yearsOfExperience`, `region`, `companySize` — 의미와 동작은 §5.1과 동일. 파라미터만 MVP 에서 제외한다 ([개발 계획 §5-G](../plans/carix-dev-plan.md)).
*   **Response (200 OK):**
    ```json
    {
      "skills": [
        {
          "skillId": "3fa85f64-5717-4562-b3fc-2c963f66afa7",
          "standardName": "Spring Boot",
          "demandIndex": 123.4,
          "recentDemandRate": 0.0812,
          "baselineDemandRate": 0.0658,
          "sources": [
            { "source": "SARAMIN", "demandIndex": 123.4, "sampleSize": 124 }
          ],
          "weight": 0.715,
          "status": "OK"
        },
        { "skillId": "3fa85f64-5717-4562-b3fc-2c963f66afa8", "standardName": "CustomSkillX", "status": "CUSTOM_SKILL_NO_STATS" },
        { "skillId": "3fa85f64-5717-4562-b3fc-2c963f66afa9", "standardName": "Airflow", "status": "INSUFFICIENT_SAMPLE", "sampleSize": 3 }
      ],
      "coverage": "보유 스킬 3개 중 1개 반영 (가중치 100% 재정규화)"
    }
    ```

### 5.6 동료집단 기반 스킬 보강 추천
*   **`MVP 범위 밖`** — 대응 스토리·화면이 없다. `S-14` 스킬수요 탭은 §5.5 로 충족한다 ([개발 계획 §5-G](../plans/carix-dev-plan.md)). `core-api` 에 구현이 없다 — 고정값을 돌려주던 스텁은 철거했다. 아래 규격은 재도입 시의 계약으로만 남긴다.
*   **Endpoint:** `GET /api/v1/skills/recommendations`
*   **Response (200 OK):**
    ```json
    {
      "peerGroup": {
        "appliedConditions": "서울 전체 / 경력 MID / 회사규모 무관"
      },
      "recommendations": [
        {
          "standardName": "Kubernetes",
          "category": "DEVOPS",
          "demandRate": 0.245,
          "growthTrend": "UP",
          "reason": "동료집단 내 공고의 24.5%에서 요구하고 있으며, 최근 수요가 급증세입니다."
        },
        {
          "standardName": "TypeScript",
          "category": "FRONTEND",
          "demandRate": 0.182,
          "growthTrend": "STABLE",
          "reason": "동료집단 내 공고의 18.2%에서 요구되는 핵심 스택입니다."
        }
      ]
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
      "scenarioName": "A사 오퍼 제안",
      "currentGrossSalary": 60000000,
      "stayExpectedRaiseRate": 3.5,
      "moveTargetGrossSalary": 72000000,
      "moveOneTimeCost": 3000000,
      "expectedUnemploymentMonths": 2,
      "isEligibleForUnemploymentBenefit": true
    }
    ```
    *(`scenarioName`: 선택 입력. 저장할 가상 시나리오의 별칭. 생략 시 기본명 부여됨)*
    *(`currentGrossSalary`: 선택 입력. 생략 시 사용자의 최신 소득 이력 값을 사용하며, 가상 시뮬레이션을 위해 임의의 세전 연봉을 지정하여 테스트할 수 있습니다)*
    *(`expectedUnemploymentMonths`: 0~6. `isEligibleForUnemploymentBenefit` 기본값 true, false면 실업급여 0으로 계산하고 `실업급여 미반영` 안내 표시. 공식은 [도메인 정의서 §4](domain-logic.md)를 따릅니다)*
*   **Response (200 OK):**
    ```json
    {
      "id": "e812d8a4-0cfb-4a5f-9dfb-12d88c91350c",
      "scenarioName": "A사 오퍼 제안",
      "stayCompensation12m": 62100000,
      "moveCompensation12m": 60960000,
      "estimatedUnemploymentBenefit": 3960000,
      "difference": -1140000,
      "type": "ESTIMATED_OPPORTUNITY_COST",
      "typeLabel": "추정 기회비용",
      "warningNote": "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
      "feasibility": {
        "feasibilityScore": 31,
        "targetPercentile": 69,
        "difficultyGrade": "CHALLENGING",
        "feedbackMessage": "목표 연봉은 동료집단 내 상위 31% 수준의 도전적인 목표입니다. 시장 평균 범위 내에 위치하여 원만히 조율 가능한 수준입니다."
      }
    }
    ```
    *(예시 산출: 잔류 = 60,000,000 × 1.035 = 62,100,000. 이직 = 72,000,000 × 10/12 + 실업급여 3,960,000 − 3,000,000 = 60,960,000. 차이 = −1,140,000)*
    *(`feasibility`: 이직 목표 연봉이 §5.2 동료집단 공고 분포에서 몇 번째 백분위(`targetPercentile`)에 해당하는지로 실현 난이도를 계산해 함께 반환한다. `difficultyGrade`는 `EASY`/`REALISTIC`/`CHALLENGING`/`VERY_HIGH_CHALLENGE` 중 하나)*
*   **결과 타입 매핑 (고정):**

| 조건 | `type` | `typeLabel` |
| --- | --- | --- |
| `difference >= 0` | `ESTIMATED_GAIN` | 추정 추가보상 |
| `difference < 0` | `ESTIMATED_OPPORTUNITY_COST` | 추정 기회비용 |

*   계산 성공 시 입력 가정과 결과를 사용자별 시나리오(`CareerScenario`) 목록에 저장합니다.

### 6.2 최근 시나리오 조회
*   **Endpoint:** `GET /api/v1/scenarios/latest`
*   **Response (200 OK):** 6.1의 요청 필드와 응답 필드를 합친 단건. 저장된 시나리오가 없으면 `204 No Content`. `calculated_at DESC, id DESC` 순 첫 행 반환.
    ```json
    {
      "id": "e812d8a4-0cfb-4a5f-9dfb-12d88c91350c",
      "scenarioName": "A사 오퍼 제안",
      "inputs": {
        "scenarioName": "A사 오퍼 제안",
        "currentGrossSalary": 60000000,
        "stayExpectedRaiseRate": 3.5,
        "moveTargetGrossSalary": 72000000,
        "moveOneTimeCost": 3000000,
        "expectedUnemploymentMonths": 2,
        "isEligibleForUnemploymentBenefit": true
      },
      "result": {
        "id": "e812d8a4-0cfb-4a5f-9dfb-12d88c91350c",
        "scenarioName": "A사 오퍼 제안",
        "stayCompensation12m": 62100000,
        "moveCompensation12m": 60960000,
        "estimatedUnemploymentBenefit": 3960000,
        "difference": -1140000,
        "type": "ESTIMATED_OPPORTUNITY_COST",
        "typeLabel": "추정 기회비용",
        "warningNote": "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
        "feasibility": {
          "feasibilityScore": 31,
          "targetPercentile": 69,
          "difficultyGrade": "CHALLENGING",
          "feedbackMessage": "목표 연봉은 동료집단 내 상위 31% 수준의 도전적인 목표입니다. 시장 평균 범위 내에 위치하여 원만히 조율 가능한 수준입니다."
        }
      },
      "calculatedAt": "2026-07-18T17:00:00Z"
    }
    ```
    *(`inputs`·`result`가 각각 §6.1 요청·응답 레코드 전체를 그대로 담으므로 `scenarioName`은 최상위·`inputs` 양쪽에, `id`·`scenarioName`은 최상위·`result` 양쪽에 중복 노출된다)*

### 6.3 가상 시나리오 전체 목록 조회 (비교용)
*   **`MVP 범위 밖`** — `US-S1` 은 마지막 시나리오 복원만 요구하고 §6.2 로 충족된다. 목록 비교 화면이 없다 ([개발 계획 §5-G](../plans/carix-dev-plan.md)). `core-api` 에 구현이 없다 — 고정값을 돌려주던 스텁은 철거했다. 아래 규격은 재도입 시의 계약으로만 남긴다.
*   **Endpoint:** `GET /api/v1/scenarios`
*   **Response (200 OK):** 저장된 모든 시나리오 리스트를 반환합니다.
    ```json
    [
      {
        "id": "e812d8a4-0cfb-4a5f-9dfb-12d88c91350c",
        "scenarioName": "A사 오퍼 제안",
        "inputs": {
          "scenarioName": "A사 오퍼 제안",
          "currentGrossSalary": 60000000,
          "stayExpectedRaiseRate": 3.5,
          "moveTargetGrossSalary": 72000000,
          "moveOneTimeCost": 3000000,
          "expectedUnemploymentMonths": 2,
          "isEligibleForUnemploymentBenefit": true
        },
        "result": {
          "id": "e812d8a4-0cfb-4a5f-9dfb-12d88c91350c",
          "scenarioName": "A사 오퍼 제안",
          "stayCompensation12m": 62100000,
          "moveCompensation12m": 60960000,
          "estimatedUnemploymentBenefit": 3960000,
          "difference": -1140000,
          "type": "ESTIMATED_OPPORTUNITY_COST",
          "typeLabel": "추정 기회비용",
          "warningNote": "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
          "feasibility": {
            "feasibilityScore": 31,
            "targetPercentile": 69,
            "difficultyGrade": "CHALLENGING",
            "feedbackMessage": "목표 연봉은 동료집단 내 상위 31% 수준의 도전적인 목표입니다. 시장 평균 범위 내에 위치하여 원만히 조율 가능한 수준입니다."
          }
        },
        "calculatedAt": "2026-07-18T17:00:00Z"
      }
    ]
    ```
    *(구조는 §6.2와 동일 — `inputs`·`result` 내부 필드 중복은 §6.2 설명 참조)*

### 6.4 가상 시나리오 이력 삭제
*   **`MVP 범위 밖`** — 시나리오 이력 삭제 화면이 없다. 계정 삭제는 `DELETE /users` 연쇄로 처리한다 ([개발 계획 §5-G](../plans/carix-dev-plan.md)). `core-api` 에 구현이 없다 — 고정값을 돌려주던 스텁은 철거했다. 아래 규격은 재도입 시의 계약으로만 남긴다.
*   **Endpoint:** `DELETE /api/v1/scenarios/{id}`
*   **Response (200 OK):** 삭제 완료.

### 6.5 가상 소득/소비 변화에 따른 지수 변화 예측 (Index Predictor)
*   **`MVP 범위 밖`** — 대응 스토리가 없고 MVP 규격 §2.3 이 확정적 예측을 제외 기능으로 둔다 ([개발 계획 §5-G](../plans/carix-dev-plan.md)). `core-api` 에 구현이 없다 — 고정값을 돌려주던 스텁은 철거했다. 아래 규격은 재도입 시의 계약으로만 남긴다.
*   **Endpoint:** `POST /api/v1/dashboard/predict`
*   **Request Body:**
    ```json
    {
      "futureExpectedRaiseRate": 5.0,
      "futureMonthlyExpenseChange": -500000
    }
    ```
    *(`futureExpectedRaiseRate`: 세전 연봉 인상 예상 비율 (%), `futureMonthlyExpenseChange`: 월평균 소비액 증감액 (원, 마이너스는 절감))*
*   **Response (200 OK):**
    ```json
    {
      "currentPurchasingPowerIndex": 94.2,
      "predictedPurchasingPowerIndex": 102.8,
      "indexDifference": 8.6,
      "changeStatus": "INCREASED",
      "rationale": "연봉이 5.0% 인상되고 월 지출을 500,000원 절감하면 실질 가처분 소득이 증가하여, 구매력 지수가 현재 94.2에서 102.8로 약 8.6p 상승할 것으로 추정됩니다."
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
*   `GET /api/v1/user-skills` — 보유 스킬 목록 (`id` (등록 매핑 ID), `skillId` (표준 스킬 ID), `standardName`, `isCustom`, `monthsOfExperience`, `lastUsedDate`)
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
*   `DELETE /api/v1/users` — 사용자·개인 데이터 삭제. 쿼리 스트링으로 확인용 `?email=...` 필수 (일부 네트워크 환경에서 HTTP DELETE Body 유실 문제 방지), 이메일 불일치 시 `400`

### 7.6 공통 기준 데이터 조회 (Reference Data)
*   **`MVP 범위 밖`** — `US-O4` 는 카테고리별 `status` 뱃지만 요구한다. 화면이 기본값 금액까지 표시하기로 하면 MVP 로 승격한다 ([개발 계획 §5-G](../plans/carix-dev-plan.md)). `core-api` 에 구현이 없다 — 고정값을 돌려주던 스텁은 철거했다. 아래 규격은 재도입 시의 계약으로만 남긴다.
*   `GET /api/v1/reference/default-expenses` — 통계청 기준 지출 가중치 및 금액 기본값 조회 (사용자가 지출을 입력하지 않은 카테고리의 대체 기본 정보를 화면에 보여주기 위해 프론트엔드가 호출).
    *   **Response (200 OK):**
        ```json
        {
          "version": "2025-v1",
          "categories": [
            { "category": "HOUSING", "weight": 0.174, "averageAmount": 511000 },
            { "category": "FOOD", "weight": 0.142, "averageAmount": 417000 },
            { "category": "TRANSPORTATION", "weight": 0.123, "averageAmount": 361000 },
            { "category": "HEALTH", "weight": 0.081, "averageAmount": 238000 },
            { "category": "EDUCATION", "weight": 0.075, "averageAmount": 220000 },
            { "category": "OTHER", "weight": 0.405, "averageAmount": 1192000 }
          ]
        }
        ```

### 7.7 소비자물가지수(CPI) 기준 데이터 조회
*   **`MVP 범위 밖`** — `US-D3` 는 `incomeIndex`·`personalPriceIndex` 2계열만 요구한다 ([개발 계획 §5-G](../plans/carix-dev-plan.md)). `core-api` 에 구현이 없다 — 고정값을 돌려주던 스텁은 철거했다. 아래 규격은 재도입 시의 계약으로만 남긴다.
*   `GET /api/v1/reference/cpi` — 월별 카테고리별 통계청 소비자물가지수(CPI) 변동 추이 목록 조회 (개인물가지수 추세의 기준 자료 시각화용).
    *   **Response (200 OK):**
        ```json
        {
          "baseYear": "2020",
          "data": [
            {
              "yearMonth": "202606",
              "categories": [
                { "category": "HOUSING", "cpiValue": 108.5 },
                { "category": "FOOD", "cpiValue": 115.2 },
                { "category": "TRANSPORTATION", "cpiValue": 105.1 },
                { "category": "HEALTH", "cpiValue": 102.3 },
                { "category": "EDUCATION", "cpiValue": 103.8 },
                { "category": "OTHER", "cpiValue": 109.4 }
              ]
            }
          ]
        }
        ```

---

## 8. 관리자 파이프라인 제어 API (Admin / Internal)

수집기(Collector)의 공고 수집, KOSIS 임금통계 보간, 공고 중복제거, 다차원 시장 집계 등 백그라운드 파이프라인을 수동 트리거하고 상태를 모니터링하는 API다 (관리자 콘솔 `/admin/collector` 연동).

*   **Gateway 경유 Base URL:** `/api/v1/admin/collector` (내부 경로 `/internal/pipeline`으로 자동 Rewrite 매핑)
*   **보안 인증:** 모든 요청에 요청 헤더 `X-Admin-Key: <ADMIN_SECRET_KEY>` 필수 전송 (누락 또는 불일치 시 `403 FORBIDDEN`).

### 8.1 채용공고 크롤링 비동기 실행
*   `POST /internal/pipeline/crawl` — 잡코리아·사람인 IT/개발 채용공고 실시간 스크래핑 트리거.
    *   **Request Body (선택):**
        ```json
        {
          "portal": "ALL",
          "maxPages": 10,
          "delayMs": 500
        }
        ```
    *   **Response (202 Accepted):**
        ```json
        {
          "jobId": "c4b12345-6789-4abc-def0-1234567890ab",
          "jobType": "CRAWL",
          "status": "RUNNING",
          "message": "크롤링 작업이 백그라운드에서 시작되었습니다."
        }
        ```

### 8.2 KOSIS 임금통계 수집 및 보간 적재
*   `POST /internal/pipeline/wage-stat` — KOSIS 임금 통계 조회 및 24계층 누적분포 보간 적재 트리거.
    *   **Request Body (Optional, 기간 지정 백필 시):**
        ```json
        {
          "startYear": "2020",
          "endYear": "2024"
        }
        ```
        *(생략 또는 빈 본문 전송 시 최신 1개 연도만 수집)*
    *   **Response (202 Accepted):**
        ```json
        {
          "jobId": "w1a12345-6789-4abc-def0-1234567890cd",
          "jobType": "WAGE_STAT_INGEST",
          "status": "ACCEPTED",
          "message": "KOSIS 임금 통계 수집 작업이 수락되어 백그라운드에서 시작되었습니다."
        }
        ```

### 8.3 채용공고 중복제거 배치 실행
*   `POST /internal/pipeline/dedup` — 채용공고 Jaccard 유사도 + Union-Find 클러스터링 기반 중복제거 배치 트리거.
    *   **Response (202 Accepted):**
        ```json
        {
          "jobId": "d2e12345-6789-4abc-def0-1234567890ef",
          "jobType": "DEDUPLICATION",
          "status": "RUNNING",
          "message": "중복제거 배치가 시작되었습니다."
        }
        ```

### 8.4 채용 시장 데이터 다차원 사전집계 실행
*   `POST /internal/pipeline/aggregate` — 직군×경력×지역×기업규모×스킬별 공고 수, 분위수 연봉, 수요율 다차원 사전집계(`market_aggregate`) 트리거.
    *   **Request Body (Optional, 단일월/연도/구간/전체 집계 시):**
        *   **1) 전체 일괄 집계 (기본값 또는 `all=true`):**
            ```json
            { "all": true }
            ```
            *(본문 생략 시에도 DB에 적재된 모든 공고 데이터의 활동 기준월을 자동 추출하여 전체 일괄 집계)*
        *   **2) 특정 단일 연도 전체(1~12월) 집계:**
            ```json
            { "year": "2025" }
            ```
        *   **3) 연도 구간(startYear ~ endYear) 집계:**
            ```json
            { "startYear": "2020", "endYear": "2025" }
            ```
        *   **4) 특정 단일 월 집계 (Query Param 또는 Body):**
            ```json
            { "month": "202608" }
            ```
            *(하위 호환 쿼리 파라미터 `POST /internal/pipeline/aggregate?month=2026-08` 지원)*
    *   **Response (202 Accepted):**
        ```json
        {
          "jobId": "a3f12345-6789-4abc-def0-123456789012",
          "jobType": "MARKET_AGGREGATION",
          "status": "ACCEPTED",
          "message": "시장 사전 집계 작업이 수락되어 백그라운드에서 시작되었습니다."
        }
        ```

### 8.5 파이프라인 작업 상태 조회
*   `GET /internal/pipeline/jobs/{jobId}` — 작업 단건 진행 상태 및 완료 결과 조회.
    *   **Response (200 OK):**
        ```json
        {
          "jobId": "c4b12345-6789-4abc-def0-1234567890ab",
          "jobType": "CRAWL",
          "status": "COMPLETED",
          "startTime": "2026-08-29T13:00:00Z",
          "endTime": "2026-08-29T13:05:12Z",
          "durationMs": 312000,
          "result": {
            "ingestedCount": 150,
            "failedCount": 0
          }
        }
        ```

### 8.6 최근 작업 실행 이력 및 현황 통계 조회
*   `GET /internal/pipeline/jobs` — 최근 100건의 파이프라인 실행 이력 목록 조회.
*   `GET /internal/pipeline/stats` — 전체 DB 적재 건수 요약 통계 조회 (원천별 공고 수, 정제 공고 수, 임금통계 관측치 수).
    *   **Response (200 OK):**
        ```json
        {
          "totalRawIngest": 12500,
          "totalJobPostings": 11800,
          "totalSkills": 340,
          "totalWageStats": 24,
          "postingsBySource": {
            "JOBKOREA": 6000,
            "SARAMIN": 5800
          }
        }
        ```

### 8.7 최근 수집 공고 미리보기 조회
*   `GET /internal/pipeline/recent-postings?limit={limit}` — 가장 최근 수집 및 정규화된 채용공고 목록 및 스킬 매핑 결과 조회 (기본 20건, 최대 50건).
    *   **Response (200 OK):**
        ```json
        [
          {
            "id": "c1a12345-6789-4abc-def0-123456789012",
            "source": "JOBKOREA",
            "externalId": "JK-42819012",
            "title": "백엔드 스프링부트 시니어 개발자 채용",
            "companyName": "카릭스랩",
            "jobFamily": "BACKEND",
            "salaryMin": 5000,
            "salaryMax": 8000,
            "region": "서울 강남구",
            "experienceMin": 5,
            "experienceMax": 10,
            "skills": ["Java", "Spring Boot", "PostgreSQL", "Docker"],
            "collectedAt": "2026-09-05T17:30:00Z"
          }
        ]
        ```
