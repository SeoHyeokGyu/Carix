# 개발자 C 3~4주차 설계 — 온보딩·대시보드·시뮬레이터 화면 (`c3`)

[개발 계획 §3](carix-dev-plan.md) 의 `c3` 블록 설계다. 대상은 화면 여섯 — `S-05` 프로필,
`S-06` 소득, `S-07` 소비, `S-09` 검토, `S-10` 대시보드, `S-15` 시뮬레이터.

라우트·진입 가드·`AppShell`·세션 컨텍스트는 이미 배선돼 있고 여섯 화면만 `PlaceholderScreen`
이다. 화면이 쓰는 백엔드 API 도 온보딩 단계 갱신 하나를 빼고 전부 구현돼 있다 — 그 하나가
§3 이고, 없으면 온보딩 네 화면이 통째로 돌지 않는다.

---

## 1. 범위

| 화면 | 라우트 | 쓰는 API |
| :--- | :--- | :--- |
| `S-05` 프로필 | `routes/onboarding/profile.tsx` | `GET`·`PATCH /v1/profile` |
| `S-06` 소득 | `routes/onboarding/income.tsx` | `GET`·`POST /v1/income`, `PUT`·`DELETE /v1/income/{id}` |
| `S-07` 소비 | `routes/onboarding/expense.tsx` | `GET /v1/expenses`, `PUT /v1/expenses/{ym}` |
| `S-09` 검토 | `routes/onboarding/review.tsx` | 위 셋의 조회 + `GET /v1/user-skills` + 온보딩 단계 갱신 |
| `S-10` 대시보드 | `routes/dashboard/index.tsx` | `GET /v1/dashboard/summary` |
| `S-15` 시뮬레이터 | `routes/dashboard/simulator.tsx` | `GET /v1/scenarios/latest`, `POST /v1/scenarios/simulate` |

**`S-08` 스킬 화면은 이 범위 밖이다 — B 소관이다.** 계획서가 C 에 「온보딩 5화면 전체」라
적었으나 같은 문서가 `c3` 를 14d 에서 18d 로 늘리며 "늘린 4d 는 이관받은 `S-06`·`S-07` 두
화면분"이라고 적었다. 이관 전 14d 가 다섯 화면이었을 수 없으므로 C 의 원래 몫은 온보딩 양
끝(`S-05`·`S-09`)이고 `S-08` 은 B 행에 있는 대로 B 다. §2 에서 계획서를 고친다.

---

## 2. 같이 나가는 문서 정정

| 문서 | 고칠 내용 |
| :--- | :--- |
| [개발 계획 §1](carix-dev-plan.md) C 행 | 「`S-05`~`S-09` 온보딩 5화면 전체」 → `S-05`·`S-06`·`S-07`·`S-09` (`S-08` 은 B). 근거는 §1 |
| [개발 계획 §3](carix-dev-plan.md) 주차 표 C 3~4주차 칸 | 같은 문구. 그리고 `auth-service` 온보딩 단계 갱신을 항목으로 추가 — `c3` 에 없던 작업이다 (§3) |
| [프런트엔드 설계 §기술 구성](../specs/frontend-design.md) | TanStack Query·React Hook Form·MSW 삭제, Zod·shadcn/ui·Recharts 유지. 근거는 §4·§5·§6 |
| [API 규격 §7.3](../specs/api-specification.md) | 온보딩 단계 갱신 경로를 `auth-service` 로 이동 (§3) |
| [API 규격 §3.1](../specs/api-specification.md) | `region` 예시 `SEOUL_GANGNAM` → 시/도 한글 어휘 (§8-c) |

명세와 코드가 갈릴 때 문서를 같이 고치는 것은 [개발 계획 §5-F](carix-dev-plan.md) 가 세운
선례다. 고치지 않으면 다음 구현자가 무효가 된 명세를 그대로 보고 작업한다.

---

## 3. 온보딩 단계 갱신 API — 신설한다

`core-api` 의 온보딩 엔드포인트 둘은 스텁이다. `GET /v1/onboarding` 은
`OnboardingStatusResponse("EXPENSE", false)` 를 하드코딩해 돌려주고,
`PATCH /v1/onboarding` 은 받은 `currentStep` 을 그대로 되돌려줄 뿐 아무것도 쓰지 않는다.
서비스 호출 자체가 없다.

`users.onboarding_step` 은 `auth-service` 소유다(`authservice/user/User.java`, auth 스키마).
`core-api` 에는 그 테이블로 가는 쓰기 경로가 없다.

**이것이 온보딩 네 화면을 막는다.** 진입 가드가 전부 `session.onboardingStep` 에 걸려 있어
(`app/lib/routeGuards.ts`) 단계가 서버에 써지지 않으면 `S-05` 를 저장하고 `S-06` 으로 가도
가드가 다시 `S-05` 로 되돌린다.

**`auth-service` 에 `PATCH /v1/auth/onboarding` 을 신설한다.** 소유자가 쓴다. 세션 조회
응답도 `auth-service` 가 만들므로 갱신과 재조회가 한 서비스에 모인다. `core-api` 의 온보딩
엔드포인트 둘은 철거한다 — [개발 계획 §5-G](carix-dev-plan.md) 의 판별 기준(화면 인수 조건이
그 응답을 요구하는가)에 걸리고, 하드코딩 값을 돌려주고 있어 배제 판정만으로는 부족하다.

**엔드포인트만 더하면 되는 일이 아니다.** `User` 는 생성자로만 값을 받고 `onboardingStep` 에
변경 메서드가 없으며, `UserRepository` 에는 `findByEmail` 하나뿐이라 갱신 경로가 아예 없다.
이 단계를 쓰는 코드는 가입 시점의 최초 값 부여가 전부다. 그러므로 실제 작업은 엔티티 변경
메서드 · 서비스 · 컨트롤러 · 테스트까지다.

**이 항목은 `c3` 의 18d 에 들어 있지 않다.** 계획서가 `c3` 를 「화면 작업만」이라고 못박았고
이건 `auth-service` 백엔드 작업이다. 범위가 느는 것을 그대로 적어 둔다 — 값은 `S-08` 이 B 로
정리되며 회수한 구간에서 낸다(§1).

검토한 대안 둘:

- **`core-api` 가 `auth-service` 를 내부 호출** — API 규격의 경로를 유지하지만 Eureka 경유
  서비스 간 호출 경로를 새로 깔아야 하고, 프로필 저장과 단계 갱신이 서로 다른 트랜잭션에
  놓인다.
- **`onboarding_step` 을 core 스키마로 이관** — 세션 응답이 auth 소유라 조회할 때 다시 서비스
  경계를 넘는다. 더 나쁘다.

**「`REVIEW` → `COMPLETED` 전환 시 첫 지수 계산 트리거」는 따로 만들지 않는다.** 스냅샷
재계산 트리거 8개가 이미 소득·소비·스킬·프로필 저장마다 걸려 있어
([개발 계획 §4-2](carix-dev-plan.md)) 검토 화면에 닿은 시점에는 스냅샷이 이미 있다.

---

## 4. 데이터 계층 — React Router 7 loader/action

이 프런트엔드는 React Router 7 framework mode 다(`ssr: false` + `prerender: ["/"]`).
프레임워크가 `clientLoader`·`clientAction`·`useFetcher`·자동 revalidation 을 이미 갖고 있어
그 위에 별도 서버 상태 캐시를 얹지 않는다. [프런트엔드 설계](../specs/frontend-design.md) 가
규정한 TanStack Query 는 이 구성에서 역할이 겹쳐 둘 중 하나가 안 쓰이게 된다.

### 4.1 세션을 root loader 로 올린다

현재는 `SessionProvider` 가 `useEffect` 로 세션을 받아 `loading` 상태를 만들고 각 layout 이
렌더 시점에 가드를 돈다. root `clientLoader` 가 세션을 잡으면 렌더 전에 세션이 확정돼
`pending` 분기 자체가 사라진다. `SessionProvider.tsx` 주석이 가리키던 대안이 이쪽이다.

- `routeGuards.ts` 의 순수 함수는 그대로 재사용한다 — layout loader 안에서 호출해
  `redirect()` 로 던진다. 기존 가드 테스트는 손대지 않는다.
- `SessionProvider` 는 남기되 초기값을 root loader 데이터에서 받고, 단계 갱신 후에는
  `useRevalidator` 로 다시 읽는다.
- 세 layout 의 렌더 시점 가드를 loader 가드로 옮긴다. `SessionPending` 은 쓰임이 없어진다.

### 4.2 화면 데이터와 변경

- 조회는 라우트별 `clientLoader`. 여러 개가 필요하면 `Promise.all` 로 병렬 — `S-09` 가 넷이다.
- 변경은 `clientAction` + `<Form>`, 화면을 떠나지 않는 제출은 `useFetcher`. 성공하면 React
  Router 가 loader 를 다시 돌려 저장 후 수동 재조회 코드가 없어진다.

### 4.3 `app/lib/coreApi.ts`

fetch 래퍼 하나를 새로 만든다.

- `credentials: "include"` 고정 — 세션이 HTTP-only 쿠키다
- 오류를 `ApiError` 로 정규화. `authApi.ts` 의 `toApiError` 를 이 모듈로 승격해 공용화
- **401 을 한 곳에서 로그인 리다이렉트로 처리한다.** [개발 계획 §5-B](carix-dev-plan.md) 의
  세션 게이트 플래그를 켜는 순간 세션 없는 모든 `core-api` 호출이 401 이 되므로 단일 처리
  지점이 필요하다

---

## 5. 폼 검증 — Zod

스키마는 `app/lib/schemas/{profile,income,expense,scenario}.ts` 에 둔다. `clientAction` 에서
parse 하고, 실패하면 필드 오류를, 성공하면 API 를 호출한다. 서버가 돌려주는 400·409 도 같은
오류 채널로 합류시켜 화면이 그리는 오류 모양을 하나로 유지한다.

React Hook Form 은 넣지 않는다 — `clientAction` 이 제출을 받고 Zod 가 검증하면 남는 역할이
제어 컴포넌트 상태 관리뿐이고, 온보딩 화면은 필드가 적다.

**허용 범위의 정본은 [기능 명세 §3](../specs/functional-specification.md) 이다.** 서버 검증은
그보다 훨씬 느슨해서(`IncomeRequest` 는 `@PositiveOrZero`, `ProfileRequest` 는 `@Min(0)` 뿐)
이 범위를 지키는 곳이 지금은 화면밖에 없다. 아래는 화면이 구현하는 표이고, 오른쪽 열은 같은
필드에 대한 서버의 현재 상태다.

| 필드 | 화면이 거는 범위 | 서버 검증 |
| :--- | :--- | :--- |
| 세전 연봉 | 1,000만 ~ 30억 원 | `@NotNull @PositiveOrZero` 뿐 |
| 월 실수령액 | 50만 ~ 2억 원 | `@NotNull @PositiveOrZero` 뿐 |
| 소득 유효 시작일 | 과거 ~ 당월 (미래 거부) | 없음 |
| 소비 카테고리 월 금액 | 0 ~ 5,000만 원 | `@NotNull @PositiveOrZero` 뿐 |
| 총 경력 | 0 ~ 50년 | `@Min(0)` |
| 예상 인상률 | -50% ~ +100% (§8-f) | -100.0 ~ 100.0 |
| 이직 목표 연봉 | 세전 연봉과 같은 범위 | 없음 |
| 이직 일회성 비용 | 0 ~ 1억 원 | 없음 |
| 예상 공백 기간 | 0 ~ 6개월 | 0 ~ 6 |
| `jobFamily`·`region` | 100자 이하 | `@Size(max = 100)` |
| `employmentType`·`companySize` | 50자 이하 | `@Size(max = 50)` |

금액은 원 단위 정수로 보내고 콤마는 표시 포맷으로만 붙인다
([기능 명세 §3](../specs/functional-specification.md)).

---

## 6. UI 계층 — shadcn/ui

배선이 먼저 필요하다. 현재 `@/` 경로 별칭이 없다(`tsconfig.json` 에 `paths` 없음, `vite.config.ts`
에 `resolve.alias` 없음). shadcn 은 이것을 전제한다.

- 추가: `components.json`, tsconfig `paths` + vite `resolve.alias`,
  `class-variance-authority`·`clsx`·`tailwind-merge`·`lucide-react`, 쓰는 Radix 패키지
- **토큰은 두 벌 만들지 않는다.** shadcn 은 `--background`·`--foreground`·`--primary` 를
  기대하고 이 프로젝트는 `--carix-*` 를 쓴다. shadcn 변수를 carix 변수로 정의한다
  (`--background: var(--carix-bg)`). 다크는 기존 `.dark` 클래스 + `prefers-color-scheme` 유지
- 가져오는 컴포넌트: Button, Input, Label, Select, Card, Badge, Table, Alert, Form

**기존 `AuthForm`·`admin/*` 은 이번에 손대지 않는다.** 범위 밖이고 `c3` 는 임계 경로다. 두
양식의 공존을 인정하고 신규 화면만 shadcn 으로 만든다. 기존 화면 이관은 별건이다.

---

## 7. 화면별 설계

### 7.1 `S-05` 프로필

- `clientLoader` — `GET /v1/profile`
- `clientAction` — Zod parse → `PATCH /v1/profile` → `PATCH /v1/auth/onboarding {currentStep:"INCOME"}`
  → `redirect("/onboarding/income")`

선택지 값은 **집계 키 어휘에 맞춘다.** `market_aggregate.job_family` 는 정확일치로 조회되므로
(`MarketAggregateRepository`) 값이 어긋나면 동료집단이 조용히 비어 `표본 부족` 이 된다.

| 필드 | 값 | 출처 |
| :--- | :--- | :--- |
| `jobFamily` | `DEVELOPER`·`DATA`·`AI`·`OTHER` | `JobFamilyClassifier` |
| `employmentType` | `FULL_TIME`·`CONTRACT`·`FREELANCE`·`INTERN` | `EmploymentTypeClassifier` |
| `companySize` | `LARGE`·`MEDIUM`·`STARTUP` (선택) | `CompanySizeClassifier` |
| `region` | 시/도 17개 한글 어휘 (`서울`·`경기`…) | `RegionRollup.SIDO` |

와이어프레임이 나눈 백엔드/프론트엔드 개발자 구분은 집계에 존재하지 않는다 — 둘 다
`DEVELOPER` 로 분류되므로 선택지를 넷으로 줄인다.

**자동 저장은 넣지 않는다.** 와이어프레임의 「자동 저장됨」 뱃지는 저장 성공 피드백으로
읽는다. 이탈 시 유실 방지는 와이어프레임에 이미 있는 「저장 후 나가기」 버튼이 하고,
`PATCH /profile` 의 부분 저장 허용이 그것을 받쳐 준다. 키 입력마다 요청을 보내는 값이 없다.

### 7.2 `S-06` 소득

- `clientLoader` — `GET /v1/income`
- 이력 테이블의 행 단위 추가·수정·삭제를 `useFetcher` 로 제출
  (`POST /v1/income`, `PUT`·`DELETE /v1/income/{id}`)
- 유효 시작일 중복은 `409` 로 오므로 해당 필드 오류로 착지시킨다
- **`netSalary` 를 필수로 받는다.** 명세와 와이어프레임은 「월 실수령액(선택)」이지만
  `IncomeRequest.netSalary` 가 `@NotNull` 이라 빈 값을 보낼 수 없다(§8-a). 0 을 대신 보내지
  않는다 — 0 은 미입력과 구분되지 않아 PPI 가 세전 기반 추정으로 떨어지지 않는다

### 7.3 `S-07` 소비

- `clientLoader` — `GET /v1/expenses` 에서 당월 행
- `clientAction` — `PUT /v1/expenses/{YYYYMM}`, 본문은 `{items:[{category, amount}]}`
- 카테고리 정본은 `ExpenseCategoryEnum` — `HOUSING`·`FOOD`·`TRANSPORT`·`MEDICAL`·`EDUCATION`·`LEISURE` (§8-d)
- 미입력 카테고리는 `items` 에서 빼면 응답이 `status: DEFAULT_CPI` 로 돌아온다. 그 값을 그대로
  뱃지로 그린다 — 화면이 따로 판정하지 않는다
- 비중 시각화는 CSS `conic-gradient` 로 만든다. 6조각 정적 도넛에 차트 라이브러리를 임계
  경로에 올릴 값이 없고, Recharts 도입은 B 의 차트 작업 소관이다
- 합계가 월 실수령액을 넘으면 경고하되 저장은 허용한다

### 7.4 `S-09` 검토

- `clientLoader` — `Promise.all` 로 profile·income·expenses·user-skills 넷
- 요약 카드 넷 + 각 단계로 가는 수정 링크
- 「기본값으로 대체된 항목」은 지출 응답에서 `status === "DEFAULT_CPI"` 인 카테고리로 만든다.
  문구를 하드코딩하지 않는다
- **필수 필드 완비 검증이 여기 있다.** [API 규격 §3.2](../specs/api-specification.md) 가 온보딩
  중 부분 저장을 허용한 대가로 완비 확인을 검토 단계에 뒀다. 미완이면 해당 단계로 가는 링크를
  띄운다
- 제출 → `PATCH /v1/auth/onboarding {currentStep:"COMPLETED"}` → `redirect("/dashboard")`
- 완료 후 재진입 허용은 가드가 이미 처리한다(`routeGuards.ts`)

### 7.5 `S-10` 대시보드

- `clientLoader` — `GET /v1/dashboard/summary`
- 카드 셋은 값이 아니라 `indexStatus` 로 그린다

| `indexStatus` | 표시 |
| :--- | :--- |
| `OK` | 수치 + 전월 대비 포인트 차 |
| `INSUFFICIENT_SAMPLE` | `표본 부족` 뱃지 |
| `UPDATE_DELAYED` | `업데이트 지연` 뱃지 + 마지막 성공 데이터 |
| `INSUFFICIENT_HISTORY`·`NOT_AVAILABLE` | `계산 대기` |

값을 0 이나 100 으로 채우지 않는다 ([개발 계획 §4-1](carix-dev-plan.md)).

- 「첫 계산 전」 판정을 규칙으로 고정한다 — PPI 와 MVI 가 **둘 다** 값이 `null` 이고
  `NOT_AVAILABLE` 이면 「첫 지수를 계산하고 있습니다」를 띄운다
  ([기능 명세 §5](../specs/functional-specification.md))
- 근거 아코디언은 `decisionSignal.rulesApplied`·`rationale` 과
  `metadata.peerGroup.fallbackLevel`·`appliedConditions` 로 만든다
- 와이어프레임의 「스킬 수요율 펼치기」는 `GET /dashboard/skills`(B 소관)를 요구하는데
  [기능 명세 §8 추적 매트릭스](../specs/functional-specification.md)의 `S-10` 행은 `summary`
  하나다. 펼치기 대신 `S-11` 링크로 대체한다
- **이 기간에는 정상 경로를 확인할 수 없다.** A 의 PPI 구현이 `a2` 로 들어오기 전까지
  `com.carix.index.stub` 이 `NOT_AVAILABLE` 을 돌려준다. 열화 상태를 먼저 만들고 정상 카드는
  `a2` 병합 후 확인한다

### 7.6 `S-15` 시뮬레이터

- `clientLoader` — `GET /v1/scenarios/latest`. 저장분이 없으면 `204` 이므로 `null` 로 받는다
- 입력 여섯 — `scenarioName`(선택)·`stayExpectedRaiseRate`·`moveTargetGrossSalary`·
  `moveOneTimeCost`·`expectedUnemploymentMonths`·`isEligibleForUnemploymentBenefit`
- `currentGrossSalary` 는 받지 않는다. 생략하면 서버가 최신 소득 이력을 쓰고, 가상 연봉 지정은
  화면 인수 조건에 없다
- 제출은 `useFetcher` 로 한다 — loader 데이터가 남아 「계산 실패 시 이전 결과 유지」가 별도
  상태 관리 없이 성립한다
- 결과는 `difference`·`typeLabel`·`warningNote`·`feasibility`.
  `feasibility.status === "INSUFFICIENT_SAMPLE"` 이면 백분위·등급 자리에 `표본 부족` 을 쓴다 —
  숫자를 지어내지 않는다
- `isEligibleForUnemploymentBenefit` 이 거짓이면 「실업급여 미반영」을 안내하고, 참이면
  `unemploymentBenefitNote` 의 기준 시점을 함께 적는다 — 상·하한이 법정 공시값이 아니라
  가정치다 ([도메인 정의서 §4.1](../specs/domain-logic.md))
- 미입력 상태에서 기본값을 채우지 않는다 ([기능 명세 §5](../specs/functional-specification.md))

---

## 8. 구현하며 드러난 계약 불일치

전부 이 설계 밖의 소관이라 여기서는 기록만 하고 해당 담당에게 넘긴다.

| # | 내용 | 소관 |
| :--- | :--- | :--- |
| a | `IncomeRequest.netSalary` 가 `@NotNull` 인데 명세·와이어프레임은 「월 실수령액(선택)」이고, 없을 때 「세전소득 기반 추정」 뱃지를 요구한다. 완화하지 않으면 `S-06` 이 빈 값을 못 보낸다 | A (소득 API) |
| b | `jobFamily` 실값은 `DEVELOPER`·`DATA`·`AI`·`OTHER` 넷뿐인데 와이어프레임은 백엔드·프론트엔드를 나눠 보여준다. 화면을 값에 맞췄다 | 명세 소유자 |
| c | `career_profile.region` 을 `PeerGroupFallbackResolver` 는 `서울 강남구` 형태 한글로 기대하고 [API 규격 §3.1](../specs/api-specification.md) 예시는 `SEOUL_GANGNAM` 이다. 조회 쿼리가 `OR m.region = 'ALL'` 로 떨어져 지금은 어느 값이든 통과하므로 증상이 없다. 값 규약을 §7.1 대로 확정한다 | 명세 소유자 |
| d | `ExpenseItem` 의 Javadoc 이 카테고리를 `TRANSPORTATION`·`HEALTH`·`OTHER` 로 적었으나 정본 `ExpenseCategoryEnum` 은 `TRANSPORT`·`MEDICAL`·`LEISURE` 다 | A (소비 API) |
| e | `core-api` 의 `extractBroadRegion` 은 `서울특별시` 를 만들고 collector 의 `RegionRollup` 은 `서울` 을 만든다. 완화 Level 3 의 광역 조회가 `region = 'ALL'` 행으로 떨어져 가려져 있다 | B (동료집단) |
| f | 예상 인상률 허용 범위가 [기능 명세 §3](../specs/functional-specification.md) 은 `-50 ~ +100`, [API 규격 §6.1](../specs/api-specification.md) 은 `-100.0 ~ 100.0` 이다. 화면은 좁은 쪽(명세 §3)을 걸어 두 규격 모두 통과시킨다 | 명세 소유자 |
| g | [기능 명세 §3](../specs/functional-specification.md) 의 허용 범위 대부분이 서버에 없다(§5 표 오른쪽 열). 지금은 화면만 막고 있어 API 를 직접 부르면 범위 밖 값이 저장된다 | A·C (해당 도메인 API) |

---

## 9. 민감 금액 취급

[기능 명세 §7](../specs/functional-specification.md) 완료 기준 10번은 전 레인 공통이고 이
화면들이 그 경로 대부분을 만든다.

- **금액을 브라우저 저장소에 쓰지 않는다.** 온보딩 초안을 `localStorage`·`sessionStorage` 에
  담으면 연봉과 목표 연봉이 디스크에 평문으로 남는다. 단계 이탈 대비는 서버 부분
  저장(`PATCH /profile`)으로 충분하다
- 금액을 쿼리스트링에 싣지 않는다. 전부 요청 본문으로 보낸다
- 폼 값과 응답 본문을 `console` 에 찍지 않는다. `coreApi` 의 오류 로깅도 상태 코드와 경로까지다

---

## 10. 상태·오류 공통 규칙

- **loader 는 열화를 예외로 던지지 않는다.** `{ok:true, data}` / `{ok:false, error}` 로
  돌려준다. 던지면 ErrorBoundary 가 마지막 성공 데이터를 잃어 [기능 명세 §5](../specs/functional-specification.md)
  가 요구한 「마지막 성공 데이터 유지 + 재시도」를 만들 수 없다
- ErrorBoundary 는 예상하지 못한 오류 전용이다
- 오류 문구에 내부 코드·스택을 노출하지 않는다. `coreApi` 가 정규화한 문구만 쓴다
- 추정·대체값은 전부 뱃지로 실측과 구분한다

---

## 11. 테스트

- Zod 스키마 단위 테스트 — §5 의 규칙 표를 케이스로 그대로 옮긴다
- `clientLoader`·`clientAction` 단위 테스트 — `fetch` 를 mock 한다(`authApi.test.ts` 와 같은
  방식). MSW 는 도입하지 않는다
- 라우트는 React Router 7 의 `createRoutesStub` 으로 loader·action 을 포함해 렌더 검증한다
- **`onboarding.test.tsx`·`dashboard.test.tsx` 는 다시 쓴다.** 현재는 `PlaceholderScreen` 의
  화면 ID 와 제목 문자열을 단언하고 있어 실화면이 들어오면 전부 깨진다
- E2E(온보딩 → 대시보드 → 시뮬레이터) 골격을 세워 두고 본 점검은 7주차에 한다

---

## 12. 작업 순서

1. **배선** — root `clientLoader` + 가드 이관, `coreApi.ts`, shadcn 배선(별칭·토큰 매핑).
   이것 없이는 여섯 화면이 모두 막힌다
2. **`auth-service` 온보딩 단계 갱신** (§3) — 엔티티 변경 메서드부터다. 이것 없이는 온보딩 네
   화면이 돌지 않는다. **`c3` 에 없던 백엔드 작업이므로 계획서 §3 주차 표 C 칸에 함께 적는다**
3. `S-05` → `S-06` → `S-07` → `S-09` — 한 줄기로 만든다
4. `S-10`
5. `S-15`

`S-08` 이 B 로 정리되며 회수한 구간이 1·2번 배선분을 덮는다.
