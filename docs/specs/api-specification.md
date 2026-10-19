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
*   `region` 은 시/도 17개 한글 어휘다(`서울`·`부산`·`경기`… 전체 목록은 collector 의
    `RegionRollup.SIDO`). 구 단위(`서울 강남구`)가 아니다 — 동료집단 완화가 기대하는 값이
    이 어휘다. 현재 `market_aggregate` 조회는 `(m.region = :region OR m.region = 'ALL')`로
    떨어져 있어 값이 어긋나도 `ALL` 행에 걸려 증상이 바로 드러나지는 않지만
    (`MarketAggregateRepository` 의 조회 둘), 값 규약은 이 어휘로 확정한다
*   **Response (200 OK):**
    ```json
    {
      "jobFamily": "DEVELOPER",
      "yearsOfExperience": 5,
      "region": "서울",
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
      "region": "서울",
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
        "effectiveDate": "2026-01-01",
        "changeReason": "JOB_CHANGE",
        "selectedChangeReason": "JOB_CHANGE"
      },
      {
        "id": "fa91b5c4-2dfb-402a-bf39-44ef8c91361c",
        "grossSalary": 55000000,
        "netSalary": 3900000,
        "effectiveDate": "2025-01-01",
        "changeReason": "INITIAL",
        "selectedChangeReason": null
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
      "effectiveDate": "2026-07-01",
      "changeReason": "RAISE"
    }
    ```
*   **필드 규격 및 화면 입력 정책:**
    *   `grossSalary` (Long, 필수): 세전 연봉(원 단위 정수). 화면에서는 사용자 입력 편의를 위해 **만원 단위**로 입력받으며, 천 단위 콤마(`,`) 실시간 서식과 실시간 원화 환산 힌트(`= N원`)를 제공하고 제출 시 원 단위로 변환해 전송한다.
    *   `netSalary` (Long, 선택): 월 실수령액(원 단위 정수). 화면에서는 만원 단위 및 천 단위 콤마 서식을 제공한다.
    *   `effectiveDate` (String, YYYY-MM-DD): 소득 적용 기준일. 화면에서는 사용자가 직관적으로 이해할 수 있도록 **"연봉 적용 시작일"**로 표기한다.
    *   `changeReason` (String, 선택): `RAISE` · `JOB_CHANGE`. 요청값은 컬럼에 그대로 저장되고 응답의 `selectedChangeReason` 으로 돌아온다 — 그 사용자의 첫 소득 행이라 해도 요청값을 무시하거나 지우지 않는다. 다만 **응답의 `changeReason`(표시값)은 그 행이 `min(effectiveDate)` 이면 요청값과 무관하게 항상 `INITIAL` 이다.** 미지정은 `null` 로 저장한다 — 기본값을 채우면 고르지 않은 것이 고른 것으로 기록된다 ([기능 명세 US-O3](functional-specification.md)).
    *   `JOB_CHANGE` 는 12개월 뒤 시나리오 예측을 실제 소득과 맞댈 때 **잔류 예측과 이직 예측 중 어느 쪽을 검증할지**를 가른다. 자기 신고값이며 그 한계는 검증 결과에 함께 표기한다.
    *   **`INITIAL` 은 저장값이 아니다.** 컬럼(`income_history.change_reason`)에는 `RAISE`·`JOB_CHANGE`·`null` 만 들어가고, 응답의 `changeReason` 은 그 사용자의 `min(effectiveDate)` 행일 때 서버가 `INITIAL` 을 얹은 표시값이다. 요청에 `INITIAL` 을 실으면 `400 INVALID_INPUT` 이다. 저장하면 사용자가 더 이른 과거 이력을 넣을 때마다 값을 행 사이로 옮겨야 하고, 그 갱신이 이미 그 행에 있던 선택값을 덮는다 ([ERD 스키마 설계 주석](erd.md)).
    *   `selectedChangeReason` (String, 응답 전용): 컬럼 원값 그대로(`RAISE`·`JOB_CHANGE`·`null`). 편집 화면이 이것으로 선택 컨트롤을 채운다. `changeReason` 만 내려주면 `INITIAL` 로 가려진 원값을 폼이 볼 수 없어, `PUT` 이 전체 교체인 탓에 다른 필드만 고쳐 저장해도 사용자가 골랐던 값이 지워진다.
*   **Response (201 Created):** 등록 완료된 단건 반환

---

## 5. 대시보드 및 지표 API

### 5.1 대시보드 지표 요약 (Summary)
*   **Endpoint:** `GET /api/v1/dashboard/summary`
*   구현: `com.carix.dashboard.service.DashboardSummaryService`. `index_snapshot` 의 그 달 유효 행을 읽는다.
*   `metadata` 의 원천별 커버리지는 `index_snapshot.rationale.sourceCoverages` 에서 온다 ([도메인 정의서 §5](domain-logic.md)). 완화 결과는 당시 프로필에 의존해 나중에 재계산하면 같은 값이 안 나오므로 저장분을 그대로 읽는다. `salaryMissingRate` 는 원천별 결측률을 **표본 수로 가중 평균**한 값이고, `peerGroup` 은 표본이 가장 큰 원천의 안착 조건이다. 완화에 성공한 원천이 없으면 네 필드 모두 `null`·빈 배열이다. 원천 `source` 값은 `JOBKOREA`·`SARAMIN`·`WANTED`·`JUMPIT` 이고, 이전 창이 비었거나 표본이 모자란 원천은 `신규 관측`·`표본 부족`으로 커버리지에 드러나며 그 원천만 결합에서 빠진다([도메인 정의서 §6.2](domain-logic.md)).
*   `openingWindowIndex` 는 전체 신규 수집 공고 중 동료집단 공고의 비중을 최근 3개월 대 이전 12개월(4~15개월 전) 비율로 100 기준 환산한 값이다(화면 표시명: **채용 활발도**, [기능 명세 §4.5](functional-specification.md)). 근거 3종은 `metadata.openingWindow` 에 담는다 — `recentSharePercent`(최근 3개월 동료집단 비중 %), `baselineSharePercent`(이전 12개월 동료집단 비중 %), `peerSampleSize`(최근 3개월 동료집단 표본 수). **이전 12개월 표본이 최소 기준 미달(수집 이력 15개월 미만 포함)이면 `indexStatus` 가 `INSUFFICIENT_SAMPLE` 이고 `value` 는 `null` 이며, 최근 3개월 표본이 기준 이상이면 `recentSharePercent` 만 채워진다.**
*   **`metadata.wageStat` 은 축 둘을 따로 싣는다.** `basis` 는 **기준선의 성격**이다 — `AGE_BAND_PROXY` 는 KOSIS 연령대를 경력구간으로 옮긴 값이라는 뜻이고, 오늘은 모든 행이 여기 해당해 컬럼 없이 상수로 낸다(경력별 실측 원천이 생기면 그때 축이 살아난다). `source` 는 **그 행의 출처 종류다** — `core.wage_stat_observation.source` 컬럼을 그대로 싣는다 — `KOSIS`(24계층 누적분포 보간 실측), `FALLBACK`(하드코딩 상수), `UNKNOWN`(출처 컬럼 도입 이전 적재분). 화면은 이 기준선으로 낸 격차·신호에 두 값을 함께 표시한다 — 프록시 위의 판정도, 상수 위의 판정도 출처 없이 내보내지 않는다 (개발 계획 §2 `b12`).
*   `negotiationTargets` 는 결정 신호가 `NEGOTIATE`·`SEEK_JOB` 이고 기준월이 가장 최근 스냅샷 달일 때 화면이 쓰는 목표 인상률 셋이다 ([기능 명세 US-D7·§4.8](functional-specification.md)). 그 외에는 블록 자체가 `null` 이다. 각 항목은 `raiseRatePercent`(소수 첫째 자리 `HALF_UP`)와 `status` 를 갖는다 — `BELOW` 는 양수 인상률, `ABOVE` 는 이미 넘어섬, `NONE` 은 복구선 전용으로 마지막 연봉 변경 이후 물가 상승분 없음이고, `ABOVE`·`NONE` 이면 `raiseRatePercent` 는 `null` 이다. 반올림 결과가 0.0 이하면 `ABOVE`·`NONE` 이다. 근거가 없으면 항목이 `null` 이다 — 현재 연봉 없음, `toWageStatMedian` 은 지금 기준선이나 스냅샷 판정 때 기준선(`rationale.wageStatSource`)의 출처가 `KOSIS` 가 아니거나 다시 구한 격차 구간이 스냅샷 구간과 다를 때, `toPeerP75` 는 동료집단 공고·공개연봉 10건 미만일 때, `toPurchasingPowerRecovery` 는 CPI 가 2개월 넘게 늦거나 카테고리가 빠질 때. `toPurchasingPowerRecovery.sinceYearMonth` 는 누적을 시작한 달(마지막 연봉 변경 달, CPI 수집 전이면 CPI 첫 달)이다. 목표 금액은 싣지 않는다. **이 값은 `index_snapshot.rationale` 에 저장하지 않는다** — 같은 응답의 기준선 금액과 조인하면 연봉이 원 단위로 복원되며, 저장은 §1.3 소득 격차가 막은 것과 같은 경로를 연다.
*   `metadata.topPriceDriver` 는 그 사용자의 물가 상승에 가장 크게 기여한 6대 카테고리 중 1위다 ([기능 명세 US-D1·§4.3](functional-specification.md)). **원자료 CPI 가 아니라 `카테고리 가중치 × 품목 CPI 변화` 로 계산된 값**이라 사용자 가중치를 아는 PPI 엔진만 만들 수 있다. 산출 불가면 `null` 이고 화면은 뱃지를 생략한다. `estimated` 는 그 카테고리 가중치가 `DEFAULT_CPI` 로 채워졌는지다. 변화는 **전년 동월 대비**다. `cpiMonth` 는 사용한 CPI 기준월이고, 대상 월보다 2개월 넘게 오래된 CPI 로는 계산하지 않는다. 여섯 카테고리 중 하나라도 계산할 수 없으면 `null` 이다.
*   `decisionSignal.rationale` 은 격차 **구간** 문구다 — 비율을 그대로 실으면 임금통계 중앙값과 조인해 연봉이 원 단위로 복원된다 ([도메인 정의서 §5](domain-logic.md)).
*   **Query Parameters (모두 선택, 가상 프로필 탐색용) — `MVP 범위 밖`:** 대응 스토리·화면이 없어 파라미터만 MVP 에서 제외한다 ([개발 계획 §4](../plans/carix-dev-plan.md)). 엔드포인트 자체는 MVP 포함이다.
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
        "indexStatus": "OK",
        "estimateLabel": "PARTIALLY_ESTIMATED"
      },
      "marketValueIndex": {
        "value": null,
        "changeFromLastMonth": null,
        "status": null,
        "indexStatus": "INSUFFICIENT_SAMPLE"
      },
      "openingWindowIndex": {
        "value": 118.4,
        "changeFromLastMonth": 3.1,
        "status": "INCREASED",
        "indexStatus": "OK",
        "estimateLabel": null
      },
      "negotiationTargets": {
        "toWageStatMedian":          { "raiseRatePercent": 20.0, "status": "BELOW" },
        "toPeerP75":                 { "raiseRatePercent": null, "status": "ABOVE" },
        "toPurchasingPowerRecovery": { "raiseRatePercent": 4.3,  "status": "BELOW", "sinceYearMonth": "202503" }
      },
      "decisionSignal": {
        "code": "SEEK_JOB",
        "label": "이직 탐색",
        "rulesApplied": [
          "연봉 격차 15% 이상",
          "보유 스킬 시장수요 상승세"
        ],
        "rationale": "공식 임금통계 중앙값보다 15% 이상 낮습니다."
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
          "statVintage": "202606",
          "basis": "AGE_BAND_PROXY",
          "source": "KOSIS"
        },
        "expenseWeightStatus": "PARTIALLY_ESTIMATED",
        "topPriceDriver": { "category": "TRANSPORT", "label": "교통 부담률 최대 상승", "estimated": false, "cpiMonth": "202609" },
        "openingWindow": {
          "recentSharePercent": 24.5,
          "baselineSharePercent": 20.7,
          "peerSampleSize": 86
        }
      }
    }
    ```
    *(`changeFromLastMonth`는 지수 포인트(p) 차이다. 백분율 변화가 아니다)*
*   **계산 불가 표현.** `value`·`changeFromLastMonth`·`status`는 nullable 이고, 값이 없는 이유는
    `indexStatus`가 담는다 — `OK` / `INSUFFICIENT_SAMPLE` / `UPDATE_DELAYED` /
    `INSUFFICIENT_HISTORY` / `NOT_AVAILABLE` (`com.carix.index.enums.IndexStatus`).
    **0 이나 100 으로 채우지 않는다** ([API 규격 §5.1](api-specification.md),
    `index_snapshot.purchasing_power_index` DDL 주석). 위 예시는 PPI 는 산출됐고 MVI 는 표본이
    부족한 경우다. 값이 있는 상태는 `OK` 와 `INSUFFICIENT_HISTORY`(구매력 지수 기준월부터 12개월 미만 —
    값과 함께 실린다) 둘뿐이다. 화면은 `indexStatus`로 카드별 뱃지를 그린다 ([기능 명세 §5](functional-specification.md)).

### 5.2 동료집단 연봉 분포
*   **Endpoint:** `GET /api/v1/dashboard/salary-distribution`
*   구현: `com.carix.dashboard.service.SalaryDistributionService`. 완화 5단계를 원 공고 위에서 돌리고 백분위는 nearest-rank 로 낸다 — collector 의 `MarketAggregateService` 와 같은 방식이라야 두 경로의 숫자가 어긋나지 않고 H2/Postgres 분위수 함수 차이도 타지 않는다.
*   **Query Parameters (선택):**
    *   `baseYearMonth` (String, optional): 대시보드 기준 연월 (YYYY-MM 또는 YYYYMM). 미지정 시 최근 유효 등록월/당월 기준.
    *   `year` (String, optional): 경력 사다리 기준 연도 (YYYY). 미지정 시 `baseYearMonth` 연도 또는 최근 기준선 연도 적용 ([개발 계획 §2 `b14`](../plans/carix-dev-plan.md)).
    *   *가상 프로필 탐색용 (MVP 범위 밖)*: `jobFamily`, `yearsOfExperience`, `region`, `companySize` — 의미와 동작은 §5.1과 동일. 파라미터만 MVP 에서 제외한다 ([개발 계획 §4](../plans/carix-dev-plan.md)).
*   결합 분포와 percentile은 원천별 5수치 요약으로 재구성할 수 없으므로, **동료집단 조건의 원 공고(`job_posting`)를 직접 집계**해 계산한다: `dedup_group_id` 단위로 원천 간 중복을 제거하고 유효 공개연봉 행만 사용. `market_aggregate` 에는 연봉 5수치를 두지 않는다 — 원천별 행은 중복 미제거라 이 응답과 모수가 다르다. **연봉 숫자의 정본은 이 경로 하나다**(개발 계획 `b11`). `salary_missing_rate` 는 `demand_rate` 신뢰도 지표라 집계에 남는다. 이 응답의 산출원이 아니다.
*   표본 부족은 동료집단 공고 < 10 **또는** 그중 공개연봉 < 10 이다. 완화 사다리는 두 수가 모두 10건을 채우는 단계에 안착하고, 공고만 채우면 한 단계 더 넓힌다(스킬 추천의 동료집단은 공고 수만 본다).
*   표본 부족 시에도 `200 OK`로 상태와 표본 수를 반환한다 (204 금지 — 화면은 `표본 부족`과 표본 수를 표시해야 함). `sampleSize` 는 동료집단 공고 수다.
    `userPosition` 은 `null` 이다. `postingReference` 는 공고가 모자라면 `null`, 공고는 충분한데 공개연봉이 모자라면 5수치가 모두 `null` 이고 `sampleSize`(공개연봉 수)·`missingRate` 만 있는 객체다(예: `{ "min": null, "p25": null, "median": null, "p75": null, "max": null, "sampleSize": 5, "missingRate": 97.5 }`). 공고가 모자란 경우의 예:
    ```json
    {
      "status": "INSUFFICIENT_SAMPLE",
      "sampleSize": 4,
      "minRequired": 10,
      "baseline": { "basis": "AGE_BAND_PROXY", "source": "KOSIS", "median": 72000000, "wageDefinition": "초과급여 제외, 상여 포함" },
      "postingReference": null,
      "userPosition": null,
      "peerGroup": { "fallbackLevel": null, "appliedConditions": "서울 / 경력 MID±1 / 회사규모 무관" },
      "careerLadder": {
        "userBracket": "MID",
        "basePeriod": "2025",
        "availableYears": ["2023", "2024", "2025"],
        "steps": [
          { "bracket": "ENTRY", "median": 31200000, "raiseFromPrevious": null, "source": "FALLBACK" },
          { "bracket": "JUNIOR", "median": 39600000, "raiseFromPrevious": 26.9, "source": "KOSIS" },
          { "bracket": "MID", "median": 51600000, "raiseFromPrevious": 30.3, "source": "KOSIS" },
          { "bracket": "SENIOR", "median": 62400000, "raiseFromPrevious": 20.9, "source": "KOSIS" },
          { "bracket": "LEAD", "median": 74400000, "raiseFromPrevious": 19.2, "source": "KOSIS" }
        ]
      },
      "trend": [],
      "trendComparison": null
    }
    ```
*   **Response (200 OK):**
    ```json
    {
      "status": "OK",
      "sampleSize": 71,
      "minRequired": null,
      "baseline": { "basis": "AGE_BAND_PROXY", "source": "KOSIS", "median": 72000000, "wageDefinition": "ANNUAL_GROSS_MEDIAN" },
      "postingReference": {
        "min": 45000000, "p25": 58000000, "median": 65000000, "p75": 74000000, "max": 95000000,
        "sampleSize": 41, "missingRate": 42.5
      },
      "userPosition": { "grossSalary": 60000000, "percentile": 35 },
      "peerGroup": {
        "fallbackLevel": 3,
        "appliedConditions": "서울 전체 / 경력 MID / 회사규모 무관"
      },
      "careerLadder": {
        "userBracket": "MID",
        "basePeriod": "2025",
        "availableYears": ["2023", "2024", "2025"],
        "steps": [
          { "bracket": "ENTRY",  "median": 31200000, "raiseFromPrevious": null, "source": "FALLBACK" },
          { "bracket": "JUNIOR", "median": 39600000, "raiseFromPrevious": 26.9, "source": "KOSIS" },
          { "bracket": "MID",    "median": 51600000, "raiseFromPrevious": 30.3, "source": "KOSIS" },
          { "bracket": "SENIOR", "median": 62400000, "raiseFromPrevious": 20.9, "source": "KOSIS" },
          { "bracket": "LEAD",   "median": 74400000, "raiseFromPrevious": 19.2, "source": "KOSIS" }
        ]
      },
      "trend": [
        { "yearMonth": "2026-05", "median": 62000000, "sampleSize": 18, "disclosedSampleSize": 11, "missingRate": 38.9 },
        { "yearMonth": "2026-06", "median": null,     "sampleSize": 6,  "disclosedSampleSize": 2,  "missingRate": 66.7 },
        { "yearMonth": "2026-07", "median": 64000000, "sampleSize": 22, "disclosedSampleSize": 14, "missingRate": 36.4 }
      ],
      "trendComparison": {
        "recent":   { "from": "2026-07", "to": "2026-09", "median": 41000000, "ciLow": 38000000, "ciHigh": 45000000, "disclosedSampleSize": 47 },
        "previous": { "from": "2026-04", "to": "2026-06", "median": 50000000, "ciLow": 42000000, "ciHigh": 52000000, "disclosedSampleSize": 35 },
        "verdict": "FLAT",
        "sources": ["JOBKOREA"],
        "inProgress": ["2026-10"]
      }
    }
    ```
    *(공고 분포는 참고 표시 전용, 판정 기준선은 `baseline` — [기능 명세 §6](functional-specification.md) 규약)*
*   **`baseline` 은 임금 기준선의 성격과 적재 출처를 각각 싣는다.** `basis: "AGE_BAND_PROXY"` 는 KOSIS 연령 구간 임금값을 경력 구간으로 옮긴 기준이라는 뜻이며, `source` 는 실제 관측치의 `core.wage_stat_observation.source` 다. 화면은 두 값을 모두 `판정 기준선`에 표시한다. 기준선을 찾지 못하면 `baseline` 은 `null` 이다.
*   **`careerLadder` 는 같은 직군의 경력 구간별 임금 사다리다** ([기능 명세 §4.7](functional-specification.md)). `wage_stat_observation` 에서 각 구간의 기준선을 읽으며, `region = ALL` 을 우선 조회하고 없으면 `전국`으로 대체한다. `findLatestBaseline` 의 출처 우선순위는 `KOSIS` > `UNKNOWN` > `FALLBACK` 이며, 선택된 관측치의 `source` 를 그대로 싣는다. `FALLBACK` 은 KOSIS 실측 보간이 아닌 하드코딩 상수, `UNKNOWN` 은 출처를 확인할 수 없는 적재분이다. 화면은 구간별 카드로 보여주며 `KOSIS` 값은 실측 보간, 그 외 출처는 `추정`으로 구분한다. `raiseFromPrevious` 는 직전 구간과의 임금 차이를 직전 구간 중앙값으로 나눈 백분율(소수점 둘째 자리 반올림)이다. 첫 구간, 중앙값 결측, 직전 중앙값이 0이면 `null` 이다. 관측치가 없으면 해당 `median` 과 `raiseFromPrevious` 는 `null`, `source` 는 `UNKNOWN` 이다. `userBracket` 은 경력 매핑(`PeerGroupFallbackResolver.mapExperienceBracket6`) 결과다. 사다리는 프로필이 있으면 동료집단 표본 부족 응답에도 포함된다.
*   **`trend` 는 같은 동료집단의 등록월별 중앙 제시 연봉이다** ([기능 명세 §4.6](functional-specification.md)). 버킷 키는 `job_posting.posted_start_date` 의 등록월이라 수집 회차와 무관하다. **완화는 현재 시점 조건으로 한 번만 안착시키고 그 조건으로 월별 집계한다** — 월마다 다시 완화하면 비교 대상이 달라져 계열이 이어지지 않는다. 그 달 공개연봉 표본이 부족하면 `median` 이 `null` 이다. 전체 수집 공고 기준이며 프레임 필터가 없다. 금액이므로 지수로 환산하지 않는다.
*   **`trendComparison` 은 최근·이전 3개월 중앙 제시 연봉 비교다** (프레임·판정 규칙은 [기능 명세 §4.6](functional-specification.md)). 월은 `job_posting.sampled_month` 로 귀속한다. 끝난 달 L 은 동료집단 필터 전, 공개 연봉(`salary_min`·`salary_max`)이 있는 표본 행 중 현재 달 이하의 `max(sampled_month)` 라 동료집단에 그 달 표본이 없어도 정해진다. `recent` 는 L-2 ~ L, `previous` 는 L-5 ~ L-3 이다. 각 기간은 공개 연봉을 합쳐 한 번에 중앙값을 내고(월별 중앙값의 중앙값이 아니다) `ciLow`·`ciHigh` 는 그 중앙값의 95% 신뢰구간이다. 공개 표본이 최소 표본 수 미만인 기간은 `median`·`ciLow`·`ciHigh` 가 `null` 이고 `disclosedSampleSize` 는 실제 값이다. `verdict` 는 `UP`(최근 하한 > 이전 상한)·`DOWN`(최근 상한 < 이전 하한)·`FLAT`(구간 겹침)·`INSUFFICIENT_SAMPLE`(어느 한 기간이 표본 미달)이다. `sources` 는 기능 명세 §4.6 의 커버리지 규칙을 통과해 비교에 쓴 무작위 표본 원천(`job_posting.source` 값)이다. `inProgress` 는 L 다음 달부터 현재 달까지 집계 중인 달(`yyyy-MM`) 목록이며 건수는 싣지 않는다. 동료집단 표본 부족 응답이거나 프로필이 없거나 무작위 표본 행이 없으면 `null` 이다.
*   **두 `sampleSize` 는 다르다.** 최상위는 중복 제거 후 동료집단 공고 수, `postingReference.sampleSize` 는
    그중 **연봉이 공개된** 건수다. `minRequired` 는 두 수에 모두 걸린다 — 동료집단이 10건이어도 공개연봉이
    10건 미만이면 `INSUFFICIENT_SAMPLE` 이고 `postingReference` 에는 공개연봉 수와 `missingRate` 만 실린다.
    `OK` 일 때도 화면은 `missingRate` 로 분포가 공고 중 얼마에서 나왔는지 함께 보인다.
*   **온보딩 미완료**(커리어 프로필 없음)면 동료집단 조건 자체가 없어 `sampleSize: 0` 이고
    `baseline`·`peerGroup` 까지 `null` 이다. 위 표본 부족 예시는 프로필은 있고 표본만 모자란 경우다.

### 5.3 지수 트렌드 차트 데이터
*   **Endpoint:** `GET /api/v1/dashboard/trends`
*   구현: `com.carix.dashboard.service.SnapshotSeriesService`. **`incomeIndex`·`personalPriceIndex`·`nominalIncome`·`realIncome` 은 아직 항상 `null` 이다** — `index_snapshot` 에 그 컬럼이 없다. 0 으로 채우면 차트가 있지도 않은 하락을 그린다.
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
          "openingWindowIndex": 100.0,
          "incomeIndex": 100.0,
          "personalPriceIndex": 100.0,
          "nominalIncome": 5000000,
          "realIncome": 5000000
        },
        {
          "yearMonth": "2026-02",
          "purchasingPowerIndex": 99.1,
          "marketValueIndex": 102.3,
          "openingWindowIndex": 104.7,
          "incomeIndex": 100.0,
          "personalPriceIndex": 100.9,
          "nominalIncome": 5000000,
          "realIncome": 4955000
        },
        {
          "yearMonth": "2026-03",
          "purchasingPowerIndex": null,
          "marketValueIndex": 103.1,
          "openingWindowIndex": null,
          "incomeIndex": null,
          "personalPriceIndex": null,
          "nominalIncome": null,
          "realIncome": null
        }
      ],
      "purchasingPowerStatus": "UPDATE_DELAYED",
      "marketValueStatus": "OK",
      "openingWindowStatus": "OK"
    }
    ```
*   **결측월 표현.** `trends[]`의 지수·금액 필드는 모두 nullable 이다. 그 달을 계산하지 못했으면
    `null` 로 두고 **차트는 선을 잇지 않고 갭으로 그린다** ([기능 명세 §5](functional-specification.md) —
    `일부 월 누락 시 갭 표시(선 연결 금지)`). 계열 단위 사유는 응답 최상위의
    `purchasingPowerStatus`·`marketValueStatus`·`openingWindowStatus`(`IndexStatus`)가 담는다.
    *(소득 vs 물가 차트는 `incomeIndex`와 `personalPriceIndex` 두 지수 계열(기준월 100)로 그린다. 원화 금액과 지수를 같은 축에 겹치지 않는다. `purchasingPowerIndex = incomeIndex ÷ personalPriceIndex × 100`. 파생 금액(`realIncome` 등)은 원 단위로 계산 후 천 원 단위 반올림해 표시한다)*

### 5.4 월별 스냅샷 이력
*   **Endpoint:** `GET /api/v1/dashboard/history`
*   구현: `com.carix.dashboard.service.SnapshotSeriesService`. 한 달에 여러 행이 있으면 최신 `data_version` → 최신 `rule_version` 순으로 하나만 고른다 (`IndexSnapshotSelector`). 스냅샷이 없으면 빈 배열이다.
*   S-11 화면용. 월별 스냅샷 목록을 최신순으로 반환. 각 점의 평평한 필드(지수·`decisionSignal`·`label`·`dataVersion`·`ruleVersion`·`rationale`)는 **현재 유효 행**이고 대시보드(`VerdictHero`·`MarketValueTrend`)도 이를 쓴다. 그 달 끝에 보여 준 판정은 `recorded` 가 담는다.
*   **당시 판정(`recorded`).** 기준월 다음 달 1일 00:00 UTC 전에 만들어진 행들에 위와 같은 선택 규칙(최신 `data_version` → 최신 `rule_version`)을 적용해 고른 한 행이다 ([도메인 정의서 §5](domain-logic.md)). 같은 지수·신호·버전·`rationale` 필드에 `recordedAt`(그 행의 생성 시각, ISO-8601 UTC)을 더한다. 그런 행이 없으면 `null` 이다 — 가입 전이거나 그 달이 끝난 뒤 소급 계산된 달.
*   **`changed`.** `recorded` 가 있고 그 `decisionSignal` 이 현재 신호와 다르면 `true`. 지수만 다른 경우는 `false` 다.
*   **Response (200 OK):**
    ```json
    {
      "snapshots": [
        {
          "baseYearMonth": "202607",
          "purchasingPowerIndex": 94.2,
          "marketValueIndex": 117.5,
          "openingWindowIndex": 118.4,
          "decisionSignal": "SEEK_JOB",
          "label": "이직 탐색",
          "dataVersion": "20260701093000-000",
          "ruleVersion": "1.2.0",
          "rationale": { "rulesApplied": ["..."], "excluded": ["..."], "baseDate": "2026-07-01" },
          "recorded": {
            "purchasingPowerIndex": 95.1,
            "marketValueIndex": 116.0,
            "openingWindowIndex": 118.4,
            "decisionSignal": "STAY",
            "label": "현 상태 유지",
            "dataVersion": "20260731150012-000",
            "ruleVersion": "1.1.0",
            "rationale": { "rulesApplied": ["..."], "excluded": [], "baseDate": "2026-07-01" },
            "recordedAt": "2026-07-31T15:00:12Z"
          },
          "changed": true
        },
        {
          "baseYearMonth": "202606",
          "purchasingPowerIndex": null,
          "marketValueIndex": 115.0,
          "openingWindowIndex": null,
          "decisionSignal": "INSUFFICIENT_DATA",
          "label": "데이터 부족",
          "dataVersion": "20260601093000-000",
          "ruleVersion": "1.2.0",
          "rationale": { "rulesApplied": [], "excluded": ["..."], "baseDate": "2026-06-01" },
          "recorded": null,
          "changed": false
        }
      ]
    }
    ```
*   `purchasingPowerIndex`·`marketValueIndex`·`openingWindowIndex` 는 모두 nullable 이다. 그 달에 계산이 불가했으면 행은
    남기되 지수를 `null` 로 둔다 (`index_snapshot` DDL 주석 — 0 이나 100 으로 채우지 않는다).

### 5.5 스킬별 수요 상세
*   **Endpoint:** `GET /api/v1/dashboard/skills`
*   스킬 분석(`S-18`, `/dashboard/skills`)의 보유 스킬 수요 섹션용. 보유 스킬별 수요 지수와 산출 근거를 반환.
*   **Query Parameters (모두 선택, 가상 프로필 탐색용) — `MVP 범위 밖`:** `jobFamily`, `yearsOfExperience`, `region`, `companySize` — 의미와 동작은 §5.1과 동일. 파라미터만 MVP 에서 제외한다 ([개발 계획 §4](../plans/carix-dev-plan.md)).
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
*   **MVP 포함.** `UPSKILL` 결정 신호가 "스킬 보강이 먼저입니다"에서 끝나 무엇을 배우라는지 말하지 않던 빈칸을 메운다. 대응 스토리는 [기능 명세 US-D6·US-D9](functional-specification.md), 화면은 `S-10` 1층 판정 히어로의 처방/추천 블록과 `S-18` 스킬 분석(`/dashboard/skills`)이다.
*   **산출 경로.** 동료집단 `market_aggregate` 행을 `demand_rate` 내림차순으로 정렬하고 사용자 `user_skill` 보유분을 뺀 상위 3개다. 동료집단 완화는 `PeerGroupFallbackResolver` 를 그대로 쓴다. **`job_posting_skill` self-join 이 필요 없다** — 그건 "내 스킬과 함께 요구되는 스킬"(동시출현) 을 낼 때의 경로이고, 이 응답은 조합이 아니라 수요율 순위다.
*   **`coOccurrence` 가 `reason` 을 만든다.** 순위는 `demand_rate` 로 정하고, 근거는 **사용자 보유 스킬과 함께 요구되는 비율**로 쓴다 — 순위만으로는 왜 그걸 배우는지 말하지 못한다. 기준 스킬은 보유 스킬 중 동료집단 공고 수가 가장 많은 것이고, 비율은 `(기준 스킬과 후보를 함께 요구하는 공고) ÷ (기준 스킬을 요구하는 공고)` 다. `job_posting_skill` self-join 이며 중복제거는 `dedup_group_id`, 동료집단은 `PeerGroupFallbackResolver` 를 그대로 쓴다.
*   **경계.** `skill.is_custom` 은 양쪽(기준·후보)에서 제외한다. 동료집단 표본이 `PeerGroupFallbackResolver.MIN_SAMPLE_SIZE` 미만이면 `recommendations` 는 빈 배열이고 임의 스킬을 채우지 않는다. 기준 스킬의 공고 수가 같은 기준에 미달하거나 동시출현율이 임계값 미만이면 `coOccurrence` 는 `null` 이고 `reason` 은 수요율 문구로 되돌린다 — 임계값은 `b8` 에서 `SkillDemandCalculator` 상수와 함께 Config Server 로 외부화한다.
*   **품질 상한.** `raw_ingest` 가 원문을 보관하지 않아 스킬 사전을 개선해도 **이미 수집된 공고에는 소급되지 않는다.** 동시출현율은 수집 시점의 파싱 품질에 묶인다.
*   **Endpoint:** `GET /api/v1/skills/recommendations`
*   **Query Parameters (선택):**
    *   `limit` (Integer, optional): 추천 스킬 최대 조회 개수 (기본 10, 최대 30). 기본 10개를 제공하여 화면에서 상위 3개 단위 순환 탐색(다른 추천 보기)을 지원한다.
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
          "coOccurrence": { "withSkill": "Spring Boot", "rate": 0.62 },
          "reason": "보유하신 Spring Boot 공고의 62%가 함께 요구합니다."
        },
        {
          "standardName": "TypeScript",
          "category": "FRONTEND",
          "demandRate": 0.182,
          "growthTrend": "STABLE",
          "coOccurrence": null,
          "reason": "동료집단 내 공고의 18.2%에서 요구되는 핵심 스택입니다."
        }
      ]
    }
    ```

### 5.7 지표 소급 재계산 (Recalculate Snapshot History)
*   **Endpoint:** `POST /api/v1/dashboard/recalculate`
*   구현: `com.carix.dashboard.controller.DashboardController.recalculateSnapshot()`, `com.carix.snapshot.SnapshotRecalculationService.recalculateSnapshot(userId, fromYearMonth)`.
*   **용도:** 온보딩 이후 축적된 소득·소비·스킬 데이터와 최신 지표 알고리즘(개방창 지수 OWI 산출기 포함)을 바탕으로, 사용자의 최초 유효월부터 현재 기준월까지의 모든 과거 월 스냅샷을 멱등하게 소급 재계산한다.
*   **호출 시점:** 대시보드 메인(`S-10`) 헤더의 **[지표 재계산]** 버튼 클릭 시 호출.
*   **Request Body:** 없음 (인증 세션의 `userId` 기준으로 소득 유효월을 자동 탐색)
*   **Response (200 OK):**
    ```json
    {
      "recalculatedCount": 3,
      "fromYearMonth": "202604",
      "toYearMonth": "202606"
    }
    ```
    *(`recalculatedCount`: 신규 계산 및 갱신된 스냅샷 월 수, `fromYearMonth`: 재계산 시작월(최초 유효월), `toYearMonth`: 재계산 종료월(현재 기준월))*
*   **오류:** 세션 없음 `401 UNAUTHORIZED` / 소득 이력이 전혀 없어 시작월을 알 수 없음 `400 INVALID_INPUT`

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
    *(`scenarioName`: 선택 입력. 저장할 가상 시나리오의 별칭. 생략하거나 공백뿐이면 이름 없이(`null`) 저장되고, 앞뒤 공백은 잘린다. 응답의 `scenarioName` 도 그때 `null` 이다)*
    *(`currentGrossSalary`: 선택 입력. 생략 시 사용자의 최신 소득 이력 값을 사용하며, 가상 시뮬레이션을 위해 임의의 세전 연봉을 지정하여 테스트할 수 있습니다. 생략했는데 소득 이력도 없으면 `400 INVALID_INPUT` 입니다 — 호출자가 이 필드를 채우면 바로 해소되는 입력 문제라 상태값으로 표현하지 않습니다)*
    *(실업급여의 평균임금은 이 시뮬레이션에 실제 사용한 연봉을 따릅니다. 가상 연봉을 넣으면 실업급여도 그 값을 기준으로 계산됩니다 — 한 응답 안에서 잔류 보상과 실업급여가 서로 다른 연봉을 기준으로 나오지 않도록 합니다)*
    *(`stayExpectedRaiseRate`: 서버는 -100.0 ~ 100.0 을 벗어나면 `400` 입니다. **정본은
    [기능 명세 §3](functional-specification.md)의 -50 ~ +100 입니다** — 화면(Zod)이 이 좁은
    범위를 걸어 두 규격을 동시에 만족시킵니다. 서버 검증은 아직 넓은 범위 그대로라 API 를
    직접 부르면 범위 밖 값이 저장될 수 있습니다(`SimulationRequest` 의 `@DecimalMin("-100.0")`·`@DecimalMax("100.0")`).
    저장 정밀도는 소수점 두 자리라 그 아래는 반올림됩니다)*
    *(`expectedUnemploymentMonths`: 0~6. `isEligibleForUnemploymentBenefit` 기본값 true, false면 실업급여 0으로 계산하고 `실업급여 미반영` 안내 표시. 공식은 [도메인 정의서 §4](domain-logic.md)를 따릅니다)*
*   **Response (200 OK):**
    ```json
    {
      "id": "e812d8a4-0cfb-4a5f-9dfb-12d88c91350c",
      "scenarioName": "A사 오퍼 제안",
      "stayCompensation12m": 62100000,
      "moveCompensation12m": 60960000,
      "estimatedUnemploymentBenefit": 3960000,
      "unemploymentBenefitNote": "가정 기반 추정 (기준 시점: 2026-01)",
      "difference": -1140000,
      "type": "ESTIMATED_OPPORTUNITY_COST",
      "typeLabel": "추정 기회비용",
      "warningNote": "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
      "feasibility": {
        "status": "OK",
        "sampleSize": 42,
        "minRequired": null,
        "feasibilityScore": 31,
        "targetPercentile": 69,
        "difficultyGrade": "CHALLENGING",
        "feedbackMessage": "목표 연봉은 동료집단 내 상위 31% 수준의 도전적인 목표입니다. 시장 평균 범위 내에 위치하여 원만히 조율 가능한 수준입니다."
      }
    }
    ```
    *(예시 산출: 잔류 = 60,000,000 × 1.035 = 62,100,000. 이직 = 72,000,000 × 10/12 + 실업급여 3,960,000 − 3,000,000 = 60,960,000. 차이 = −1,140,000)*
    *(`unemploymentBenefitNote`: 실업급여 상·하한이 법정 공시값이 아닌 가정치이므로 기준 시점과 함께 알린다([도메인 정의서 §4.1](domain-logic.md)). 수급 자격이 없으면 `실업급여 미반영`)*
    *(`feasibility`: 이직 목표 연봉이 §5.2 동료집단 공고 분포에서 몇 번째 백분위(`targetPercentile`)에 해당하는지로 실현 난이도를 계산해 함께 반환한다. `difficultyGrade`는 `EASY`/`REALISTIC`/`CHALLENGING`/`VERY_HIGH_CHALLENGE` 중 하나)*
    *(`feasibility.status`: 표본이 모자라면 `INSUFFICIENT_SAMPLE` 이고 `feasibilityScore`·`targetPercentile`·`difficultyGrade`·`feedbackMessage` 가 전부 `null` 이다. 백분위를 임의 분포로 지어내지 않는다. `sampleSize` 는 백분위를 매긴 **공개연봉** 표본 수이고, `minRequired` 는 표본 부족일 때만 채운다)*
    *(산출 조건: 동료집단 공고 수와 그중 공개연봉 건수가 **둘 다** `minRequired` 이상이어야 한다. §5.2 연봉 분포와 같은 기준이며, 동료집단 완화도 공개연봉이 모자라면 한 단계 더 넓힌다)*
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
        "unemploymentBenefitNote": "가정 기반 추정 (기준 시점: 2026-01)",
        "difference": -1140000,
        "type": "ESTIMATED_OPPORTUNITY_COST",
        "typeLabel": "추정 기회비용",
        "warningNote": "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
        "feasibility": {
          "status": "OK",
          "sampleSize": 42,
          "minRequired": null,
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
    *(`result.feasibility` 는 저장한 백분위에서 등급·점수·문구를 다시 만든 값이다. 백분위 자체를 재계산하지 않는 이유는 동료집단 분포가 그 시점의 공고에 의존해, 나중에 다시 구하면 사용자가 실제로 본 판단이 나오지 않기 때문이다)*
    *(`result.unemploymentBenefitNote` 의 기준 시점은 **현재** 가정치를 가리킨다. 금액은 저장분 그대로이므로 숫자는 바뀌지 않는다)*

### 6.3 저장된 시나리오 목록 조회 (복원용)
*   **MVP 포함, 다만 용도는 복원이다.** `career_scenario` 는 `scenario_name` 을 받고 `UNIQUE` 가 없어 행이 쌓이는데 조회가 §6.2 `latest` 하나뿐이라 **이름을 붙여도 다시 볼 수 없었다** — 쓰기 전용 컬럼이었다. 이 엔드포인트가 그 읽기 경로다 ([기능 명세 US-S1](functional-specification.md)).
*   **비교 화면은 만들지 않는다.** `S-15` 안의 선택 컨트롤로 과거 입력 가정을 불러오는 데까지다.
*   같은 목록이 12개월 뒤 사후 검증(예측 vs 실제 소득)의 읽기 경로이기도 하다 — 그때 백엔드를 새로 만들지 않는다.
*   **응답에 복호화된 금액이 여러 건 실린다.** 본인 데이터라 노출 문제는 아니지만 로그·분석 이벤트에 남기지 않는다 (완료 기준 10번).
*   **Endpoint:** `GET /api/v1/scenarios`
*   **Response (200 OK):** 저장된 모든 시나리오 리스트를 `calculatedAt DESC, id DESC`(§6.2 와 같은 순서) 로 반환합니다. 첫 행이 §6.2 `latest` 와 같다. 저장분이 없으면 빈 배열 `[]` 이다 — 204 가 아니다.
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
*   **`MVP 범위 밖`** — 시나리오 이력 삭제 화면이 없다. 계정 삭제는 `DELETE /users` 연쇄로 처리한다 ([개발 계획 §4](../plans/carix-dev-plan.md)). `core-api` 에 구현이 없다 — 고정값을 돌려주던 스텁은 철거했다. 아래 규격은 재도입 시의 계약으로만 남긴다.
*   **Endpoint:** `DELETE /api/v1/scenarios/{id}`
*   **Response (200 OK):** 삭제 완료.

### 6.5 가상 소득/소비 변화에 따른 지수 변화 예측 (Index Predictor)
*   **폐기.** 엔드포인트를 재도입하지 않는다. 대신 같은 계산을 **역으로** 돌린 `negotiationTargets` 가 §5.1 로 들어갔다 — "이렇게 바꾸면 지수가 얼마"보다 "목표에 닿으려면 얼마가 필요한가"가 `NEGOTIATE`·`SEEK_JOB` 신호의 빈칸을 메운다.
*   **옛 배제 근거는 틀렸다.** MVP 규격 §2.3 이 막는 것은 **확정적 연봉 예측**인데 이 엔드포인트는 연봉을 예측하지 않았다 — 인상률을 사용자가 넣고 시스템은 재계산만 했다. `S-15` 시뮬레이터가 같은 구조로 이미 MVP 에 있다. 실제 사유는 규격이 먼저 적은 쪽, **대응 스토리·화면이 없다**였다. 아래 규격은 폐기된 계약의 기록으로만 남긴다.

### 7.1 지출 이력
*   `GET /api/v1/expenses` — 기준월별 지출 기록 목록. 각 항목: `baseYearMonth`, `items[{category, amount, status}]`
*   `PUT /api/v1/expenses/{baseYearMonth}` — 해당 기준월 지출 등록·수정 (6개 카테고리 부분 입력 허용). 미입력 카테고리는 기본 가중치로 보간 (혼합 규칙: [도메인 정의서 §1.1-4](domain-logic.md))
*   상태는 **카테고리별** `status`(`USER_DEFINED` / `DEFAULT_CPI`)로 반환하고, 월 요약 `weightStatus`는 `USER_DEFINED` / `PARTIALLY_ESTIMATED` / `PROPAGATED` / `DEFAULT_CPI`

### 7.2 스킬
*   `GET /api/v1/skills/search?q={query}` — 표준 스킬 사전 검색 (별칭 포함 자동완성)
*   `GET /api/v1/user-skills` — 보유 스킬 목록 (`id` (등록 매핑 ID), `skillId` (표준 스킬 ID), `standardName`, `isCustom`, `monthsOfExperience`, `lastUsedDate`)
*   `POST /api/v1/user-skills` — 스킬 추가. `monthsOfExperience`는 1~600 범위만 허용. 사전에 없으면 `isCustom: true`로 생성하며 이때 `category` 입력 필수 (`BACKEND`/`FRONTEND`/`DEVOPS`/`DATA`/`AI_ML`/`ETC`). 커스텀 스킬은 생성자 전용으로 검색·자동완성에 노출하지 않고, 마지막 사용자가 삭제하면 함께 삭제한다. 동일 정규화 스킬 중복 시 `409 Conflict`
*   `PUT /api/v1/user-skills/{id}` / `DELETE /api/v1/user-skills/{id}` — 수정·삭제

### 7.3 온보딩 상태 및 재시작
*   현재 단계는 `GET /api/v1/auth/session`(§2.3) 응답의 `onboardingStep` 으로 읽는다. 별도 조회 엔드포인트는 없다.
*   **`PATCH /api/v1/auth/onboarding`**(`auth-service` 소유) — 단계를 갱신한다. `users.onboarding_step` 은
    `auth-service` 가 소유한 컬럼이고 세션 조회 응답도 같은 서비스가 만들어, 갱신과 재조회가 한 서비스에 모인다. 단계
    진행만 갱신하며 `REVIEW` → `COMPLETED` 전환에서 별도로 첫 지수 계산을 트리거하지 않는다 —
    소득·소비·스킬·프로필 저장마다 걸린 스냅샷 재계산 트리거([도메인 정의서 §5](domain-logic.md))가
    이미 8곳에 있어 검토 단계에 닿을 때는 스냅샷이 먼저 나와 있다.
    *   **역방향 전환 방어:** 클라이언트 버그 등으로 이전 단계로 역행하는 요청은 `400 Bad Request`(`ONBOARDING_STEP_REGRESSION`)로 거부한다.
*   **`POST /api/v1/auth/onboarding/reset`**(`auth-service` 소유).
    *   **목적:** 온보딩을 완료(`COMPLETED`)했거나 진행 중인 사용자가 온보딩을 처음부터 다시 시작할 수 있도록 단계를 `PROFILE`로 초기화한다.
    *   **보안:** 세션 쿠키 필수 (`401 Unauthorized`).
    *   **동작:** `users.onboarding_step`을 `PROFILE`로 갱신하고 `updated_at`을 최신화한다. 기존 입력된 프로필, 소득, 소비, 스킬 데이터는 삭제되지 않고 유지된다.
    *   **Response (200 OK):**
        ```json
        {
          "currentStep": "PROFILE",
          "completed": false
        }
        ```

### 7.4 소득 이력 수정·삭제
*   `PUT /api/v1/income/{id}` / `DELETE /api/v1/income/{id}` — 유효 시작일 중복 시 `409 Conflict`. 변경 시 영향 유효월부터 스냅샷 재계산 트리거

### 7.5 세션·사용자
*   `POST /api/v1/auth/logout` — 현재 세션 폐기
*   `POST /api/v1/auth/logout-all` — 전체 세션 폐기
*   `DELETE /api/v1/users` — 사용자·개인 데이터 삭제. 쿼리 스트링으로 확인용 `?email=...` 필수 (일부 네트워크 환경에서 HTTP DELETE Body 유실 문제 방지), 이메일 불일치 시 `400`

### 7.6 적용된 소비 금액·가중치 조회

*   **MVP 포함.** `S-09` 검토 화면이 "기본값으로 대체된 항목 목록"을 약속하는데 **금액이 없어** 사용자가 어떤 수치로 지수가 계산됐는지 모른 채 넘어간다. 그 빈칸을 메운다 ([기능 명세 US-O6](functional-specification.md)).
*   **통계청 평균을 그대로 내려주는 API 가 아니다.** 물어야 할 것은 "무엇이 실제로 적용됐나" 이고, 그 답은 `ExpenseWeightResolver` 가 입력·전파·보간 중 무엇을 썼는지에 달려 있다. `amountBasis` 가 그것을 말한다.
    *   `USER_INPUT` — 사용자 입력이 금액 기준이다. 미입력 카테고리는 `기본가중치 × 입력합계 ÷ 입력분 가중치합` 으로 보간된 금액이 **실제로 적용**된다 (도메인 정의서 §1.1 Step 1·4).
    *   `PROPAGATED` — 과거 완전 입력 월의 금액이 전파됐다 (Step 2).
    *   `NONE` — **절대 금액 기준이 없다.** 입력도 전파할 과거 월도 없어 지수가 가중치만으로 계산된 경우다 (Step 3). 이때 `amount` 는 전부 `null` 이다.
*   **`NONE` 에서 금액을 0 으로 내리지 않는다.** 0 은 "한 달 주거비 0원" 이라는 거짓이고 도메인 정의서가 금지한 임의 수치다. 그 경우 화면이 보여줄 수 있는 "적용된 것" 은 `weight` 뿐이므로 응답이 가중치를 항상 함께 싣는다.
*   **`S-07` 입력 화면에서는 쓰지 않는다.** 입력 전에 평균 금액을 보여주면 사용자가 거기 끌려가 앵커링이 걸린다. `S-09` 와 `S-10` 구매력 카드의 입력값 한 줄은 입력을 마친 뒤라 그 위험이 없고 투명성만 남는다. 이것이 이 API 의 배치 조건이다. `S-10` 은 판정 기준월을 넘긴다.
*   **카테고리 코드는 `ExpenseCategoryEnum` 과 같다** — `HOUSING`·`FOOD`·`TRANSPORT`·`MEDICAL`·`EDUCATION`·`LEISURE`. 가중치 정본은 `config/default-expense-weights.yml` 이고 응답의 `weightVersion`·`weightBasePeriod` 가 그 출처를 밝힌다.
*   `GET /api/v1/expenses/applied?baseYearMonth=YYYYMM` — 기준월 생략 시 최신 소비 이력 월, 그마저 없으면 현재월.
    *   **Response (200 OK):**
        ```json
        {
          "baseYearMonth": "202609",
          "weightVersion": 1,
          "weightBasePeriod": "2025",
          "amountBasis": "USER_INPUT",
          "categories": [
            { "category": "HOUSING", "amount": 511000, "weight": 0.1795, "weightStatus": "USER_DEFINED" },
            { "category": "FOOD", "amount": 886000, "weight": 0.3112, "weightStatus": "DEFAULT_CPI" }
          ]
        }
        ```
    *   `amountBasis` 가 `NONE` 이면 모든 `amount` 가 `null` 이고 `weightStatus` 는 전부 `DEFAULT_CPI` 다.
*   **폐기.** `GET /reference/default-expenses` 는 서지 않았다. 정본 yml 에 없는 고정 금액(`averageAmount`)을 약속했고, 그 금액은 어느 분기에서도 실제로 적용되지 않는다.

### 7.7 소비자물가지수(CPI) 기준 데이터 조회
*   **`MVP 범위 밖`** — 화면이 필요로 하는 것은 **원자료 CPI 가 아니라 계산된 기여도**다. `S-10` 구매력 카드의 1위 품목 뱃지는 사용자 가중치가 곱해진 값이라 `GET /dashboard/summary` 의 `metadata.topPriceDriver` 로 나가고(`US-D1`), `S-14` 소득vs물가는 `incomeIndex`·`personalPriceIndex` 2계열만 쓴다. 원자료를 그대로 노출할 화면이 없다 ([API 규격 §5.1](api-specification.md)). `core-api` 에 구현이 없다 — 고정값을 돌려주던 스텁은 철거했다. 아래 규격은 재도입 시의 계약으로만 남긴다.
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
                { "category": "TRANSPORT", "cpiValue": 105.1 },
                { "category": "MEDICAL", "cpiValue": 102.3 },
                { "category": "EDUCATION", "cpiValue": 103.8 },
                { "category": "LEISURE", "cpiValue": 109.4 }
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
*   `POST /internal/pipeline/crawl` — 잡코리아·사람인·점핏·원티드 IT/개발 채용공고 실시간 스크래핑 트리거.
    *   `portal` 은 `ALL`(잡코리아·사람인, 기본)·`JOBKOREA`·`SARAMIN`·`JUMPIT`·`WANTED` 이다. `JUMPIT` 은 점핏 목록을 끝까지 순회해 사람인 공고를 보강·추가하며(규칙은 [데이터 아키텍처 §1.3](data-architecture.md), 원천 호출은 [외부 연동 §3.4](external-api-integration.md)) `ALL` 에는 들지 않는다. `JUMPIT` 작업 요약의 키는 `pages`(순회한 목록 페이지)·`fetched`(읽은 공고)·`created`(새로 만든 공고)·`enriched`(보강한 기존 공고)·`skipped`(필수 필드를 못 읽어 건너뜀)·`errors`(공고 단위 실패)·`droppedSkills`(사전에 없어 버린 기술 스택의 빈도)·`listFailed`(목록 응답을 못 받아 순회가 끊김)다. 새로 만들거나 보강한 공고가 하나도 없고 `errors` 만 있거나, 목록 페이지를 하나도 못 읽고(`pages` 가 0) `listFailed` 이면 작업은 `FAILED`(`errorMessage` 에 `errors` 수 또는 목록 실패)로 끝난다.
    *   `WANTED` 는 원티드 개발(518) 목록을 끝까지 순회해 새 공고를 만들고 이미 있는 공고는 마지막 관측월만 갱신한다(`ALL` 에는 들지 않는다. 원천 호출은 [외부 연동 §3.5](external-api-integration.md), 저장 규칙은 [데이터 아키텍처 §1.3](data-architecture.md)). 작업 요약의 키는 `pages`·`fetched`·`created`·`seen`(이미 있어 관측월만 갱신한 공고)·`skipped`(상위 태그가 518 이 아니거나 `__NEXT_DATA__` 가 없거나 목록 번호와 페이지 번호가 달라 건너뜀)·`errors`·`droppedSkills`·`listFailed`(목록 응답을 못 받아 순회가 끊김)·`portal` 이다. 새로 만들거나 본 공고가 하나도 없고(`created`+`seen` 이 0) `errors` 만 있거나, 목록 페이지를 하나도 못 읽고(`pages` 가 0) `listFailed` 이면 작업은 `FAILED` 로 끝난다.
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

### 8.8 데이터 상태 점검 조회
*   `GET /internal/pipeline/health` — 세 질문(구매력·시장가치·결정 신호)에 온전히 답하는지와 고칠 곳을 판정하는 읽기 전용 점검 (관리자 콘솔 `/admin/data` 맨 위 점검 줄 연동). 데이터를 바꾸지 않는다. 판정할 데이터가 없으면 정상이 아니라 `UNKNOWN`(판정 불가)이다. 데이터를 읽지 못하거나 판정이 실패해도 응답은 내며 점검 5개를 모두 `UNKNOWN` 으로 채운다.
    *   **Response (200 OK):**
        ```json
        {
          "evaluatedAt": "2026-10-05T18:00:00+09:00",
          "referenceMonth": "202610",
          "previousMonth": "202609",
          "engineRuleVersion": "1.5.0",
          "summary": {
            "users": 3,
            "fullyAnswered": 1,
            "gaps": [ { "answer": "MVI", "reason": "INSUFFICIENT_SAMPLE", "users": 1 } ]
          },
          "checks": [
            {
              "id": "CPI_LATEST",
              "title": "CPI 최신월",
              "verdict": "OK",
              "message": "최신 CPI 202609 (1개월 지연)",
              "impactUsers": 0,
              "action": null,
              "actionLink": null,
              "drillDown": "/admin/data?tab=cpi",
              "evidence": { "latestCpiMonth": "202609", "lagMonths": 1 }
            }
          ]
        }
        ```
    *   `referenceMonth`·`previousMonth` 는 서버 시계(UTC)로 센 이번 달·지난달이다(수집기 관측월과 같은 기준). `evaluatedAt` 은 `Asia/Seoul` 오프셋이다. `summary.users` 는 프로필 또는 스냅샷이 있는 사용자 수, `fullyAnswered` 는 세 질문에 온전히 답하는 사용자 수, `gaps[]` 는 답이 빈 사유(`answer` 는 `PPI`·`MVI`·`DECISION`·`ALL`)별 사용자 수다. 데이터를 읽지 못했으면 사용자 수를 0 으로 세지 않고 `summary` 가 `null` 이다.
    *   `checks[]` 는 아래 5개를 이 순서로 싣는다. `verdict` 는 `OK`·`WARN`·`ERROR`·`UNKNOWN` 이다.

        | `id` | 판정 |
        | --- | --- |
        | `AGGREGATE_STALE` | 월별 상한 적용 표본 수와 시장 집계 표본 수가 어긋난 달이 있는지. 잡코리아 또는 원티드가 어긋나면 `ERROR`(`impactUsers` = 시장가치 상태가 `OK` 가 아닌 사용자 수), 원티드 상한 적용 표본을 계산하지 못했고 어긋난 달이 없으면 `WARN`. 표본이 있는 원천만 비교하며 사람인·점핏은 표본이 아니라 대상이 아니다 |
        | `RULE_VERSION` | 사용자 스냅샷의 규칙 버전이 엔진(core-api, 8.9)보다 뒤처졌는지. 뒤처지면 `WARN` |
        | `SNAPSHOT_FRESHNESS` | 스냅샷 기준월이 `referenceMonth` 보다 이른 사용자가 있는지. 있으면 `WARN` |
        | `CPI_LATEST` | 최신 CPI 월이 `referenceMonth` 보다 늦은 개월 수. `cpi-max-lag-months`(2)와 같으면 `WARN`, 넘으면 `ERROR` |
        | `CRAWL_FRESHNESS` | 가장 오래된 정기 수집 원천의 마지막 수집이 `crawl-stale-days`(35)일을 넘으면 `WARN` |

    *   기준값은 collector 설정 `carix.collector.health.*`(`cpi-max-lag-months` 2, `crawl-stale-days` 35)이다.
    *   `checks[].drillDown` 은 근거 원본을 여는 관리자 콘솔 경로(문자열, nullable)이며 판정과 무관하게 채운다. `AGGREGATE_STALE` 는 `/admin/data?tab=monthly&from={시작}&to={기준월}` 이고 시작은 어긋난 달 중 가장 이른 달과 기준월−17개월 중 이른 쪽이되 기준월−35개월(36개월 범위)보다 이르면 그 달이다. `CPI_LATEST` 는 `/admin/data?tab=cpi`(기본 범위)이고 `CRAWL_FRESHNESS` 는 가장 오래된 원천의 마지막 수집 달·그 원천·최근 수집순으로 여는 `/admin/data?tab=postings&month={YYYYMM}&sort=recent&source={원천}` 이다. 그 밖의 점검은 `null` 이다.

### 8.9 엔진 규칙 버전 조회 (core-api)
*   `GET /api/v1/meta/versions` — 결정 신호 엔진의 규칙 버전. 인증이 없고, collector 가 게이트웨이를 거치지 않고 서비스 간 호출로 읽어 8.8 의 `RULE_VERSION` 점검에 쓴다.
    *   **Response (200 OK):**
        ```json
        { "ruleVersion": "1.5.0" }
        ```

### 8.10 수집 데이터 월별 현황 조회
*   `GET /internal/pipeline/explore/monthly?from={YYYYMM}&to={YYYYMM}&sampleSource={JOBKOREA|WANTED}` — 월별 표본·정기 수집 건수 조회 (관리자 콘솔 `/admin/data` 월별 현황 탭 연동). 8.10~8.13 은 모두 읽기 전용이며 데이터를 바꾸지 않는다. 공개 원천의 금액(공고 제시 연봉 범위, 임금통계 중위값)은 싣고, 사용자 소득은 싣지 않는다. 월 기준은 서버 시계(UTC)이며 수집기 관측월과 같다.
    *   `from`·`to` 는 모두 선택이다. 기본 범위는 이번 달까지 최근 18개월이고, 범위는 최대 36개월이다. 관리자 콘솔은 URL 의 `from`·`to` 를 그대로 넘긴다. 형식 오류, `from` 이 `to` 보다 늦은 경우, 36개월 초과는 `400 BAD_REQUEST` 다. `sampleSource` 는 선택이며 `sampledByFamily`(직무별 표본)를 셀 표본 원천이다 — `JOBKOREA`(기본)·`WANTED`, 그 밖의 값은 `400 BAD_REQUEST`.
    *   표본은 `sampled_month`, 정기 수집은 `first_seen_month` 로 월을 센다. `current` 는 그 달이 서버 시계(UTC) 기준 이번 달이면 `true` 다(수집 중인 달). `sampledByFamily` 는 `DEVELOPER`·`DATA`·`AI` 만 싣는다. `jobkoreaSampledCapped`·`wantedSampledCapped` 와 `sampledByFamily` 는 같은 회사 월 3건 상한을 적용한 표본 기준으로 시장가치 동료집단과 같은 모수다.
    *   **Response (200 OK):**
        ```json
        {
          "from": "202510",
          "to": "202610",
          "months": [
            {
              "month": "202609",
              "jobkoreaSampled": 250,
              "jobkoreaSampledCapped": 244,
              "jobkoreaCrawled": 1248,
              "saraminCrawled": 412,
              "wantedSampled": 250,
              "wantedSampledCapped": 238,
              "wantedCrawled": 530,
              "sampledByFamily": { "DEVELOPER": 203, "DATA": 10, "AI": 31 },
              "current": false
            }
          ]
        }
        ```

### 8.11 수집 공고 목록 조회
*   `GET /internal/pipeline/explore/postings[?month={YYYYMM}]` — 한 달의 수집 공고를 필터·페이지로 조회 (관리자 콘솔 `/admin/data` 공고 탭 연동). 제시 연봉 범위 `salaryMin`·`salaryMax`(연간, 원)는 공고에 공개된 금액만이며 없으면 `null` 이다. 비공개 검색 구간은 `salaryBandMin`·`salaryBandMax` 에 따로 싣고(없으면 `null`), `salaryDisclosure` 는 `PUBLIC`·`BAND`·`NONE`·`UNKNOWN`(판정 전) 이다.
    *   **Query Parameters:**

        | 파라미터 | 필수 | 설명 |
        | --- | --- | --- |
        | `month` | 선택 | `YYYYMM`. 생략하거나 비우면 가장 최근 수집 월(`core.job_posting` 의 `COALESCE(sampled_month, first_seen_month)` 최댓값, 다른 필터와 무관)이다. 형식 오류는 `400 BAD_REQUEST` |
        | `source` | 선택 | `JOBKOREA`·`SARAMIN`·`WANTED` |
        | `sampled` | 선택 | `true` 면 `sampled_month = month`, `false` 면 `sampled_month IS NULL` 이고 `first_seen_month = month`, 생략하면 둘의 합집합 |
        | `family` | 선택 | 직무군. `OTHER` 는 `DEVELOPER`·`DATA`·`AI` 가 아닌 모든 공고(직무군이 비어 있는 공고 포함)다 |
        | `company` | 선택 | 회사명 부분 일치 |
        | `skill` | 선택 | 표준 스킬명 정확 일치 |
        | `disclosure` | 선택 | 연봉 공개 구분 `PUBLIC`·`BAND`·`NONE`·`UNKNOWN`. 그 밖의 값은 `400 BAD_REQUEST` |
        | `sort` | 선택 | `recent`(기본, `collectedAt` 내림차순, 같으면 `source`·`externalId` 오름차순)·`source`(`source`, `externalId` 오름차순). 그 밖의 값은 `400 BAD_REQUEST` |
        | `page` | 선택 | 0부터. 기본 0 |
        | `size` | 선택 | 기본 50, 최대 100 (넘으면 100) |

    *   `month` 는 실제로 조회한 월이고 공고가 하나도 없으면 `null`(`total` 0, 빈 `items`)이다. `current` 는 그 월이 이번 달(UTC)인지다.
    *   `collectedVia` 는 공고를 확인한 수집 경로 목록(쉼표 구분: `JOBKOREA_LIST`·`JOBKOREA_BACKFILL`·`JOBKOREA_SEED`·`SARAMIN_SEARCH`·`JUMPIT`·`WANTED_LIST`·`WANTED_BACKFILL`)이고 `sourceJobTags` 는 원천의 직군 태그 원본이며 점핏은 `jobCategory` 이름, 원티드는 하위 직군 `{태그 ID}:{이름}`(쉼표 구분)이고 없으면 `null` 이다. `collectedAt` 은 공고를 수집한 시각(UTC)이다. `url` 은 원천 공고 링크로, 잡코리아 `https://www.jobkorea.co.kr/Recruit/GI_Read/{externalId}`, 사람인 `https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx={externalId}`, 원티드 `https://www.wanted.co.kr/wd/{externalId}` 다.
    *   **Response (200 OK):**
        ```json
        {
          "month": "202610",
          "current": true,
          "total": 250,
          "page": 0,
          "size": 50,
          "items": [
            {
              "source": "JOBKOREA",
              "externalId": "49500742",
              "url": "https://www.jobkorea.co.kr/Recruit/GI_Read/49500742",
              "title": "QA 채용",
              "companyName": "(주)평화이즈",
              "jobFamily": "DEVELOPER",
              "experienceMin": 3,
              "experienceMax": null,
              "employmentType": "FULL_TIME",
              "region": "서울 서초구",
              "postedStartDate": "2026-07-02",
              "postedEndDate": "2026-07-12",
              "firstSeenMonth": "202607",
              "sampledMonth": "202607",
              "collectedVia": "JOBKOREA_LIST",
              "sourceJobTags": null,
              "collectedAt": "2026-07-03T01:00:00Z",
              "salaryMin": 50000000,
              "salaryMax": null,
              "salaryBandMin": null,
              "salaryBandMax": null,
              "salaryDisclosure": "PUBLIC",
              "skills": ["QA"]
            }
          ]
        }
        ```

### 8.12 CPI 월 × 품목 조회
*   `GET /internal/pipeline/explore/cpi?from={YYYYMM}&to={YYYYMM}` — 월별 CPI 지수를 품목 코드별로 조회하고 결측 칸을 알린다 (관리자 콘솔 `/admin/data` CPI 탭 연동).
    *   `from`·`to` 는 모두 선택이다. 기본 범위는 이번 달까지 최근 24개월이고, 범위는 최대 120개월이다. 관리자 콘솔은 URL 의 `from`·`to` 를 그대로 넘긴다. 형식 오류, `from` 이 `to` 보다 늦은 경우, 초과는 `400 BAD_REQUEST` 다.
    *   값이 없는 칸은 `values` 에 키를 두지 않는다. `missing` 은 12개 품목이 다 있지 않은 달과 빠진 코드다. 행이 하나도 없는 달도 DB 의 첫 CPI 월부터 마지막 CPI 월 사이이고 요청 범위 안이면 `months` 에 빈 `values` 로 나오고 `missing` 에 12개 코드 전부로 오른다. 그 구간 밖의 달은 싣지 않는다(공표 지연은 `CPI_LATEST` 소관).
    *   **Response (200 OK):**
        ```json
        {
          "codes": ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"],
          "months": [ { "month": "202609", "values": { "A": 118.32, "B": 109.10 } } ],
          "missing": [ { "month": "202403", "codes": ["L"] } ]
        }
        ```

### 8.13 임금통계 칸별 출처 조회
*   `GET /internal/pipeline/explore/wage` — 최신 기준 연도 임금통계의 직무 × 경력 칸별 중위값·출처와 빈 칸 조회 (관리자 콘솔 `/admin/data` 임금통계 탭 연동). 파라미터가 없다. 칸마다 `values` 에 전국(`region = ALL`) 행의 정의 × 출처별 최신 회차(`statVintage`) 중위값(연간, 원)을 세전 총액(`ANNUAL_GROSS_MEDIAN`, 엔진 기본 기준선) → 정액급여(`ANNUAL_BASE_MEDIAN`) 순으로 담는다. `engineBaselines` 는 칸마다 core-api 가 전국 경로(`전국` → `ALL`)에서 실제로 고르는 기준선이다 — 출처(KOSIS → UNKNOWN → 그 밖) → 세전 총액 → 최신 연도 → 최신 회차 순이라 연도를 가리지 않으며, 최신 기준 연도 칸에 없거나 값이 다를 수 있다. 임금통계가 없으면 `basePeriod` 는 `null` 이고 15칸이 모두 `missing`, `engineBaselines` 는 빈 배열이다.
    *   `families` 는 `DEVELOPER`·`DATA`·`AI`, `brackets` 는 `ENTRY`·`JUNIOR`·`MID`·`SENIOR`·`LEAD` 다. 값이 있는 칸은 `cells` 에, 없는 칸은 `missing` 에 `직무/경력` 으로 담는다.
    *   **Response (200 OK):**
        ```json
        {
          "basePeriod": "2025",
          "families": ["DEVELOPER", "DATA", "AI"],
          "brackets": ["ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD"],
          "cells": [
            {
              "jobFamily": "DEVELOPER", "experienceBracket": "MID", "sources": ["KOSIS"],
              "values": [
                { "wageDefinition": "ANNUAL_GROSS_MEDIAN", "source": "KOSIS", "statVintage": "202508", "wageMedian": 61234567 },
                { "wageDefinition": "ANNUAL_BASE_MEDIAN", "source": "KOSIS", "statVintage": "202508", "wageMedian": 51130000 }
              ]
            }
          ],
          "missing": ["DATA/LEAD"],
          "engineBaselines": [
            { "jobFamily": "DEVELOPER", "experienceBracket": "MID", "region": "ALL", "basePeriod": "2025", "wageDefinition": "ANNUAL_GROSS_MEDIAN", "source": "KOSIS", "statVintage": "202508", "wageMedian": 61234567 }
          ]
        }
        ```

### 8.14 공고 급여 재판정 실행
*   `POST /internal/pipeline/salary-reclassify` — 잡코리아·사람인 공고의 상세페이지를 다시 열어 급여 공개 구분(`salary_disclosure`)을 판정한다. 비동기로 실행하며 `JobExecution`(`SALARY_RECLASSIFY`)으로 진행을 본다(§8.5). 판정 규칙은 [데이터 아키텍처 §1.3](data-architecture.md)이 소관이다.
    *   **Query Parameters:**

        | 파라미터 | 필수 | 설명 |
        | --- | --- | --- |
        | `dryRun` | 선택 | 기본 `true`. `true` 면 DB 를 쓰지 않고 측정만 한다. `false` 면 판정을 저장한다 |
        | `delayMs` | 선택 | 공고 사이 대기(ms). 기본 1000 |
        | `onlyUnjudged` | 선택 | `true` 면 판정 전(`UNKNOWN`) 공고만 다시 받는다 — 일시 실패로 남은 공고만 다시 판정할 때. 기본 `false`. 요약에 `onlyUnjudged` 가 함께 실린다 |

    *   `dryRun=false` 에서 상세페이지를 못 열었거나 급여를 못 읽은 공고는, 한 번도 판정되지 않은 공고에 한해 `UNKNOWN` 으로 두고 기존 연봉 값을 `salary_band_*` 로 옮긴다. 이미 판정된 공고는 건드리지 않는다.
    *   **Response (202 Accepted):** 작업 ID 를 돌려준다(§8.1 과 같은 형식).
    *   **작업 요약 (작업 상태 조회 §8.5 응답의 `details`):**
        ```json
        {
          "dryRun": true,
          "total": 8700,
          "unreachable": 40,
          "bySource": {
            "JOBKOREA": { "PUBLIC": 800, "BAND": 4200, "NONE": 1500, "UNKNOWN": 30 },
            "SARAMIN": { "PUBLIC": 300, "BAND": 0, "NONE": 1800, "UNKNOWN": 10 }
          },
          "publicPeers": {
            "DEVELOPER/JUNIOR": { "before": 12, "after": 9 }
          }
        }
        ```
        `unreachable` 은 판정이 `UNKNOWN` 인 공고 수다(상세페이지를 못 열었거나, 열었지만 description 이나 급여 항목이 없는 경우). `publicPeers` 는 직무 × 경력(`JUNIOR`·`MID`·`SENIOR`) × 서울·정규직 칸의 표본 수로, `before` 는 지금 `salary_min`·`salary_max` 가 둘 다 저장된 공고 수(공개 구분과 무관), `after` 는 판정이 `PUBLIC` 인 공고 수다. 이 수는 원문 공고를 그대로 센다 — 중복제거도 기업 규모 필터도 거치지 않으므로 core-api 가 실제로 쓰는 표본 크기의 근사치다. `errors` 는 처리 중 예외가 나 건너뛴 공고 수이며(해당 공고는 바꾸지 않는다).

### 8.15 과거 공고 표본 백필 실행
*   `POST /internal/pipeline/backfill` — 지정한 달의 공고를 공고 번호 무작위 표본으로 채운다. 비동기로 실행하며 `JobExecution` 으로 진행을 본다(§8.5). 표본 규칙은 [데이터 아키텍처 §1.3](data-architecture.md)이 소관이다.
    *   **Request Body:**

        | 필드 | 필수 | 설명 |
        | --- | --- | --- |
        | `fromMonth`·`toMonth` | 필수 | `YYYYMM`. 누락은 `400 BAD_REQUEST` |
        | `source` | 선택 | `JOBKOREA`(기본)·`WANTED`. 그 밖의 값은 `400 BAD_REQUEST` |
        | `perMonthTarget` | 선택 | 월 목표 건수. 원티드 기본 250 |
        | `maxRequestsPerMonth` | 선택 | 월 요청 상한. 원티드 기본 2000 |
        | `delayMs` | 선택 | 요청 간격(ms). 원티드 기본 1000 |
        | `minId`·`maxId` | 선택 | 공고 번호 탐색 범위(상한 포함). 원티드 기본 `minId` 280000, `maxId` 는 목록 API 의 최신 공고 번호(못 읽으면 420000) |

    *   **Response (202 Accepted):** 작업 ID 를 돌려준다(§8.1 과 같은 형식).
    *   **작업 요약:** 달(`YYYYMM`)마다 `requests`·`saved`·`alreadySampled`·`empty`·`otherMonth`·`nonTarget`·`errors` 를 싣는다. `errors` 는 저장에 실패해 건너뛴 후보 수이며 그 달은 계속하지만, 저장이 연달아 5번 실패하면 계통 오류로 보고 예외를 던져 작업을 `FAILED` 로 끝낸다(잡코리아·원티드 공통). 모든 달의 `saved` 합이 0 이고 `errors` 합이 0 보다 크면 작업은 `FAILED`(`errorMessage` 에 `errors` 수)로 끝난다. `source=WANTED` 는 `droppedSkills`(사전에 없어 버린 스킬 태그의 빈도)와 `apiMisses`(새로 만든 공고 중 상세 API 에서 스킬 태그를 못 읽은 수)를 더 싣는다.

### 8.16 MVI 수집 우선순위 조회
*   `GET /internal/pipeline/priorities` (별칭 `/collection-priorities`) — 동료집단 조합별 표본 수로 MVI 수집 우선순위를 매긴다 (관리자 콘솔 수집 우선순위 연동). 읽기 전용이다.
    *   **Query Parameters:** `month`(`YYYYMM`, 선택 — 비면 `ALL` 집계 행이 있는 당월(UTC) 이하 최신 월, 그것도 없으면 당월), `source`(`ALL` 기본·`JOBKOREA`·`SARAMIN` 등 집계 원천), `minSampleSize`(기본 10), `underSampledOnly`(기본 `false` — `true` 면 표본 부족 항목만).

### 8.17 KOSIS 소비자물가지수 수집
*   `POST /internal/pipeline/cpi` — 통계청 지출목적별 소비자물가지수(`DT_1J22001`) 12대 분류를 받아 `core.cpi_observation` 에 적재한다. 비동기로 실행하며 `JobExecution` 으로 진행을 본다(§8.5).
    *   **Request Body (선택):** `startYearMonth`·`endYearMonth`(`YYYYMM`). 비우면 최신 1개월이다.
    *   **Response (202 Accepted):** 작업 ID 를 돌려준다(§8.1 과 같은 형식).

### 8.18 보조 배치
비동기로 실행하며 `202 Accepted` 로 작업 ID 를 돌려준다(§8.1 과 같은 형식, 진행은 §8.5).
*   `POST /internal/pipeline/reclassify-jobkorea?delayMs={ms}` — 기존 잡코리아 공고를 상세페이지로 다시 열어 직무군과 스킬을 백필과 같은 규칙으로 맞춘다([데이터 아키텍처 §1.3](data-architecture.md)). `delayMs` 는 요청 간격이며 비우면 기본값, 음수는 `400 BAD_REQUEST`.
*   `POST /internal/pipeline/refine-skills` — 적재된 모든 공고의 제목과 기존 태그를 스킬 사전으로 다시 분석해 `job_posting_skill` 을 최신화한다. 요청 본문이 없다.
*   `POST /internal/pipeline/seed-load` — 파일시스템에 보존한 원문 JSON 으로 `core.raw_ingest`·`core.job_posting` 을 적재한다(초기 환경 구축용). **Request Body (선택):** `dataDir`(시드 디렉터리 경로).
