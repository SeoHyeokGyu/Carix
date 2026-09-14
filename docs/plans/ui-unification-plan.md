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

### P0 — 결정 신호 단일 출처 + 시각 회귀 베이스라인

유일하게 급한 단계다. 1-A 는 동작이 갈린 건이다.

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

### P1 — 토큰 확장

- 다섯 번째 의미 색상을 `--carix-action` 으로 추가한다. 역할 이름이라야
  `positive`/`warning`/`danger`/`info` 와 같은 계열이 된다 — `--carix-purple` 같은 색 이름은
  그 규칙을 깬다. `accent` 는 쓰지 않는다: shadcn 이 `--accent` 를 이미
  `var(--carix-border)` 로 점유하고 있어 이름이 겹친다
- 라이트/다크 쌍을 함께 정의한다. 현재 `purple-400` 단일값이 실제 결함이다.
  출발값은 라이트 `#6d28d9` / 다크 `#a78bfa` 로 잡되, **기존 토큰과 같은 기준으로 대비를
  실측해 확정한다** — `docs/specs/ui-ux-design.md` §1.1 은 `positive` 5.29:1,
  `warning` 4.84:1 처럼 실측치를 적고 있고 새 토큰도 같은 자리에 실측치를 남긴다
- 포커스 링 토큰(`--ring`)을 raw 요소에서도 쓸 수 있는 유틸로 노출

### P2 — 공용 컴포넌트 4개

| 컴포넌트 | 흡수 대상 |
| :--- | :--- |
| `SignalBadge` | `history.tsx` `getSignalBadge` + `dashboard/index.tsx` 의 `<Badge>` |
| `StatusBadge` | `IndexCard` 의 `STATUS_BADGE_LABEL` + `SkillDemandSection` 의 `getTrendBadge` — 현재 둘로 갈림 |
| `EmptyState` | B 의 열화 박스를 표준으로. 알약 뱃지 + 제목 + 설명 + 대체 안내 슬롯 |
| `LoadingState` | 텍스트 3종 + admin 스켈레톤을 스켈레톤 하나로 |

`EmptyState` 의 형태는 `SalaryDistributionChart` 의 표본 부족·공개연봉 미제공 상태를
기준으로 잡는다 — 실제 표본 수와 최소 기준, 적용된 완화 조건, 대체 지표 안내까지 담는
형태가 이미 거기 있다.

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
| 박지수 | `components/admin/` 4파일 | 팔레트 누출 → 토큰, raw button → `Button`, `animate-pulse` → `LoadingState` |
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
