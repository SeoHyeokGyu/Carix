# UI 일관성 통일 P1 실행 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 클래스 기반 테마 전환에서 `dark:` 유틸이 실제로 발화하게 만들고, 결정 신호 다섯 번째 의미 색상을 토큰으로 세운다.

**Architecture:** `@custom-variant dark` 한 줄로 Tailwind v4 의 `dark:` 를 미디어쿼리에서 `.dark` 클래스 기준으로 돌린다 — 앱의 테마 토글이 이미 그 클래스를 쓴다. 그 위에 `--carix-action` 을 기존 네 토큰과 같은 형태(라이트/다크 쌍, 대비 실측)로 추가하고, P0 이 임시로 넣은 raw 팔레트와 `[.dark_&]` 우회를 걷는다.

**Tech Stack:** React Router 7 + TypeScript + Tailwind v4, Vitest, Playwright.

**Spec:** `docs/plans/ui-unification-plan.md`

## Global Constraints

- 의미 색상 토큰은 **역할 이름**을 쓴다 — `positive`·`warning`·`danger`·`info` 계열. 색 이름(`purple`)은 규칙을 깬다. `accent` 는 shadcn 이 `--accent: var(--carix-border)` 로 점유하고 있어 쓸 수 없다.
- 모든 의미 색상 토큰은 **라이트/다크 쌍**을 갖는다. 단일값은 결함이다.
- `docs/specs/ui-ux-design.md` §1.1 은 대비 실측치를 적는다 (`positive` 5.29:1, `warning` 4.84:1). 새 토큰도 같은 자리에 같은 형식으로 남긴다.
- 커밋 메시지는 한글 Conventional Commits. `Co-Authored-By` 트레일러와 세션/어시스턴트 귀속 줄은 넣지 않는다.
- 프런트 검증 5종: `pnpm lint`, `pnpm typecheck`, `pnpm format:check`, `pnpm test:unit`, `pnpm build`.
- 시각 회귀: `pnpm test:e2e primitives.spec.ts`. 베이스라인은 `chromium-darwin` 전용이므로 P0 을 만든 것과 같은 OS 에서 돌린다.

---

## 배경 — P1 이 스펙보다 커진 이유

스펙 §5 의 P1 은 「토큰 하나 추가」였다. P0 의 최종 리뷰가 그보다 큰 것을 드러냈다.

**`dark:` 유틸이 앱 전체에서 죽어 있다.** 레포에 `@custom-variant dark` 가 없어 Tailwind v4 는
`dark:` 를 `@media (prefers-color-scheme: dark)` 로 컴파일한다. 그런데
`app/components/ThemeToggle.tsx` 는 `document.documentElement` 에 `.dark`/`.light`
**클래스**를 건다. 두 메커니즘은 독립이다 — 사용자가 토글을 눌러도 `dark:` 규칙은 하나도
발화하지 않고, OS 설정이 다크일 때만 발화한다.

현재 `dark:` 를 쓰는 곳은 10파일 22곳이다.

| 파일 | 건수 |
| :--- | ---: |
| `components/ui/button.tsx` | 4 |
| `routes/admin/collector.tsx` | 3 |
| `components/dashboard/SalaryDistributionChart.tsx` | 3 |
| `components/admin/JobHistoryTable.tsx` | 3 |
| `components/ui/input.tsx` | 2 |
| `components/ui/badge.tsx` | 2 |
| `components/admin/RecentPostingsTable.tsx` | 2 |
| `routes/onboarding/expense.tsx` | 1 |
| `components/ui/select.tsx` | 1 |
| `components/onboarding/NativeSelect.tsx` | 1 |

전부 의도를 갖고 쓰인 것인데 토글 경로에서 무효다. P0 은 이 문제를 자기 한 줄에 대해서만
`[.dark_&]:` 임의 변형으로 우회했고, 나머지는 「기존 문제, 다음 단계 몫」으로 남겼다. 여기가
그 다음 단계다.

**결정: `@custom-variant dark` 를 추가한다.** 22곳이 한 번에 발화하기 시작하므로 넓은 시각
변화다. 그래도 대안(앞으로 계속 `[.dark_&]:` 를 손으로 쓰는 것)은 죽은 코드 22곳을 그대로 둔
채 우회만 늘리는 것이라 방어할 수 없다. **이것이 이 계획에서 가장 위험한 변경이고, Task 1 을
따로 둔 이유다** — 되돌릴 때 한 커밋만 되돌리면 된다.

**스펙에서 뺀 것.** 스펙 P1 의 세 번째 항목(「포커스 링 토큰을 raw 요소용 유틸로 노출」)은
넣지 않는다. P3 이 raw `<button>` 30개를 `<Button>` 으로 바꾸고 `<Button>` 은 이미
`focus-visible:ring` 을 갖는다 — 그 유틸의 소비자를 P3 이 없앤다. P3 마이그레이션이
멈추면 그때 다시 본다.

---

## File Structure

**수정**

| 파일 | 변경 |
| :--- | :--- |
| `frontend/app/styles/tokens.css` | `@custom-variant dark` 추가 (Task 1). `--carix-action` 라이트/다크 쌍 + `@theme` 등록 (Task 2) |
| `frontend/app/lib/decisionSignal.ts` | `SIGNAL_TONE_CLASS.action` 을 토큰으로, `[.dark_&]` 우회 제거, `NOTE` 주석 갱신 (Task 2) |
| `frontend/app/styles/tokens.test.ts` | 새 토큰 hex 단언 추가 (Task 2) |
| `docs/specs/ui-ux-design.md` | §1.1 에 새 토큰과 대비 실측치 (Task 2) |
| `frontend/e2e/primitives.spec.ts-snapshots/*.png` | 두 태스크 모두 재생성 |

**생성:** 없음.

---

### Task 1: `dark:` 유틸을 클래스 기준으로 돌린다

**Files:**
- Modify: `frontend/app/styles/tokens.css`
- Regenerate: `frontend/e2e/primitives.spec.ts-snapshots/*.png`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: 레포 전역에서 `dark:*` 유틸이 `.dark` 클래스 하위에서 발화한다. Task 2 는 이것을 전제로 `[.dark_&]:` 우회를 걷는다.

- [ ] **Step 1: 변경 전 상태를 증거로 남긴다**

Run:
```bash
cd frontend && pnpm build
grep -c 'prefers-color-scheme' build/client/assets/root-*.css
```
결과를 기록한다. 변경 후 이 숫자가 줄어야 한다 — `dark:` 유틸이 미디어쿼리에서 빠져나오기 때문이다.

- [ ] **Step 2: `@custom-variant dark` 를 추가한다**

`frontend/app/styles/tokens.css` 의 `@import "tailwindcss";` 바로 아래에 넣는다.

```css
@import "tailwindcss";

/*
 * `dark:` 를 `.dark` 클래스 기준으로 돌린다.
 *
 * Tailwind v4 의 기본 `dark:` 는 `@media (prefers-color-scheme: dark)` 로 컴파일된다.
 * 그런데 `ThemeToggle` 은 `document.documentElement` 에 `.dark`/`.light` 클래스를 건다 —
 * 두 메커니즘이 독립이라 토글을 눌러도 `dark:` 규칙이 하나도 발화하지 않았다.
 * 아래 :root 의 `@media (prefers-color-scheme: dark)` 블록은 그대로 둔다. 그쪽은 토글을
 * 건드리지 않은 사용자의 기본값을 담당한다.
 */
@custom-variant dark (&:where(.dark, .dark *));
```

- [ ] **Step 3: 컴파일 결과가 실제로 바뀌었는지 확인한다**

Run:
```bash
cd frontend && pnpm build
grep -o '\.dark [^{]*dark\\:[^{]*' build/client/assets/root-*.css | head -5
grep -c 'prefers-color-scheme' build/client/assets/root-*.css
```
Expected: 첫 명령이 `.dark` 를 선행 선택자로 갖는 규칙을 출력한다. 두 번째 숫자가 Step 1 보다 작다.

둘 중 하나라도 아니면 멈추고 보고한다 — 변형이 안 걸린 것이다.

- [ ] **Step 4: 발화하기 시작하는 22곳을 눈으로 확인한다**

아래 10파일이 영향을 받는다. 각 파일에서 `dark:` 가 붙은 선언이 **다크 테마에서 의도한 개선인지** 확인하고, 퇴행으로 보이는 것이 있으면 목록으로 보고한다. 고치지는 말 것 — 이 태스크는 변형을 켜는 것까지다.

```
components/ui/button.tsx            components/ui/input.tsx
components/ui/badge.tsx             components/ui/select.tsx
components/onboarding/NativeSelect.tsx
components/dashboard/SalaryDistributionChart.tsx
components/admin/JobHistoryTable.tsx
components/admin/RecentPostingsTable.tsx
routes/admin/collector.tsx          routes/onboarding/expense.tsx
```

특히 볼 것: `JobHistoryTable`·`RecentPostingsTable`·`collector`·`expense` 는 토큰 밖 팔레트
(`dark:text-blue-400`·`dark:text-rose-400`·`dark:text-amber-400` 등)를 쓴다. 이들은 P4 의 raw
팔레트 금지 규칙 대상이기도 하다 — 지금 고치지 말고 보고서에만 적는다.

- [ ] **Step 5: 베이스라인을 재생성하고 diff 를 판독한다**

Run: `cd frontend && pnpm test:e2e primitives.spec.ts --update-snapshots`

`git diff --stat` 으로 **어느 스냅샷이 바뀌었는지** 확인한다. `button.tsx`·`badge.tsx`·
`input.tsx`·`select.tsx` 가 `dark:` 를 쓰므로 **다크 쪽 스냅샷만** 바뀌어야 한다.
라이트 스냅샷이 바뀌었으면 멈추고 보고한다 — 변형이 라이트에도 새는 것이다.

바뀐 다크 스냅샷을 실제로 열어 보고, 무엇이 달라졌는지 한 줄씩 보고서에 적는다.

- [ ] **Step 6: 해시 고유성 재확인**

Run:
```bash
cd frontend/e2e/primitives.spec.ts-snapshots
ls | wc -l
shasum -a 256 *.png | awk '{print $1}' | sort -u | wc -l
```
Expected: 둘 다 14. 어느 라이트/다크 쌍이라도 같아지면 멈추고 보고한다.

- [ ] **Step 7: 전체 검증**

Run: `cd frontend && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test:unit && pnpm build && pnpm test:e2e primitives.spec.ts`
Expected: 전부 통과.

- [ ] **Step 8: 커밋**

```bash
git add frontend/app/styles/tokens.css frontend/e2e/primitives.spec.ts-snapshots
git commit -m "fix(frontend): dark: 유틸을 클래스 기준으로 돌려 테마 토글에서 발화하게 한다"
```

---

### Task 2: `--carix-action` 토큰

**Files:**
- Modify: `frontend/app/styles/tokens.css`
- Modify: `frontend/app/styles/tokens.test.ts`
- Modify: `frontend/app/lib/decisionSignal.ts`
- Modify: `docs/specs/ui-ux-design.md`
- Regenerate: `frontend/e2e/primitives.spec.ts-snapshots/primitives-badges-*.png`

**Interfaces:**
- Consumes: Task 1 의 `@custom-variant dark` — 이것이 있어야 아래에서 평범한 `dark:` 를 쓸 수 있다.
- Produces: `--carix-action` / `--color-carix-action`. P2·P3 의 컴포넌트가 다섯 번째 의미 색상이 필요할 때 이것을 쓴다.

- [ ] **Step 1: 대비를 실측한다**

후보값은 라이트 `#6d28d9`, 다크 `#a78bfa` 다. 계산상 라이트 표면(`#ffffff`) 대비 약 7.1:1,
다크 표면(`#1f2937`) 대비 약 5.4:1 로 둘 다 WCAG AA 를 넘는다. **계산을 믿지 말고 실측한다.**

WCAG 상대휘도 공식으로 네 조합을 계산하는 일회용 스크립트를 쓰고, 결과를 보고서에 적는다.

| 전경 | 배경 | 기대 |
| :--- | :--- | :--- |
| `#6d28d9` | `#ffffff` (라이트 표면) | ≥ 4.5:1 |
| `#6d28d9` | `#f3f4f6` (라이트 배경) | ≥ 4.5:1 |
| `#a78bfa` | `#1f2937` (다크 표면) | ≥ 4.5:1 |
| `#a78bfa` | `#111827` (다크 배경) | ≥ 4.5:1 |

넷 중 하나라도 4.5:1 미만이면 그 쪽 명도를 한 단계 조정해 다시 재고, 최종값과 실측치를 보고한다.
스크립트는 커밋하지 않는다.

- [ ] **Step 2: 토큰 테스트에 실패 단언을 추가한다**

`frontend/app/styles/tokens.test.ts` 의 「강조 색상 4종」 테스트를 5종으로 넓힌다.
`#a78bfa` 는 Step 1 에서 확정한 다크값으로 바꾼다.

```ts
  it("강조 색상 5종 hex를 spec 값 그대로 포함한다", () => {
    expect(css).toContain("#10b981");
    expect(css).toContain("#f97316");
    expect(css).toContain("#ef4444");
    expect(css).toContain("#4f46e5");
    expect(css).toContain("#a78bfa");
  });
```

- [ ] **Step 3: 실패 확인**

Run: `cd frontend && pnpm vitest run app/styles/tokens.test.ts`
Expected: FAIL — `tokens.css` 에 아직 그 hex 가 없다.

- [ ] **Step 4: 토큰을 등록한다**

`frontend/app/styles/tokens.css` 네 곳에 넣는다. 위치는 기존 네 토큰 바로 뒤다.

`@theme` 블록:
```css
  --color-carix-action: var(--carix-action);
```

`:root` (라이트):
```css
    --carix-action: #6d28d9; /* 바이올렛 (Step 1 실측치로 확정) */
```

`@media (prefers-color-scheme: dark) { :root { ... } }` 와 `.dark` 에 다크값을, `.light` 에
라이트값을 같은 형식으로 넣는다. **`.light` 에도 넣어야 한다** — 다크 OS 에서 라이트로
토글한 사용자가 있다.

- [ ] **Step 5: 통과 확인**

Run: `cd frontend && pnpm vitest run app/styles/tokens.test.ts`
Expected: PASS

- [ ] **Step 6: `action` 톤을 토큰으로 바꾼다**

`frontend/app/lib/decisionSignal.ts` 의 `SIGNAL_TONE_CLASS` 에서 `action` 줄과 그 위 주석을
바꾼다. raw 팔레트와 `[.dark_&]` 우회가 둘 다 사라진다.

```ts
/** 톤 → 클래스. 다섯 톤 모두 의미 색상 토큰을 쓴다. */
export const SIGNAL_TONE_CLASS: Record<SignalTone, string> = {
  positive: "bg-carix-positive/15 text-carix-positive border border-carix-positive/30",
  warning: "bg-carix-warning/15 text-carix-warning border border-carix-warning/30",
  danger: "bg-carix-danger/15 text-carix-danger border border-carix-danger/30",
  info: "bg-carix-info/15 text-carix-info border border-carix-info/30",
  action: "bg-carix-action/15 text-carix-action border border-carix-action/30",
  muted: "bg-carix-border/40 text-carix-text/70 border border-carix-border",
};
```

P0 이 남긴 `NOTE: (cjkang)` 주석(다섯 번째 토큰이 없어 raw 팔레트를 쓴다는 내용, P1 이 P4 보다
먼저 와야 한다는 내용)은 **통째로 지운다.** 두 조건 다 해소됐다.

`decisionSignal.test.ts` 는 `tone` 값만 단언하고 클래스 문자열은 안 보므로 그대로 통과한다.

- [ ] **Step 7: 명세 문서에 실측치를 남긴다**

`docs/specs/ui-ux-design.md` §1.1 의 라이트/다크 두 목록에 각각 한 줄씩 넣는다. 형식은 옆줄과 같다.

```
    *   **행동 권장 (Action):** 바이올렛 (`#6d28d9`) - 흰색 배경 명도 대비(N.NN:1)를 만족. 결정 신호 `협상 검토` 에 쓴다.
```
`N.NN` 은 Step 1 의 실측치다. 다크 목록에도 같은 형식으로 다크값을 넣는다. 다른 줄은 건드리지 않는다.

- [ ] **Step 8: 베이스라인 재생성**

Run: `cd frontend && pnpm test:e2e primitives.spec.ts --update-snapshots`

`primitives-badges-light` 와 `primitives-badges-dark` **둘 다** 바뀌어야 한다 — `NEGOTIATE`
칩의 색이 양쪽에서 바뀌기 때문이다. 다른 스냅샷이 바뀌었으면 보고한다.

Step 6 의 해시 확인(14파일 / 14고유)을 다시 돌린다.

- [ ] **Step 9: raw 팔레트가 이 파일에서 사라졌는지 확인**

Run: `cd frontend && grep -n 'purple-' app/lib/decisionSignal.ts`
Expected: 출력 없음.

- [ ] **Step 10: 전체 검증**

Run: `cd frontend && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test:unit && pnpm build && pnpm test:e2e primitives.spec.ts`
Expected: 전부 통과.

- [ ] **Step 11: 커밋**

```bash
git add frontend/app/styles/tokens.css frontend/app/styles/tokens.test.ts \
        frontend/app/lib/decisionSignal.ts docs/specs/ui-ux-design.md \
        frontend/e2e/primitives.spec.ts-snapshots
git commit -m "feat(frontend): 다섯 번째 의미 색상 --carix-action 을 세우고 결정 신호에서 raw 팔레트를 걷는다"
```

---

## 완료 조건

- [ ] `.dark` 클래스 하위에서 `dark:` 유틸이 발화한다. 빌드 CSS 에서 `.dark` 선행 선택자를 갖는 규칙이 확인된다.
- [ ] `dark:` 를 쓰는 10파일의 변화를 눈으로 확인했고, 퇴행으로 보이는 것은 목록으로 보고됐다.
- [ ] `--carix-action` 이 라이트/다크 쌍으로 존재하고 `.light`·`.dark` 두 클래스 블록에도 들어있다.
- [ ] 네 조합 대비 실측치가 전부 4.5:1 이상이고 `ui-ux-design.md` §1.1 에 기록됐다.
- [ ] `decisionSignal.ts` 에 `purple-` 문자열이 없다. `[.dark_&]` 우회도 없다.
- [ ] 베이스라인 14장, 고유 해시 14개, 라이트/다크 쌍 전부 다름.
- [ ] 프런트 검증 5종 + `test:e2e primitives.spec.ts` 통과.

## 다음 단계에 남기는 것

- **P4 의 raw 팔레트 금지 eslint 규칙**은 이제 `decisionSignal.ts` 를 막지 않는다. 다만
  `JobHistoryTable`·`RecentPostingsTable`·`collector`·`expense` 의 누출은 그대로다 — P4 의
  규칙을 켜기 전에 그 네 파일을 먼저 정리해야 한다.
- **P3 의 시각 회귀 그물에 다크 경로가 비어 있다.** `primitives.spec.ts` 는 클래스를 토글하지만
  Playwright 가 `colorScheme: 'light'` 로 고정돼 있어 `@media (prefers-color-scheme: dark)`
  경로는 한 번도 안 탄다. Task 1 이후로는 클래스 토글만으로 `dark:` 가 잡히므로 그물이
  실질적으로 넓어지지만, OS 기본값 경로를 덮으려면 P3 에서 `emulateMedia` 를 병행해야 한다.
