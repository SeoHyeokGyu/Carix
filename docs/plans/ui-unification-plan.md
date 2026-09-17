# UI 일관성 통일 계획 (B 시각·UX 규범 채택)

프런트엔드가 두 벌의 시각 언어와 두 벌의 DTO 를 동시에 갖고 있다. 이 문서는 그것을
하나로 모으는 실행 계획이다. 규범 자체는 코드가 들어온 뒤 `docs/specs/ui-ux-design.md`
에 반영한다 — 문서는 현재 상태만 서술하므로 구현보다 먼저 쓰지 않는다.

---

## 1. 현상

불일치가 세 층에 있다. 아래는 실측치다.

### 1-A. 동작까지 갈라진 것 — 결정 신호

같은 결정 신호가 두 화면에서 서로 다른 것이 됐다.

| 층 | `S-10` 대시보드 | `S-11` 이력 |
| :--- | :--- | :--- |
| DTO | `lib/api/dashboard.ts` — `DecisionSignal` 객체 (`code`·`label`·`rationale`·`rulesApplied`) | `lib/dashboardMarketApi.ts` — `decisionSignal: string` 코드만 |
| 라벨 출처 | 서버가 준 `label` | `routes/dashboard/history.tsx` `getSignalBadge` 프런트 하드코딩 |
| 시각 | `<Badge>` 단색 중립 — 신호 종류 무관 | 5색 의미 매핑 |

**스타일 문제가 아니다.** 서버가 결정신호 문구를 바꾸면 `S-10` 만 따라오고 `S-11` 은 안
따라온다. 나머지 발견은 전부 이것보다 아래다.

원인은 대시보드 도메인에 프런트 API 클라이언트가 둘(`lib/api/dashboard.ts`,
`lib/dashboardMarketApi.ts`)이라는 데 있다. 이 계획은 증상(DTO 분기)만 고친다 — 클라이언트
병합은 범위 밖이다(§6).

### 1-B. 토큰 공백 — 신호 5종 vs 색 4종

`app/styles/tokens.css` 의 의미 색상은 `positive`·`warning`·`danger`·`info` 넷이다. 결정
신호는 다섯이다. 모자란 하나를 `NEGOTIATE` 가 토큰 밖에서 끌어왔다.

```
"bg-purple-500/15 text-purple-400 border border-purple-500/30"
```

다른 토큰은 전부 라이트/다크 쌍을 갖는다(`--carix-positive` = `#047857` / `#10b981`).
이 purple 은 고정값이고 `text-purple-400` 은 다크 기준 명도다 — 라이트 모드 대비 미달
가능. **고칠 자리는 화면이 아니라 토큰 세트다.**

### 1-C. 프리미티브 미채택

디자인 시스템이 있는데 절반이 쓰이지 않는다.

| 항목 | 실측 |
| :--- | :--- |
| 죽은 프리미티브 | `ui/alert.tsx`·`ui/select.tsx`·`ui/table.tsx` — import 0 |
| raw `<table>` | 4곳. `ui/table.tsx` 는 아무도 안 씀 |
| raw `<button>` | 30개 / 서로 다른 className 문자열 10종 |
| 포커스 링 | 두 벌 — 프리미티브는 `focus-visible:ring-[3px] ring-ring/50`, raw 30개는 UA 기본 아웃라인 |
| 오류 표현 | 두 계열 — `text-destructive` 인라인 9회 vs `carix-danger` 박스 배너 3회 |
| 카드 | `<Card>` 8곳 vs 동일 클래스 문자열 인라인 복제 6곳 |
| 팔레트 누출 | 6파일 — admin 4, `routes/dashboard/history.tsx`, `routes/onboarding/expense.tsx` |

차트 색은 누출이 아니다. `IncomeVsPriceChart` 의 stroke/fill 은 전부
`var(--color-carix-*)` 토큰을 쓴다.

### 1-D. 로딩 표현 미통일

사용자 화면은 텍스트만 쓴다(`"불러오는 중입니다..."`, `"불러오는 중…"`, `"계산 중..."`).
admin 만 `animate-pulse` 스켈레톤과 `⏳ animate-spin` 을 쓴다. 공용 로딩 컴포넌트가 없다.

---

## 2. 결정 사항

| 축 | 결정 | 근거 |
| :--- | :--- | :--- |
| 통일 범위 | **시각 + UX 둘 다.** 프리미티브 계층을 유지하되 그 안을 B 시각 규칙으로 재정의하고, B 의 UX 규범(열화 상태 전용 화면·산출 근거 점진적 공개·빈 값을 0 으로 안 채우기)을 전 화면에 적용 | B 방향은 반(反)프리미티브가 아니다 — B 도 `mypage/profile.tsx` 에서는 프리미티브를 쓰고, `ui/card.tsx` 를 carix 토큰으로 통일한 것도 B 다 |
| 신호 라벨·색 출처 | **프런트 단일 테이블 + 서버 폴백.** 프런트 `decisionSignal.ts` 가 유일 출처, 서버 `label` 은 프런트에 매핑 없는 새 코드일 때만 | B 의 프런트 매핑 방향을 따르면서 서버 DTO 계약을 안 깬다 |
| 실행 주체 | **C 전담, 공용 계층만.** 개별 화면 교체는 각 소유자 | 개발 계획서 §1 「화면 셸 소유는 하나다」 를 안 깨는 유일한 배치. 공용 계층은 이미 C 소유(`coreApi`·라우트 가드 선례) |

---

## 3. 갈림길과 선택

| | **제자리 재정의 (채택)** | variant 추가 (기각) |
| :--- | :--- | :--- |
| 방식 | `Card`·`Button`·`Badge` **기본값**을 B 시각 언어로 교체 | B 스타일 variant 를 새로 달고 기존 기본값 유지 |
| C 화면 7개 | 한 커밋에 같이 바뀜 | 안 바뀜 (opt-in) |
| DRY | 해소 | 두 시각 언어가 공존 — **통일이 아님** |

제자리 재정의를 택한다. "B 방향으로 통일"의 문자 그대로다.

**대가를 명시한다.** `Button` 기본값이 `h-9 px-4 py-2 rounded-md text-sm` 에서
`rounded-lg px-4 py-1.5 text-xs` 로 가면 `dashboard/index`·`simulator`·`onboarding/profile`
`·income·expense·review`·`mypage/profile` 의 모든 버튼이 한 번에 작아지고 모서리가 바뀐다.

## 4. 안전망 현황

`@playwright/test` 와 `@axe-core/playwright` 는 설치돼 있고 `playwright.config.ts` 와
`e2e/` 스펙 둘(`landing`·`onboarding-flow`)이 있다. **그러나 시각 회귀는 돌지 않는다.**

- `landing.spec.ts` 는 `page.screenshot({ path })` 로 찍기만 하고 비교(`toHaveScreenshot`)를
  하지 않는다 — 베이스라인이 없으므로 회귀를 못 잡는다
- axe 검사는 랜딩 한 화면뿐
- `projects` 는 chromium 하나

§3 의 제자리 재정의는 7화면을 한 번에 바꾸므로 **베이스라인 캡처가 P0 에 들어간다.**
이것 없이 P3 을 하면 안 된다.

---

## 5. 단계

### P0 — 결정 신호 단일 출처 + 시각 회귀 베이스라인  ✅ 완료 (PR #161)

유일하게 급한 단계였다. 1-A 는 동작이 갈린 건이다. 태스크 단위 실행 계획은 병합과 함께
걷어냈다 — 납품 내역은 PR #161 이다.

- `app/lib/decisionSignal.ts` 신설 — `code → { label, tone, description }` 단일 테이블.
  서버 `label` 은 프런트에 매핑 없는 새 코드일 때만 폴백
- `lib/dashboardMarketApi.ts` 의 `SnapshotPoint` 에 `label` 필드를 `decisionSignal` 옆에
  나란히 추가. 코드는 코드로 두고 문구만 딸려오게 한다 — 서버 레코드와 같은 모양이다
- 백엔드 `GET /dashboard/history` 응답에 `label` 추가. `rulesApplied` 는
  `SnapshotRationale` 에 이미 있고, 근거 문장(`rationale`)은 `S-11` 이 렌더하지 않으므로
  넣지 않는다. §1 이 `summary`·`trends`·`history` 를 C 에 줬으므로 **레인 안**이다 —
  클라이언트 파일이 B 작성물이라는 것과 무관하다
- 서버 라벨 매핑(`DashboardSummaryService.label`)을 공용 자리로 빼 `history` 도 같은 것을
  쓰게 한다. 한글 문구가 세 벌이 되는 것이 지금 고치는 결함이다 — 서버는 `"스킬 보강"`,
  `history.tsx` 는 `"스킬 보강 우선"` 로 이미 갈려 있다
- **시각 회귀는 화면이 아니라 프리미티브 갤러리로 건다.** 인증이 필요한 화면 7개는
  `CARIX_E2E_EMAIL`/`CARIX_E2E_PASSWORD` 가 있어야 돌고 `onboarding-flow.spec.ts` 는 그것을
  「7주차 통합 점검용」으로 못박아 기본 스킵이다. 계정·시드·CI 시크릿을 세우는 것은 P0
  안에 들어갈 크기가 아니다. 대신 `dev/primitives` 라우트(기존 `dev/skills`·`dev/trends`
  선례)에 `Card`·`Button`·`Badge`·`Table` 을 variant × 상태 × 라이트/다크로 펼쳐
  `toHaveScreenshot` 으로 잠근다 — P3 이 실제로 바꾸는 층이 바로 거기다.
  **한계를 명시한다:** 갤러리는 `Card` 의 `gap` 변경이 C 화면 내부를 재배치하는 것을 못
  잡는다. 갤러리에 실제 화면과 같은 구성의 합성 카드(`CardHeader`/`Title`/`Content`/
  `Footer` 적층) 절을 함께 두어 간격 변화가 diff 로 드러나게 하는 것이 P0 크기에 맞는
  완화다. 화면 단위 베이스라인은 7주차 계정이 설 때 붙인다

### P1 — 토큰 확장  ✅ 완료 (PR #162)

- 다섯 번째 의미 색상을 `--carix-action` 으로 추가한다. 역할 이름이라야
  `positive`/`warning`/`danger`/`info` 와 같은 계열이 된다 — `--carix-purple` 같은 색 이름은
  그 규칙을 깬다. `accent` 는 쓰지 않는다: shadcn 이 `--accent` 를 이미
  `var(--carix-border)` 로 점유하고 있어 이름이 겹친다
- 라이트/다크 쌍을 함께 정의한다. 현재 `purple-400` 단일값이 실제 결함이다.
  출발값은 라이트 `#6d28d9` / 다크 `#a78bfa` 로 잡되, **기존 토큰과 같은 기준으로 대비를
  실측해 확정한다** — `docs/specs/ui-ux-design.md` §1.1 은 `positive` 5.29:1,
  `warning` 4.84:1 처럼 실측치를 적고 있고 새 토큰도 같은 자리에 실측치를 남긴다
- ~~포커스 링 토큰(`--ring`)을 raw 요소에서도 쓸 수 있는 유틸로 노출~~ — **뺀다.** P3 이
  raw `<button>` 30개를 `<Button>` 으로 바꾸고 `<Button>` 은 이미 `focus-visible:ring` 을
  갖는다. 그 유틸의 소비자를 P3 이 없앤다. P3 마이그레이션이 멈추면 다시 본다
- **`@custom-variant dark` 를 추가한다** — P0 최종 리뷰가 드러낸 것. 레포에 이 선언이 없어
  Tailwind v4 가 `dark:` 를 `prefers-color-scheme` 미디어쿼리로 컴파일하는데 `ThemeToggle` 은
  `.dark` 클래스를 건다. 10파일 22곳의 `dark:` 가 토글 경로에서 전부 무효다. 태스크 단위
  실행 계획은 병합과 함께 걷어냈다 — 납품 내역은 PR #162 다

### P2 — 공용 컴포넌트 3개  ✅ 완료 (PR #164)

**`SignalBadge` 는 이 목록에서 빠졌다** — P0 이 먼저 납품했다(`components/dashboard/SignalBadge.tsx`).
원래 네 개였고 결정 신호가 P0 의 본체였으므로 그쪽으로 당겨졌다.

| 컴포넌트 | 흡수 대상 |
| :--- | :--- |
| `ToneBadge` | 뱃지 표현 계층 한 벌. `SignalBadge` 가 들고 있던 클래스 문자열이 여기로 내려온다 |
| `EmptyState` | B 의 열화 박스를 표준으로. 알약 뱃지 + 제목 + 설명 + 대체 안내 슬롯 |
| `LoadingState` | 텍스트 3종 + admin 스켈레톤을 스켈레톤 하나로 |

**`StatusBadge` 는 만들지 않는다 — 결번.** 이 자리에 `ToneBadge` 가 들어간다.
원래 계획은 `IndexCard` 의 `STATUS_BADGE_LABEL` 과 `SkillDemandSection` 의 `getTrendBadge` 를
"둘로 갈린 하나"로 보고 합치는 것이었다. **그 전제가 틀렸다.** 둘은 서로 다른 서버 enum 이다 —
`IndexStatus`(5종)와 `SkillDemandStatus`(4종)는 각자 다른 도메인의 어휘이고 애초에 하나였던 적이
없다. 어휘를 합치면 없는 상태값을 서로에게 노출하게 된다.

합칠 수 있는 것은 어휘가 아니라 **표현 계층**이다. P0 이 결정 신호에서 세운 구조를 그대로 복제한다:

- `ToneBadge` — `tone` + 내용만 받는 표현 프리미티브. 어휘를 모른다.
- 도메인별 해석 모듈 1개씩 (`lib/indexStatus.ts`, `lib/skillDemandStatus.ts`) — `decisionSignal.ts` 와
  같은 모양. 각자 자기 코드→{문구, 톤} 표와 미지 코드용 `muted` 폴백을 가진다.
- `SignalBadge` 는 `ToneBadge` 위의 얇은 래퍼로 내려앉는다.

**같이 고치는 계약 결함.** `dashboardMarketApi.ts` 의 `SkillDemandDetail.status` 가 맨 `string` 이라
어휘가 서버와 말없이 어긋나 있었다 — P0 이 `decisionSignal` 에서 고친 것과 같은 부류다. 실제 상태:

| 프런트 `getTrendBadge` 분기 | 서버 |
| :--- | :--- |
| `CUSTOM_SKILL_NO_STATS` | 내려온다 (`MarketValueIndexServiceImpl` 의 문자열 리터럴) |
| `NEW_OBSERVATION` | 내려온다 |
| `SAMPLE_TOO_SMALL` | **어디에도 없다.** 죽은 분기 |
| `NO_RECENT_DEMAND` | 이 엔드포인트로는 안 내려온다 (`excludedSkills` 쪽 어휘) |

프런트 타입은 현재 내려오는 3종이 아니라 `SkillDemandStatus` 어휘 전체 + `CUSTOM_SKILL_NO_STATS`
를 덮는다. 아래 서버 결함이 고쳐지면 바로 맞아 들어가게 하려는 것이고, 그때까지 남는 분기는
폴백이 받는다. 백엔드 `IndexContractVocabularyTest` 에 대응하는 프런트 계약 테스트를 같이 세운다 —
이번 어긋남을 잡았을 물건이 그것뿐이다.

**범위 밖으로 넘기는 서버 결함 (P2 에서 고치지 않는다).**
`MarketValueIndexServiceImpl#buildSkillDemandDetails` 의 `overallStatus` 는 `"OK"` 로 시작해
`NEW_OBSERVATION` 일 때만 바뀐다. `SkillDemandCalculator` 가 계산한 `INSUFFICIENT_SAMPLE`·
`NO_RECENT_DEMAND` 는 집계에서 버려지고, 해당 스킬은 `status:"OK"` 에 정상 `demandIndex` 를 달고
나간다 — 표본이 모자란 스킬이 화면에서 정상 추세로 보인다. 이 목록은 제외 스킬을 걸러내지 않고
보유 스킬 전체를 순회하므로 회피 경로도 없다. 표시 규칙이 바뀌는 제품 판단이라 UI 통일 축에서
처리하지 않고 별건으로 올린다.

`EmptyState` 의 형태는 `SalaryDistributionChart` 의 표본 부족·공개연봉 미제공 상태를
기준으로 잡는다 — 실제 표본 수와 최소 기준, 적용된 완화 조건, 대체 지표 안내까지 담는
형태가 이미 거기 있다.

**P2 가 P3 로 넘긴 것 (최종 리뷰에서 나온 것들).**

| 항목 | 내용 |
| :--- | :--- |
| `EmptyState` 의 제목 태그 | `h4` 로 고정돼 있다. `history.tsx` 에서 `h1 → h4` 로 한 단계를 건너뛴다. 선행 문제(삭제한 블록도 `h4` 였다)지만 이제 공용 프리미티브에 박혀 있어 P3/P4 가 호출부를 늘리면 같이 번진다. `headingLevel` prop 을 받되 기본값은 현행 유지. |
| 손수 뱃지 잔여 8건 | `admin/JobHistoryTable`, `admin/CollectorStatusBanner`, `hero/Hero`, `onboarding/skills`, `SalaryDistributionChart`. 앞 넷은 P4, `SalaryDistributionChart` 는 P3 (열화 상태 3종이 본문에 표본 수·완화 조건을 엮고 있어 프리미티브 재정의와 같이 간다). |
| `history.tsx` 로딩 분기 | 기계 검증이 0인 유일한 자리다. 모든 테스트가 `fetchSnapshotHistory` 를 즉시 resolve 해서 `isLoading` 을 타지 않는다. |
| 시각 커버리지 없는 호출부 | `IndexCard` 의 뱃지(P0 의 `w-fit` 늘어남 버그가 빠져나간 바로 그 레이아웃 맥락인데 아직 갤러리 밖), `SkillDemandSection` 의 `variant="plain"` 빈 상태(갤러리엔 합성 카드만 있다). |

**서버 수정 시 같이 볼 것.** 아래 서버 결함이 고쳐져 `INSUFFICIENT_SAMPLE`·`NO_RECENT_DEMAND` 행이
실제로 내려오기 시작하면, `SkillDemandSection` 의 가중치 셀이 `CUSTOM_SKILL_NO_STATS` 만 걸러
"-" 를 그리므로 그 행들은 숫자 분기로 흘러 가중치 %를 렌더한다. 그때 `weight` 에 뭐가 들어오는지
확인할 것 — 이미 처리됐다고 가정하지 말 것.

### P3 — 프리미티브 제자리 재정의

- `Card` — `py-6` + 자식 `px-6` 구조를 B 의 `p-6` 단일로. `gap-6` 기본값은 B 카드가
  자식 간격을 `gap` 이 아니라 개별 여백(`mt-1`·`mb-6`·`pb-5`)으로 잡고 있어 그대로 옮길
  값이 없다 — P0 베이스라인 스크린샷 대조로 정한다
- `Button` — `rounded-lg px-4 py-1.5 text-xs font-medium`, hover `opacity-90`,
  disabled `opacity-50`. **단 `focus-visible:ring` 은 남긴다.** B 의 raw button 30개에 없던
  것이고, 이 항목만 C 쪽을 지킨다
- `Badge` — B 의 두 종류(상태 `rounded` / 칩 `rounded-full`)를 variant 로
- `Table` — 죽은 `ui/table.tsx` 를 B 의 `thead` 스타일로 되살림
- `alert`·`select` — import 0. 삭제하고 역할은 `EmptyState` 가 흡수

**수동 다크모드 확인 시 주의.** `/dev/trends`·`/dev/skills`·`/dev/primitives` 는
`routes.ts` 에서 `AppShell` 밖에 매핑돼 `ThemeToggle` 이 마운트되지 않는다 — devtools
로 `documentElement` 에 클래스를 수동 주입하지 않는 한 `.dark`/`.light` 클래스가 전혀
걸리지 않는다. `primitives.spec.ts` 는 클래스를 직접 토글하므로 자동 베이스라인은
영향이 없다. 문제는 사람이 이 URL 을 열어 `dark:` 유틸(예: `SalaryDistributionChart`
의 `dark:ring-carix-surface`)을 육안 확인할 때다 — OS 가 다크면 토큰만 다크값으로
바뀌고 `dark:` 규칙은 안 걸려, 운영에는 없는 상태(흰 마커 링이 남는 것)를 버그로
오인하게 된다. 수동 확인은 `ThemeToggle` 이 실제로 뜨는 라우트(`/dashboard/trends`,
`/onboarding/skills` 등)에서 토글을 눌러 해야 한다.

### P3 착수 전 — 시각 회귀 그물 보강

P3 착수 전에 시각 회귀 그물을 아래 순서로 보강한다.

1. **갤러리부터 넓힌다.** `routes/dev/primitives.tsx` 는 `Button`·`Badge`·`Card`·
   `Table` 과 합성 카드까지만 덮는다. `Input`·`Select`·`NativeSelect`, 차트 마커
   링은 갤러리에 없다 — 그 상태로 computed-style 단언(2번)을 추가해도 이미
   픽셀이 덮는 것만 다시 덮는다. 갤러리를 미커버 컴포넌트로 넓히는 것이 2번의
   전제 조건이다.
2. **`getComputedStyle` 기반 단언을 1급 테스트로 추가한다** — 톤·프리미티브
   색상별로. 허용오차에 흔들리지 않고, 재색상화를 직접 재는 유일한 계측기다.
   스크린샷은 이 종류의 변경에는 애초에 맞지 않는 도구다.
3. **`expect.toHaveScreenshot.threshold` 를 0.05 이하로 좁히고 1회
   재베이스라인한다.** AA·폰트 힌팅 노이즈를 관찰할 것. 0.1 이 아니라 0.05 인
   이유: Playwright pixelmatch 컷오프는 `35215 × threshold²` 다. 0.1 은 352.15 로,
   이 결함을 드러낸 YIQ 델타 268.43 / 102.67 보다 커서 정작 잡아야 할 변경에
   대해 무효(no-op)다. 0.05 는 88.04 로 둘 다 잡지만, 다크 쪽 델타는 그 문턱을
   약 17% 차이로 겨우 넘는다.
4. **P3 브리프에 "`--update-snapshots` 후 `*.png` diff 없음"이 "색 변화 없음"의
   증거가 아니라고 명시한다.** `--update-snapshots` 기본 모드 `changed` 는
   허용오차 안의 렌더는 파일을 바이트 단위로 그대로 둔다. computed-style
   확인은 선택이 아닌 필수 단계여야 한다.
5. **`maxDiffPixels`/`maxDiffPixelRatio` 는 "조이는" 용도로 건드리지 않는다.**
   설치된 Playwright 에서 미설정 값은 이미 0(가장 엄격한 값)으로 해석되고,
   비교는 `count > maxDiffPixels` 일 때만 실패한다. 0 이 아닌 값을 넣으면
   그물을 조이는 것이 아니라 **느슨하게 만들 수만 있다** — 예를 들어 500×300
   캡처에 `maxDiffPixelRatio: 0.01` 을 걸면 약 1500 픽셀의 차이를 공짜로
   허용하게 된다. 3번이 노이즈로 불안정하다고 판명될 때만, 관측된 노이즈
   크기에 맞춰 flake 허용 노브로 쓴다.

미니파이된 CSS 를 검증할 때 쓴 형태도 재도출을 두 번 겪었으므로 여기 남긴다 —
단일행 빌드 산출물을 grep 가능하게 바꾸는 형태다.

```sh
tr '}' '\n' < build/client/assets/root-*.css | grep ...
```

### P4 — 규범 반영과 회귀 방지

- `docs/specs/ui-ux-design.md` 에 컴포넌트 계약·상태 표현 규칙 반영.
  같은 기회에 §4.2 표류를 고친다 — 문서는 Range Bar / Box Plot 을 적고 있는데 실제 화면은
  인체 실루엣 차트다
- eslint 규칙으로 회귀를 막는다
  - 토큰 밖 Tailwind 팔레트 클래스 금지 (`bg-blue-500` 등)
  - 허용 목록 밖 raw `<button>` 금지

---

## 6. 마이그레이션 계약

C 는 공용 계층만 낸다. 화면 교체는 소유자 몫이므로 **이 표가 곧 납품물이다.**

| 소유 | 파일 | 교체 |
| :--- | :--- | :--- |
| B | `SalaryDistributionChart`·`SkillDemandSection`·`IncomeVsPriceChart`·`routes/dashboard/history`·`trends`·`routes/onboarding/skills` | raw 카드 6 → `Card`, raw button 10종 → `Button`, raw table 3 → `Table`, 열화 박스 → `EmptyState`, 상태 span → `StatusBadge`, `getSignalBadge` → `SignalBadge` |
| 박지수 | `components/admin/` 6파일 + `routes/admin/collector.tsx` | 팔레트 누출 → 토큰, raw button → `Button`, `animate-pulse` → `LoadingState`, `collector.tsx` 상태 뱃지 `-300` → 테이블과 동일한 `-400`(P4 로 이연) |
| C | 온보딩 4화면·대시보드·시뮬레이터·`mypage/profile` | P3 으로 자동 반영. `routes/onboarding/expense.tsx` 의 amber 누출만 수동 |

**실패 모드.** 교체 PR 이 들어오지 않으면 공용 계층과 raw 복제본이 영구 공존한다. 통일이
아니라 계층이 하나 더 생기는 것으로 끝난다. P4 의 eslint 규칙이 그걸 막는 유일한 장치이므로
**P4 를 뒤로 미루면 안 된다.**

## 7. 범위 밖

- **프런트 API 클라이언트 병합** — `lib/api/dashboard.ts` 와 `lib/dashboardMarketApi.ts` 가
  같은 대시보드 도메인을 쪼개 갖고 있는 것이 1-A 의 원인이다. 별도 건으로 다룬다
- **`MarketValueIndexServiceImpl` 1026줄** — 코딩 규칙 800줄 상한을 넘는 레포 유일 파일.
  UI 와 무관하다
- **`SalaryDistributionChart` 의 격자 두 벌** — `getBodyPartInfo` 의 `relativeY` 가 죽은
  값이고 `percentileToBodyY` 와 경계가 어긋난다. B 소유 컴포넌트 내부 결함이라 위 교체표의
  작업과 함께 B 가 다룬다
