# Carix REST API Endpoints Specification

이 문서는 Next.js 프론트엔드와 Spring Boot 백엔드 간에 통신할 핵심 REST API 규격을 정의합니다. 모든 API 요청과 응답은 JSON 포맷을 기준으로 하며, 민감 정보(소득, 지출액)의 노출을 차단하도록 아웃풋 모델을 구성했습니다.

---

## 1. 공통 규격

### 1.1 기본 경로 및 인증
*   **Base URL:** `/api/v1`
*   **인증 방식:** HTTP Only Cookie 기반 JWT 세션 인증 (`Authorization` 헤더 또는 쿠키 검증)
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
      "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
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
*   **Endpoint:** `PUT /api/v1/profile`
*   **Request Body:**
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
        "estimateLabel": "SEMI_ESTIMATED"
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
        "rationale": "보유 중인 스킬의 가치는 상승 추세이나, 현재 연봉은 시장 중앙값 대비 18% 낮습니다."
      },
      "metadata": {
        "baseDate": "2026-07-01",
        "sampleSize": 124,
        "salaryMissingRate": 42.5
      }
    }
    ```

### 5.2 지수 트렌드 차트 데이터
*   **Endpoint:** `GET /api/v1/dashboard/trends`
*   **Response (200 OK):**
    ```json
    {
      "trends": [
        {
          "yearMonth": "2026-01",
          "purchasingPowerIndex": 100.0,
          "marketValueIndex": 100.0,
          "nominalIncome": 5000000,
          "realIncome": 5000000
        },
        {
          "yearMonth": "2026-02",
          "purchasingPowerIndex": 99.1,
          "marketValueIndex": 102.3,
          "nominalIncome": 5000000,
          "realIncome": 4955000
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
      "moveOneTimeCost": 3000000
    }
    ```
*   **Response (200 OK):**
    ```json
    {
      "stayCompensation12m": 62100000,
      "moveCompensation12m": 69000000,
      "difference": 6900000,
      "type": "ESTIMATED_OPPORTUNITY_COST",
      "typeLabel": "추정 기회비용",
      "warningNote": "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다."
    }
    ```
