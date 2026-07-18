# Carix 도메인 및 계산 공식 세부 정의서 (Domain Logic & Math Formulations)

이 문서는 Carix 핵심 지표산출 엔진 및 시나리오 시뮬레이터 구현에 필요한 알고리즘, 수학 공식, 예외 처리 정책 및 가중치 전파 논리를 상세히 정의합니다.

---

## 1. 시계열 소비 가중치 상속 및 전파 논리 (Expense Weight Propagation)

사용자는 매달 소비 패턴을 기록하지 않을 가능성이 높습니다. 시계열 실질 구매력 지수(PPI)를 누적 계산할 때 소비 가중치 데이터 공백을 해결하기 위한 상속 알고리즘을 다음과 같이 정의합니다.

### 1.1 가중치 산출 및 전파 알고리즘
기준월 $t$에 대하여 사용자의 소비 가중치 벡터 $W(t)$를 산출하는 순서입니다.

1.  **사용자 직접 입력 검사:**
    해당 월 $t$에 기록된 `expense_history` 및 `expense_item` 데이터가 존재한다면, 입력된 금액 비율을 기준으로 가중치 $W_i(t)$를 계산합니다.
    $$W_i(t) = \frac{\text{Amount}_i(t)}{\sum_{j=1}^{6} \text{Amount}_j(t)}$$
    *(상태 라벨: `USER_DEFINED`)*

2.  **직전 과거 이력 상속 (Propagation):**
    월 $t$에 소비 데이터가 없다면, 과거로 역추적하여 가장 가까운 최근 월 $t-k \, (k \ge 1)$의 성공적인 소비 기록 $W(t-k)$를 그대로 복사하여 $W(t)$로 사용합니다.
    *(상태 라벨: `PROPAGATED`)*

3.  **기본 통계 가중치 대체 (Default Fallback):**
    과거 기록이 전무한 가입 초기 온보딩 단계 등에서는 통계청(KOSIS) 가구별 월평균 소비지출 비중 데이터를 사용자의 가족 구성원수(프로필 기반)에 맞추어 기본값으로 주입합니다.
    *(상태 라벨: `DEFAULT_CPI`)*

### 1.2 UI 표기 정책
대시보드 상에 계산된 구매력 지수 옆에 위 상태 라벨에 따른 뱃지를 다음과 같이 매칭하여 데이터 신뢰도를 사용자에게 투명하게 알립니다.
*   `USER_DEFINED` ➡️ **"내 지출 기준"** (신뢰도 높음)
*   `PROPAGATED` ➡️ **"이전 지출 기준"** (중간)
*   `DEFAULT_CPI` ➡️ **"통계청 평균 기준"** (참고용)

---

## 2. 동료집단(Peer Group) 완화 알고리즘 계층 구조 (Fallback Hierarchy)

공개 채용 공고 데이터를 특정 세부 필터(직무 + 지역 + 경력 + 고용형태 + 회사규모)로 묶었을 때 표본 수가 최소 기준($N \ge 10$) 미만으로 떨어지는 **데이터 희소성 문제**를 해결하기 위한 단계적 쿼리 완화 전략입니다.

```text
[검색 시도]
   │
   ├── Level 1: 완전 일치 (직무 + 지역구 + 경력년수 + 고용형태 + 회사규모) ──▶ (N >= 10?) ──▶ [성공: 반환]
   │                                                                           │ (아니오)
   ├── Level 2: 회사 규모 조건 해제 (직무 + 지역구 + 경력년수 + 고용형태) ───▶ (N >= 10?) ──▶ [성공: 반환]
   │                                                                           │ (아니오)
   ├── Level 3: 지역 범위 확장 (지역구(예: 강남구) -> 시/도(예: 서울시)) ─────▶ (N >= 10?) ──▶ [성공: 반환]
   │                                                                           │ (아니오)
   ├── Level 4: 경력 구간 확장 (정확한 년수 -> 인접 경력구간 [-1, +1년]) ─────▶ (N >= 10?) ──▶ [성공: 반환]
   │                                                                           │ (아니오)
   └── Level 5: 고용 형태 조건 해제 (정규직/계약직 전체 통합) ───────────────▶ (N >= 10?) ──▶ [성공: 반환]
                                                                               │ (아니오)
                                                                       [최종: 데이터 부족 반환]
```

### 2.1 완화 단계별 쿼리 정책

1.  **Level 1 (Exact Match):**
    ```sql
    WHERE job_family = :job AND region = :region_detail AND experience = :exp AND company_size = :size AND employment_type = :type
    ```
2.  **Level 2 (Drop Company Size):**
    ```sql
    WHERE job_family = :job AND region = :region_detail AND experience = :exp AND employment_type = :type
    ```
3.  **Level 3 (Expand Region):**
    `region_detail` (예: 서울 강남구) ➡️ `region_broad` (예: 서울특별시 전체)로 넓혀 쿼리합니다.
4.  **Level 4 (Expand Experience Range):**
    정확한 년차 `experience = 3` 대신 `experience BETWEEN 2 AND 4`로 구간 조회를 수행합니다.
5.  **Level 5 (All Employment Types):**
    `employment_type` 조건을 쿼리에서 제거하여 전체 유형을 병합합니다.

### 2.2 메타데이터 반환 규격
지표 API 응답 시 실제 적용된 Fallback 단계를 함께 반환하여, 프론트엔드가 대시보드 하단에 **"비교군: 서울 전체 / 경력 2~4년 기준 (회사규모 무관)"**과 같이 표기할 수 있도록 지원합니다.

---

## 3. 사용자 스킬 가중치($W_{skill}$) 산출 수학 공식

사용자가 다수의 스킬을 입력했을 때, 각 스킬의 실무 사용 경력(Months)과 최근성(Recency)을 계산하여 상대적 중요도 가중치를 분배하는 수학적 모형입니다.

### 3.1 변수 정의
*   $E_i$: $i$번째 스킬의 실무 경력 (개월 수, $E_i \ge 0$)
*   $R_i$: $i$번째 스킬의 마지막 사용 이후 경과된 기간 (개월 수, $R_i \ge 0$)
    *   *이번 달까지 사용한 경우 $R_i = 0$*

### 3.2 스킬 숙련도 점수 산출 공식
1.  **경력 기간 요인 ($F_{dur}$):** 경력이 길어질수록 숙련도가 올라가나, 한계효용 체감을 반영하기 위해 자연로그 함수를 적용합니다.
    $$F_{dur}(E_i) = \ln(E_i + 1)$$
2.  **최근성 감쇄 요인 ($F_{rec}$):** 사용하지 않은 지 오래될수록 기억 및 시장 숙련도가 떨어지는 망각 곡선을 지수적 반비례 공식으로 모델링합니다.
    $$F_{rec}(R_i) = \frac{1}{1 + \alpha \cdot R_i} \quad (\text{기본 감쇄율 } \alpha = 0.1)$$
3.  **스킬별 원천 점수 ($S_i$):**
    $$S_i = F_{dur}(E_i) \times F_{rec}(R_i) = \ln(E_i + 1) \times \frac{1}{1 + 0.1 \cdot R_i}$$
4.  **정규화 가중치 ($W_i$):** 사용자가 보유한 전체 $N$개 스킬에 대하여 총합이 1이 되도록 비중을 나눕니다.
    $$W_i = \frac{S_i}{\sum_{j=1}^{N} S_j}$$

### 3.3 산출 예시
*   **스킬 A:** 24개월 사용, 0개월 방치 (현재 사용) ➡️ $S_A = \ln(25) \times 1 \approx 3.22$
*   **스킬 B:** 12개월 사용, 10개월 방치 ➡️ $S_B = \ln(13) \times \frac{1}{2} \approx 2.56 \times 0.5 = 1.28$
*   **총합 ($\sum S$):** $3.22 + 1.28 = 4.50$
*   **최종 가중치:** 스킬 A ($71.5\%$), 스킬 B ($28.5\%$)

---

## 4. 시나리오 시뮬레이터 고도화 및 세후 계산 공식

이직 시 발생하는 실업 공백 및 실업급여 수급 조건을 시나리오 시뮬레이션에 반영하여 현실성을 높입니다.

### 4.1 한국 실업급여(구직급여) 모킹 공식
고용보험 가입 기간(180일 이상) 요건을 충족했다고 가정한 시뮬레이션 공식입니다.

*   **1일 구직급여 지급액:**
    $$\text{DailyBenefit} = \min\left(\text{UpperLimit}, \max\left(\text{LowerLimit}, 0.6 \times \text{AvgDailyWage}\right)\right)$$
    *   상한액 (`UpperLimit`): 일 66,000 원
    *   하한액 (`LowerLimit`): 최저임금의 80% (2026년 기준 일 약 60,120 원 적용)
*   **지급 기간 (구직급여 소정급여일수):**
    MVP에서는 연령 및 가입 기간 조건 테이블을 단순화하여 **평균 150일(5개월)**을 수급 한도로 지정하고, 사용자가 입력한 구직 공백 기간 $U$(개월)만큼 비례 지급합니다.
    $$\text{TotalBenefit}(U) = \min(U, 5) \times (\text{DailyBenefit} \times 30)$$

### 4.2 12개월 시나리오 세후 누적 보상 연산식
사용자가 입력한 예상 공백 기간 $U$(개월, $0 \le U \le 6$)을 기준으로 비교 연산합니다.

#### ① 잔류 시나리오 (Stay Scenario)
$$Stay_{12m} = \text{CurrentSalary} \times \left(1 + \frac{\text{expectedRaiseRate}}{100}\right)$$

#### ② 이직 시나리오 (Move Scenario)
$$Move_{12m} = \left(\text{TargetSalary} \times \frac{12 - U}{12}\right) + \text{TotalBenefit}(U) - \text{MoveOneTimeCost}$$

#### ③ 차이값 (Difference)
$$\text{Difference} = Move_{12m} - Stay_{12m}$$
*   차가 양수일 경우 ➡️ **추정 기회이익 (Estimated Gain)**
*   차가 음수일 경우 ➡️ **추정 기회비용 (Estimated Opportunity Cost)**
