# UI 일관성 통일 계획 (B 시각·UX 규범 채택)

프런트엔드가 두 벌의 시각 언어와 두 벌의 DTO 를 동시에 갖고 있다. 이 문서는 그것을
하나로 모으는 실행 계획이다. 규범 자체는 코드가 들어온 뒤 `docs/specs/ui-ux-design.md`
에 반영한다 — 문서는 현재 상태만 서술하므로 구현보다 먼저 쓰지 않는다.

---

## 1. 남은 불일치 — 프리미티브 미채택

디자인 시스템이 있는데 절반이 쓰이지 않는다. 아래는 실측치다.

| 항목 | 실측 |
| :--- | :--- |
| 죽은 프리미티브 | `ui/alert.tsx`·`ui/select.tsx`·`ui/table.tsx` — import 0 |
| raw `<table>` | 11곳. `ui/table.tsx` 는 아무도 안 씀 |
| raw `<button>` | 34개 / 서로 다른 className 문자열 10종 |
| 포커스 링 | 두 벌 — 프리미티브는 `focus-visible:ring-[3px] ring-ring/50`, raw 는 UA 기본 아웃라인 |
| 오류 표현 | 두 계열 — `text-destructive` 인라인 vs `carix-danger` 박스 배너 |
| 카드 | `<Card>` vs 동일 클래스 문자열 인라인 복제 |
| 팔레트 누출 | admin 4파일, `routes/onboarding/expense.tsx` |

차트 색은 누출이 아니다. `IncomeVsPriceChart` 의 stroke/fill 은 전부
`var(--color-carix-*)` 토큰을 쓴다.

**결정 신호 분기·토큰 공백·로딩 표현은 P0~P2 가 해소했다.** 남은 것은 이 층뿐이고
P3·P4 가 다룬다.

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

## 4. 단계

### 완료 — P0·P1·P2

| 단계 | 납품 | PR |
| :--- | :--- | :--- |
| P0 | 결정 신호 단일 출처(`lib/decisionSignal.ts`)와 서버 `label` 폴백, `GET /dashboard/history` 응답의 `label`, `dev/primitives` 갤러리 시각 회귀 베이스라인 | #161 |
| P1 | 다섯 번째 의미 색상 `--carix-action`(라이트/다크 쌍), `@custom-variant dark` — 이것이 없어 `dark:` 22곳이 토글 경로에서 무효였다 | #162 |
| P2 | `ToneBadge`·`EmptyState`·`LoadingState` 표현 계층, 도메인별 해석 모듈(`lib/indexStatus.ts`·`lib/skillDemandStatus.ts`), `SkillDemandDetail.status` 와이어 타입 좁히기 | #164 |

**`StatusBadge` 는 결번이다.** `IndexStatus`(5종)와 `SkillDemandStatus`(4종)를 "둘로 갈린
하나"로 본 전제가 틀렸다 — 서로 다른 도메인 어휘라 합치면 없는 상태값을 노출한다. 합칠 수
있는 것은 어휘가 아니라 표현 계층이고 그 자리를 `ToneBadge` 가 갖는다.

**화면 단위 시각 회귀 베이스라인은 아직 없다.** 인증이 필요한 7화면은
`CARIX_E2E_EMAIL`/`CARIX_E2E_PASSWORD` 게이트 뒤에 있어 갤러리로 대신했다. 갤러리는
`Card` 의 `gap` 변경이 화면 내부를 재배치하는 것을 못 잡는다. 계정·시드·CI 시크릿이
설 때 화면 베이스라인을 붙인다.

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

**P2 가 넘긴 것.**

| 항목 | 내용 |
| :--- | :--- |
| `EmptyState` 의 제목 태그 | `h4` 로 고정돼 있어 `history.tsx` 에서 `h1 → h4` 로 한 단계를 건너뛴다. 공용 프리미티브에 박혀 있어 호출부가 늘면 같이 번진다. `headingLevel` prop 을 받되 기본값은 현행 유지 |
| 손수 뱃지 잔여 | `SalaryDistributionChart` 는 P3 — 열화 상태 3종이 본문에 표본 수·완화 조건을 엮고 있어 프리미티브 재정의와 같이 간다. `admin/JobHistoryTable`·`admin/CollectorStatusBanner`·`hero/Hero`·`onboarding/skills` 넷은 P4 |
| `history.tsx` 로딩 분기 | 기계 검증이 0인 유일한 자리다. 모든 테스트가 `fetchSnapshotHistory` 를 즉시 resolve 해서 `isLoading` 을 안 탄다 |
| 시각 커버리지 없는 호출부 | `IndexCard` 의 뱃지, `SkillDemandSection` 의 `variant="plain"` 빈 상태 — 갤러리엔 합성 카드만 있다 |

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

## 5. 마이그레이션 계약

C 는 공용 계층만 낸다. 화면 교체는 소유자 몫이므로 **이 표가 곧 납품물이다.**

| 소유 | 파일 | 교체 |
| :--- | :--- | :--- |
| B | `SalaryDistributionChart`·`SkillDemandSection`·`IncomeVsPriceChart`·`routes/dashboard/history`·`trends`·`routes/onboarding/skills` | raw 카드 6 → `Card`, raw button 10종 → `Button`, raw table 3 → `Table`, 열화 박스 → `EmptyState`, 상태 span → `ToneBadge`, `getSignalBadge` → `SignalBadge` |
| 박지수 | `components/admin/` 6파일 + `routes/admin/collector.tsx` | 팔레트 누출 → 토큰, raw button → `Button`, `animate-pulse` → `LoadingState`, `collector.tsx` 상태 뱃지 `-300` → 테이블과 동일한 `-400`(P4 로 이연) |
| C | 온보딩 4화면·대시보드·시뮬레이터·`mypage/profile` | P3 으로 자동 반영. `routes/onboarding/expense.tsx` 의 amber 누출만 수동 |

**실패 모드.** 교체 PR 이 들어오지 않으면 공용 계층과 raw 복제본이 영구 공존한다. 통일이
아니라 계층이 하나 더 생기는 것으로 끝난다. P4 의 eslint 규칙이 그걸 막는 유일한 장치이므로
**P4 를 뒤로 미루면 안 된다.**

## 6. 범위 밖

- **프런트 API 클라이언트 병합** — `lib/api/dashboard.ts` 와 `lib/dashboardMarketApi.ts` 가
  같은 대시보드 도메인을 쪼개 갖고 있다. P0 이 고친 결정 신호 분기의 뿌리이고, 병합은 별도 건으로 다룬다
- **`MarketValueIndexServiceImpl` 1026줄** — 코딩 규칙 800줄 상한을 넘는 레포 유일 파일.
  UI 와 무관하다
- **`SalaryDistributionChart` 의 격자 두 벌** — `getBodyPartInfo` 의 `relativeY` 가 죽은
  값이고 `percentileToBodyY` 와 경계가 어긋난다. B 소유 컴포넌트 내부 결함이라 위 교체표의
  작업과 함께 B 가 다룬다
