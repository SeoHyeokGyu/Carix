# UI 일관성 통일 P2 — 공용 표현 계층 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 화면마다 손으로 쓴 뱃지·빈 상태·로딩 마크업을 표현 프리미티브 세 개와 도메인별 해석 모듈로 모으고, 그 과정에서 드러난 스킬 수요 상태 어휘의 계약 어긋남을 막는다.

**Architecture:** P0 이 결정 신호에서 세운 구조를 그대로 복제한다 — 어휘를 모르는 표현 프리미티브(`ToneBadge`) 하나, 도메인마다 코드→{문구, 톤} 표를 가진 해석 모듈, 미지 코드용 `muted` 폴백. 어휘는 합치지 않는다. `IndexStatus` 와 `SkillDemandStatus` 는 서로 다른 서버 enum 이고 합치면 없는 상태값을 서로에게 노출한다.

**Tech Stack:** React Router 7, Tailwind v4, Vitest + Testing Library, Playwright(시각 회귀)

**Spec:** `docs/plans/ui-unification-plan.md` (§P2)

## Global Constraints

- 의미 색상은 `carix-*` 토큰만 쓴다. 원시 팔레트(`purple-400`, `gray-500` 등) 금지.
- 뱃지 표현은 `ToneBadge` 한 곳에서만 정의한다. 호출부가 클래스 문자열을 다시 쓰지 않는다.
- 서버 어휘를 프런트에 문자열 리터럴로 복제하지 않는다. 해석 모듈의 표가 유일한 사본이다.
- 미지 코드는 던지지 않고 `muted` 톤 폴백으로 렌더한다 — 서버가 상태를 늘려도 빈 뱃지가 나가지 않게 한다.
- 커밋 메시지는 한글 Conventional Commits. `Co-Authored-By` 및 생성 푸터 금지.
- 각 태스크 끝에서 CI 5종이 통과해야 한다: `pnpm typecheck`, `pnpm lint`, `pnpm format:check`, `pnpm test:unit`, `pnpm build` (`frontend/` 에서). **`pnpm test` 는 서버 스모크 테스트라 CI 가 부르지 않는다 — vitest 는 `test:unit` 이다.**

## 파일 구조

| 파일 | 책임 |
| :--- | :--- |
| `app/components/ui/tone-badge.tsx` | 신규. `tone` + children 만 받는 표현 프리미티브 |
| `app/lib/indexStatus.ts` | 신규. `IndexStatus` → {문구, 톤} |
| `app/lib/skillDemandStatus.ts` | 신규. 스킬 수요 상태·추세 → {문구, 톤} |
| `app/components/dashboard/SignalBadge.tsx` | 수정. `ToneBadge` 위 얇은 래퍼로 내려앉음 |
| `app/components/dashboard/IndexCard.tsx` | 수정. `STATUS_BADGE_LABEL` 을 해석 모듈로 이관 |
| `app/components/dashboard/SkillDemandSection.tsx` | 수정. 손으로 쓴 뱃지 6종 제거 |
| `app/lib/dashboardMarketApi.ts` | 수정. `status: string` → 어휘 유니온 |
| `app/components/ui/empty-state.tsx` | 신규. 열화 박스 표준형 |
| `app/components/ui/loading-state.tsx` | 신규. 로딩 박스 표준형 |
| `app/routes/dev/primitives.tsx` | 수정. 신규 3종을 갤러리에 추가 |
| `app/e2e/primitives.spec.ts` → `e2e/primitives.spec.ts` | 수정. 신규 섹션 스냅샷 + computed-style 단언 |

---

### Task 1: ToneBadge 프리미티브와 지수 상태 해석 모듈

**Files:**
- Create: `frontend/app/components/ui/tone-badge.tsx`
- Create: `frontend/app/lib/indexStatus.ts`
- Create: `frontend/app/lib/indexStatus.test.ts`
- Modify: `frontend/app/lib/decisionSignal.ts`
- Modify: `frontend/app/components/dashboard/SignalBadge.tsx`
- Modify: `frontend/app/components/dashboard/IndexCard.tsx`
- Test: `frontend/app/components/dashboard/IndexCard.test.tsx` (기존 통과 유지)

**Interfaces:**
- Produces: `ToneBadge({ tone, className, children, ...rest })`; `type Tone`; `resolveIndexStatus(status: IndexStatus): { label: string; tone: Tone } | null`
- Consumes: `SignalTone` / `SIGNAL_TONE_CLASS` (기존 `decisionSignal.ts`)

`SignalTone` 은 `Tone` 으로 이름을 바꿔 `tone-badge.tsx` 로 옮긴다. 톤은 결정 신호만의 것이 아니게 되므로 이름에서 `Signal` 을 뗀다. `decisionSignal.ts` 는 그 타입을 다시 내보내 기존 import 를 깨지 않는다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`frontend/app/lib/indexStatus.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { resolveIndexStatus } from "./indexStatus";
import type { IndexStatus } from "./api/dashboard";

describe("resolveIndexStatus", () => {
  it("OK 는 뱃지를 만들지 않는다", () => {
    expect(resolveIndexStatus("OK")).toBeNull();
  });

  it("설계 §7.5 표의 문구를 그대로 돌려준다", () => {
    expect(resolveIndexStatus("INSUFFICIENT_SAMPLE")?.label).toBe("표본 부족");
    expect(resolveIndexStatus("UPDATE_DELAYED")?.label).toBe("업데이트 지연");
    expect(resolveIndexStatus("INSUFFICIENT_HISTORY")?.label).toBe("계산 대기");
    expect(resolveIndexStatus("NOT_AVAILABLE")?.label).toBe("계산 대기");
  });

  it("표본 부족과 업데이트 지연은 서로 다른 톤이다", () => {
    expect(resolveIndexStatus("INSUFFICIENT_SAMPLE")?.tone).toBe("warning");
    expect(resolveIndexStatus("UPDATE_DELAYED")?.tone).toBe("info");
  });

  // 서버 IndexContractVocabularyTest 에 대응하는 프런트 계약 테스트.
  // 어휘가 늘면 여기가 먼저 깨져서 해석 표를 같이 늘리게 만든다.
  it("IndexStatus 어휘 5종을 빠짐없이 덮는다", () => {
    const vocabulary: IndexStatus[] = [
      "OK",
      "INSUFFICIENT_SAMPLE",
      "UPDATE_DELAYED",
      "INSUFFICIENT_HISTORY",
      "NOT_AVAILABLE",
    ];
    for (const code of vocabulary) {
      expect(() => resolveIndexStatus(code)).not.toThrow();
    }
    expect(resolveIndexStatus("OK")).toBeNull();
    for (const code of vocabulary.filter((c) => c !== "OK")) {
      expect(resolveIndexStatus(code)).not.toBeNull();
    }
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd frontend && pnpm vitest run app/lib/indexStatus.test.ts`
Expected: FAIL — `Failed to resolve import "./indexStatus"`

- [ ] **Step 3: ToneBadge 를 만든다**

`frontend/app/components/ui/tone-badge.tsx`:

```tsx
import type { ComponentPropsWithoutRef } from "react";
import { cn } from "@/lib/utils";

/**
 * 의미 톤 — 어느 도메인의 어휘든 여기 여섯 중 하나로 떨어진다.
 * `muted` 는 프런트가 모르는 코드가 내려왔을 때의 폴백이다.
 */
export type Tone =
  | "positive"
  | "warning"
  | "danger"
  | "info"
  | "action"
  | "muted";

/** 톤 → 클래스. 뱃지 색 조합은 앱 전체에서 이 표 하나뿐이다. */
export const TONE_CLASS: Record<Tone, string> = {
  positive:
    "bg-carix-positive/15 text-carix-positive border border-carix-positive/30",
  warning:
    "bg-carix-warning/15 text-carix-warning border border-carix-warning/30",
  danger: "bg-carix-danger/15 text-carix-danger border border-carix-danger/30",
  info: "bg-carix-info/15 text-carix-info border border-carix-info/30",
  action: "bg-carix-action/15 text-carix-action border border-carix-action/30",
  muted: "bg-carix-border/40 text-carix-text/70 border border-carix-border",
};

export interface ToneBadgeProps extends ComponentPropsWithoutRef<"span"> {
  tone: Tone;
}

/**
 * 뱃지 표현 프리미티브. 어떤 어휘도 모른다 — 톤과 내용만 받는다.
 * 도메인별 문구·톤 결정은 `lib/decisionSignal.ts`, `lib/indexStatus.ts`,
 * `lib/skillDemandStatus.ts` 가 각자 맡는다.
 */
export function ToneBadge({
  tone,
  className,
  children,
  ...rest
}: ToneBadgeProps) {
  return (
    <span
      data-tone={tone}
      className={cn(
        // `w-fit shrink-0` 은 기존 `ui/badge.tsx` 가 들고 있던 것이다. 빼면 flex 컨테이너
        // (`flex flex-col`) 안에서 align-items 기본값 stretch 를 받아 뱃지가 가로로 늘어난다.
        "inline-flex w-fit shrink-0 items-center gap-1 rounded px-2 py-0.5 text-xs font-semibold",
        TONE_CLASS[tone],
        className,
      )}
      {...rest}
    >
      {children}
    </span>
  );
}
```

- [ ] **Step 4: 지수 상태 해석 모듈을 만든다**

`frontend/app/lib/indexStatus.ts`:

```ts
import type { Tone } from "@/components/ui/tone-badge";
import type { IndexStatus } from "./api/dashboard";

/**
 * 지수 열화 상태의 화면 표현 (설계 §7.5 표).
 *
 * `INSUFFICIENT_HISTORY` 와 `NOT_AVAILABLE` 은 사용자에게 같은 "계산 대기"로 묶인다 —
 * 원인은 다르지만 할 수 있는 일이 같아서다.
 *
 * 스킬 수요 상태(`lib/skillDemandStatus.ts`)와 합치지 않는다. 서버에서 다른 enum 이고
 * 합치면 한쪽에 없는 상태값이 다른 쪽 화면에 노출된다.
 */
const INDEX_STATUS: Record<
  Exclude<IndexStatus, "OK">,
  { label: string; tone: Tone }
> = {
  INSUFFICIENT_SAMPLE: { label: "표본 부족", tone: "warning" },
  UPDATE_DELAYED: { label: "업데이트 지연", tone: "info" },
  INSUFFICIENT_HISTORY: { label: "계산 대기", tone: "muted" },
  NOT_AVAILABLE: { label: "계산 대기", tone: "muted" },
};

/** `OK` 는 뱃지를 만들지 않는다 — 정상 상태에 라벨을 붙이지 않는다. */
export function resolveIndexStatus(
  status: IndexStatus,
): { label: string; tone: Tone } | null {
  if (status === "OK") return null;
  return INDEX_STATUS[status] ?? { label: status, tone: "muted" };
}
```

- [ ] **Step 5: 테스트 통과를 확인한다**

Run: `cd frontend && pnpm vitest run app/lib/indexStatus.test.ts`
Expected: PASS (4 tests)

- [ ] **Step 6: decisionSignal.ts 를 ToneBadge 의 톤 위로 옮긴다**

`decisionSignal.ts` 에서 `SignalTone` 선언과 `SIGNAL_TONE_CLASS` 표를 **지우고** 다음으로 바꾼다:

```ts
import type { Tone } from "@/components/ui/tone-badge";

/** 톤 이름은 `ToneBadge` 소관이다. 기존 import 를 깨지 않으려고 여기서 다시 내보낸다. */
export type SignalTone = Tone;
```

`SIGNAL_TONE_CLASS` 를 지워도 되는지는 확인했다 — 임포터는 `SignalBadge.tsx` 하나뿐이고 Step 7 이 그 파일을 통째로 다시 쓴다. 호환용 재내보내기는 두지 않는다. `SignalTone` 도 `decisionSignal.ts` 바깥 사용처가 없지만, 타입 별칭은 한 줄이라 남겨 둔다.

`SIGNALS` 표와 `resolveDecisionSignal` 본문은 그대로 둔다. 파일 상단 주석의 "다섯 톤 모두 의미 색상 토큰을 쓴다" 문장은 표와 함께 `tone-badge.tsx` 로 이미 옮겼으므로 지운다.

- [ ] **Step 7: SignalBadge 를 ToneBadge 래퍼로 내린다**

`frontend/app/components/dashboard/SignalBadge.tsx` 전체를 다음으로 바꾼다:

```tsx
import { resolveDecisionSignal } from "../../lib/decisionSignal";
import { ToneBadge } from "@/components/ui/tone-badge";

interface SignalBadgeProps {
  code: string;
  /** 프런트 테이블에 없는 코드일 때만 쓰는 서버 문구. */
  serverLabel?: string | null;
  className?: string;
}

/** 결정 신호 뱃지. 두 화면(`S-10`·`S-11`)이 같은 것을 쓴다. */
export function SignalBadge({
  code,
  serverLabel,
  className,
}: SignalBadgeProps) {
  const { label, tone } = resolveDecisionSignal(code, serverLabel);

  return (
    <ToneBadge tone={tone} data-testid="signal-badge" className={className}>
      {label}
    </ToneBadge>
  );
}
```

`data-tone` 은 `ToneBadge` 가 붙이므로 여기서 다시 붙이지 않는다. 기존 `SignalBadge.test.tsx` 가 `data-testid` 와 `data-tone` 을 모두 보고 있으면 둘 다 그대로 나온다.

- [ ] **Step 8: IndexCard 를 해석 모듈로 옮긴다**

`IndexCard.tsx` 에서 `STATUS_BADGE_LABEL` 상수 블록(주석 포함)을 지우고, import 에 다음을 더한다:

```tsx
import { resolveIndexStatus } from "../../lib/indexStatus";
import { ToneBadge } from "@/components/ui/tone-badge";
```

`const { value, changeFromLastMonth, indexStatus } = index;` 바로 아래에 더한다:

```tsx
  const status = resolveIndexStatus(indexStatus);
```

`indexStatus !== "OK"` 블록을 다음으로 바꾼다 — 두 줄 위의 `changeFromLastMonth !== null &&` 와 같은 모양으로 맞춘다:

```tsx
        {status && <ToneBadge tone={status.tone}>{status.label}</ToneBadge>}
```

`Badge` 는 `changeFromLastMonth` 쪽에서 계속 쓰므로 import 를 지우지 않는다.

- [ ] **Step 9: 전체 테스트와 CI 5종을 돌린다**

```bash
cd frontend
pnpm lint && pnpm typecheck && pnpm test:unit && pnpm format:check && pnpm build
```
Expected: 전부 PASS. `IndexCard.test.tsx` 가 `Badge` 의 `variant="outline"` 클래스를 문자열로 단언하고 있으면 톤 클래스로 고친다 — 문구 단언은 그대로 통과해야 한다.

- [ ] **Step 10: 커밋**

```bash
git add frontend/app/components/ui/tone-badge.tsx frontend/app/lib/indexStatus.ts frontend/app/lib/indexStatus.test.ts frontend/app/lib/decisionSignal.ts frontend/app/components/dashboard/SignalBadge.tsx frontend/app/components/dashboard/IndexCard.tsx frontend/app/components/dashboard/IndexCard.test.tsx
git commit -m "refactor: 뱃지 표현 계층을 ToneBadge 로 모으고 지수 상태 해석을 분리한다"
```

---

### Task 2: 스킬 수요 상태 해석 모듈과 계약 어긋남 차단

**Files:**
- Create: `frontend/app/lib/skillDemandStatus.ts`
- Create: `frontend/app/lib/skillDemandStatus.test.ts`
- Modify: `frontend/app/lib/dashboardMarketApi.ts`
- Modify: `frontend/app/components/dashboard/SkillDemandSection.tsx`
- Test: `frontend/app/components/dashboard/SkillDemandSection.test.tsx` (기존 통과 유지)

**Interfaces:**
- Consumes: `ToneBadge`, `Tone` (Task 1)
- Produces: `type SkillDemandStatusCode`; `resolveSkillDemandStatus(status): { label: string; tone: Tone } | null`; `resolveDemandTrend(demandIndex): { label: string; tone: Tone }`

**이 태스크가 고치는 결함.** `SkillDemandDetail.status` 가 맨 `string` 이라 프런트 분기가 서버 어휘와 말없이 어긋나 있었다. `SAMPLE_TOO_SMALL` 은 백엔드 어디에도 없는 죽은 분기다. 타입을 붙이고 계약 테스트를 세워 다음 어긋남을 컴파일 타임에 잡는다.

**범위 밖.** 서버 `MarketValueIndexServiceImpl#buildSkillDemandDetails` 가 `INSUFFICIENT_SAMPLE`·`NO_RECENT_DEMAND` 를 집계에서 버리는 것은 이 태스크에서 고치지 않는다(스펙 §P2 참조). 해석 표는 그 어휘까지 미리 덮어 두어, 서버가 고쳐지면 프런트 변경 없이 맞아 들어가게 한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`frontend/app/lib/skillDemandStatus.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import {
  resolveDemandTrend,
  resolveSkillDemandStatus,
  SKILL_DEMAND_STATUS_CODES,
} from "./skillDemandStatus";

describe("resolveSkillDemandStatus", () => {
  it("OK 는 상태 뱃지를 만들지 않는다 — 추세 뱃지가 그 자리를 쓴다", () => {
    expect(resolveSkillDemandStatus("OK")).toBeNull();
  });

  it("열화 상태마다 문구와 톤을 준다", () => {
    expect(resolveSkillDemandStatus("CUSTOM_SKILL_NO_STATS")).toEqual({
      label: "통계 미제공",
      tone: "warning",
    });
    expect(resolveSkillDemandStatus("NEW_OBSERVATION")).toEqual({
      label: "신규 관측",
      tone: "info",
    });
    expect(resolveSkillDemandStatus("INSUFFICIENT_SAMPLE")).toEqual({
      label: "표본 부족",
      tone: "warning",
    });
    expect(resolveSkillDemandStatus("NO_RECENT_DEMAND")).toEqual({
      label: "최근 수요 없음",
      tone: "muted",
    });
  });

  // 서버 IndexContractVocabularyTest 의 프런트 대응물.
  // SkillDemandStatus enum 4종 + CUSTOM_SKILL_NO_STATS 리터럴 = 5종을 덮어야 한다.
  it("서버 어휘 5종을 빠짐없이 덮는다", () => {
    expect([...SKILL_DEMAND_STATUS_CODES].sort()).toEqual(
      [
        "OK",
        "NEW_OBSERVATION",
        "INSUFFICIENT_SAMPLE",
        "NO_RECENT_DEMAND",
        "CUSTOM_SKILL_NO_STATS",
      ].sort(),
    );
  });

  it("모르는 코드는 던지지 않고 muted 로 받는다", () => {
    expect(resolveSkillDemandStatus("SAMPLE_TOO_SMALL")).toEqual({
      label: "SAMPLE_TOO_SMALL",
      tone: "muted",
    });
  });
});

describe("resolveDemandTrend", () => {
  it("105 이상은 상승, 95 이하는 하락, 사이는 유지다", () => {
    expect(resolveDemandTrend(105)).toEqual({ label: "▲ 상승", tone: "positive" });
    expect(resolveDemandTrend(95)).toEqual({ label: "▼ 하락", tone: "warning" });
    expect(resolveDemandTrend(100)).toEqual({ label: "- 유지", tone: "info" });
  });

  it("경계값은 상승·하락 쪽에 붙는다", () => {
    expect(resolveDemandTrend(104.9).tone).toBe("info");
    expect(resolveDemandTrend(95.1).tone).toBe("info");
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd frontend && pnpm vitest run app/lib/skillDemandStatus.test.ts`
Expected: FAIL — `Failed to resolve import "./skillDemandStatus"`

- [ ] **Step 3: 해석 모듈을 만든다**

`frontend/app/lib/skillDemandStatus.ts`:

```ts
import type { Tone } from "@/components/ui/tone-badge";

/**
 * 스킬 수요 상태의 화면 표현.
 *
 * 서버 어휘는 두 출처가 섞여 있다 — `com.carix.index.enums.SkillDemandStatus` 4종과,
 * 커스텀 스킬을 표시하려고 `MarketValueIndexServiceImpl` 이 직접 넣는 문자열
 * `CUSTOM_SKILL_NO_STATS` 하나다. 서버 DTO 의 필드 타입이 `String` 이라 enum 으로 좁힐 수
 * 없어, 프런트에서 유니온으로 못박고 계약 테스트로 지킨다.
 *
 * 지수 상태(`lib/indexStatus.ts`)와 합치지 않는다. 서버에서 다른 enum 이다.
 *
 * NOTE: (cjkang) `INSUFFICIENT_SAMPLE` 과 `NO_RECENT_DEMAND` 는 서버 집계가 현재
 * 버리고 있어 실제로는 내려오지 않는다. 표에 미리 넣어 둔 것은 서버가 고쳐질 때
 * 프런트를 다시 건드리지 않기 위해서다 (스펙 `ui-unification-plan.md` §P2).
 */
export const SKILL_DEMAND_STATUS_CODES = [
  "OK",
  "NEW_OBSERVATION",
  "INSUFFICIENT_SAMPLE",
  "NO_RECENT_DEMAND",
  "CUSTOM_SKILL_NO_STATS",
] as const;

export type SkillDemandStatusCode = (typeof SKILL_DEMAND_STATUS_CODES)[number];

const STATUS: Record<
  Exclude<SkillDemandStatusCode, "OK">,
  { label: string; tone: Tone }
> = {
  CUSTOM_SKILL_NO_STATS: { label: "통계 미제공", tone: "warning" },
  NEW_OBSERVATION: { label: "신규 관측", tone: "info" },
  // `indexStatus.ts` 의 같은 문구와 톤을 맞춘다 — 같은 라벨이면 같은 색이다.
  INSUFFICIENT_SAMPLE: { label: "표본 부족", tone: "warning" },
  NO_RECENT_DEMAND: { label: "최근 수요 없음", tone: "muted" },
};

/** `OK` 만 `null` 이다 — 그 자리에는 수요 추세 뱃지가 들어간다. */
export function resolveSkillDemandStatus(
  status: string,
): { label: string; tone: Tone } | null {
  if (status === "OK") return null;
  const known = STATUS[status as Exclude<SkillDemandStatusCode, "OK">];
  return known ?? { label: status, tone: "muted" };
}

/** 기준선 100 대비 수요 지수의 추세. 임계값은 기능 명세의 ±5p 구간이다. */
const TREND_UP_THRESHOLD = 105;
const TREND_DOWN_THRESHOLD = 95;

export function resolveDemandTrend(demandIndex: number): {
  label: string;
  tone: Tone;
} {
  if (demandIndex >= TREND_UP_THRESHOLD)
    return { label: "▲ 상승", tone: "positive" };
  if (demandIndex <= TREND_DOWN_THRESHOLD)
    return { label: "▼ 하락", tone: "warning" };
  return { label: "- 유지", tone: "info" };
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `cd frontend && pnpm vitest run app/lib/skillDemandStatus.test.ts`
Expected: PASS (6 tests)

- [ ] **Step 5: 와이어 타입을 좁힌다**

`frontend/app/lib/dashboardMarketApi.ts` 의 `SkillDemandDetail` 에서 `status: string;` 을 다음으로 바꾸고, 파일 상단 import 에 타입을 더한다:

```ts
import type { SkillDemandStatusCode } from "./skillDemandStatus";
```

```ts
  /** 서버 어휘는 `skillDemandStatus.ts` 가 못박는다. 여기 문자열을 복제하지 않는다. */
  status: SkillDemandStatusCode;
```

- [ ] **Step 6: SkillDemandSection 의 뱃지 6종을 지운다**

`getTrendBadge` 함수 전체(`const getTrendBadge = (detail: SkillDemandDetail) => { ... };`)를 다음으로 바꾼다:

```tsx
  const getTrendBadge = (detail: SkillDemandDetail) => {
    const status = resolveSkillDemandStatus(detail.status);
    if (status) {
      return <ToneBadge tone={status.tone}>{status.label}</ToneBadge>;
    }

    const trend = resolveDemandTrend(detail.demandIndex);
    return (
      <ToneBadge tone={trend.tone}>
        <span>{trend.label}</span>
        <span>({detail.demandIndex.toFixed(1)})</span>
      </ToneBadge>
    );
  };
```

파일 상단 import 에 더한다:

```tsx
import {
  resolveDemandTrend,
  resolveSkillDemandStatus,
} from "../../lib/skillDemandStatus";
import { ToneBadge } from "@/components/ui/tone-badge";
```

- [ ] **Step 7: 테스트와 CI 5종을 돌린다**

```bash
cd frontend
pnpm lint && pnpm typecheck && pnpm test:unit && pnpm format:check && pnpm build
```

Expected: 전부 PASS 하기 전에 **하나가 먼저 깨진다.**

`SkillDemandSection.test.tsx:75` 가 `status: "SAMPLE_TOO_SMALL"` 을 넣고 "표본 부족" 을 기대한다. 이제 그 값은 타입에 없으므로 `pnpm typecheck` 가 먼저 잡는다. **테스트가 틀린 것이다** — 서버가 그 값을 보낸 적이 없고, 이 테스트가 죽은 분기를 살아 있는 것처럼 보이게 해 왔다. `INSUFFICIENT_SAMPLE` 로 고친다. 기대 문구 "표본 부족" 은 그대로 맞다.

추세 뱃지 문구(`▲ 상승`·`▼ 하락`·`- 유지`)는 현행 문자열 그대로다 — 이 태스크는 문구를 바꾸지 않는다.

- [ ] **Step 8: 커밋**

```bash
git add frontend/app/lib/skillDemandStatus.ts frontend/app/lib/skillDemandStatus.test.ts frontend/app/lib/dashboardMarketApi.ts frontend/app/components/dashboard/SkillDemandSection.tsx frontend/app/components/dashboard/SkillDemandSection.test.tsx
git commit -m "fix: 스킬 수요 상태 어휘를 타입으로 못박고 죽은 분기를 걷어낸다"
```

---

### Task 3: EmptyState 표준형

**Files:**
- Create: `frontend/app/components/ui/empty-state.tsx`
- Create: `frontend/app/components/ui/empty-state.test.tsx`
- Modify: `frontend/app/routes/dashboard/history.tsx`
- Modify: `frontend/app/components/dashboard/SkillDemandSection.tsx`

**Interfaces:**
- Consumes: `ToneBadge`, `Tone` (Task 1)
- Produces: `EmptyState({ icon, tone, title, description, children })`

**이행 범위.** 대시보드 축의 빈 상태 2곳만 옮긴다 — `history.tsx`(박스형)와 `SkillDemandSection`(맨몸형). 이 둘이 **같은 사람이 쓴 서로 다른 두 형태**라서 표준형이 필요하다는 근거 자체다. `SalaryDistributionChart` 의 3곳은 본문에 표본 수·완화 조건 같은 도메인 서술이 엮여 있어 P3 의 프리미티브 재정의와 같이 간다. 온보딩·admin 의 나머지는 P4 범위다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`frontend/app/components/ui/empty-state.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EmptyState } from "./empty-state";

describe("EmptyState", () => {
  it("제목을 표제로 올린다", () => {
    render(<EmptyState title="기록된 월별 스냅샷이 없습니다." />);
    expect(
      screen.getByRole("heading", { name: "기록된 월별 스냅샷이 없습니다." }),
    ).toBeInTheDocument();
  });

  it("설명과 아이콘은 있을 때만 그린다", () => {
    const { rerender } = render(<EmptyState title="비었음" />);
    expect(screen.queryByTestId("empty-state-icon")).not.toBeInTheDocument();

    rerender(<EmptyState title="비었음" icon="📋" description="곧 채워집니다" />);
    expect(screen.getByTestId("empty-state-icon")).toHaveTextContent("📋");
    expect(screen.getByText("곧 채워집니다")).toBeInTheDocument();
  });

  it("children 을 행동 슬롯으로 받는다", () => {
    render(
      <EmptyState title="비었음">
        <button type="button">시작하기</button>
      </EmptyState>,
    );
    expect(
      screen.getByRole("button", { name: "시작하기" }),
    ).toBeInTheDocument();
  });

  it("tone 을 주면 아이콘이 그 톤의 알약이 된다", () => {
    render(<EmptyState title="비었음" icon="⚠️" tone="warning" />);
    expect(screen.getByTestId("empty-state-icon")).toHaveAttribute(
      "data-tone",
      "warning",
    );
  });

  it("plain 은 테두리와 배경을 벗는다 — 이미 카드 안일 때 쓴다", () => {
    const { rerender } = render(<EmptyState title="비었음" />);
    const boxed = screen.getByTestId("empty-state").className;
    expect(boxed).toContain("border-dashed");
    expect(boxed).toContain("bg-carix-surface");

    rerender(<EmptyState title="비었음" variant="plain" />);
    const plain = screen.getByTestId("empty-state").className;
    expect(plain).not.toContain("border-dashed");
    expect(plain).not.toContain("bg-carix-surface");
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd frontend && pnpm vitest run app/components/ui/empty-state.test.tsx`
Expected: FAIL — `Failed to resolve import "./empty-state"`

- [ ] **Step 3: 컴포넌트를 만든다**

`frontend/app/components/ui/empty-state.tsx`:

```tsx
import type { ComponentPropsWithoutRef, ReactNode } from "react";
import { cn } from "@/lib/utils";
import { ToneBadge, type Tone } from "./tone-badge";

export interface EmptyStateProps extends ComponentPropsWithoutRef<"div"> {
  /**
   * `boxed`(기본) — 점선 박스. 화면에 바로 놓일 때.
   * `plain` — 테두리·배경 없음. 이미 카드 안에 놓일 때. 박스 안의 박스를 만들지 않는다.
   */
  variant?: "boxed" | "plain";
  /** 이모지 한 글자. 없으면 아이콘 자리를 아예 비운다. */
  icon?: ReactNode;
  /** 주면 아이콘이 그 톤의 알약이 된다. 없으면 중립 원형이다. */
  tone?: Tone;
  title: string;
  description?: ReactNode;
  /** 링크·버튼 같은 다음 행동. */
  children?: ReactNode;
  className?: string;
}

/**
 * 빈 상태·열화 상태의 표준형.
 *
 * 전에는 같은 뜻을 두 가지로 그렸다 — 점선 박스에 아이콘과 표제를 세운 형태와,
 * 아무 테두리 없이 흐린 문단만 얹은 형태. 후자는 화면에서 "로딩이 덜 끝났나" 로 읽혔다.
 * 박스형으로 모은다.
 *
 * `variant` 는 그 두 형태의 부활이 아니다. 구조(아이콘·표제·설명·행동 슬롯)는 한 벌이고,
 * 바깥 테두리만 놓이는 자리에 따라 달라진다 — 이미 카드 안이면 박스를 한 겹 더 그리지 않는다.
 */
const VARIANT_CLASS = {
  boxed:
    "rounded-xl border border-dashed border-carix-border bg-carix-surface p-12 shadow-xs",
  plain: "py-12",
} as const;

export function EmptyState({
  variant = "boxed",
  icon,
  tone,
  title,
  description,
  children,
  className,
  ...rest
}: EmptyStateProps) {
  return (
    <div
      // 기본 식별자를 먼저 두고 `...rest` 를 뒤에 편다 — 호출부가 덮어쓸 수 있게.
      // 갤러리처럼 한 화면에 여러 개가 놓이면 Playwright strict 모드가 중복으로 던진다.
      data-testid="empty-state"
      {...rest}
      className={cn("text-center", VARIANT_CLASS[variant], className)}
    >
      {icon &&
        (tone ? (
          <ToneBadge
            tone={tone}
            data-testid="empty-state-icon"
            className="mb-3 rounded-full px-3 py-1"
          >
            {icon}
          </ToneBadge>
        ) : (
          <div
            data-testid="empty-state-icon"
            className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-carix-info/10 text-xl"
          >
            {icon}
          </div>
        ))}
      <h4 className="text-base font-bold text-carix-text">{title}</h4>
      {description && (
        <p className="mt-2 text-sm text-carix-text/70">{description}</p>
      )}
      {children && <div className="mt-6">{children}</div>}
    </div>
  );
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `cd frontend && pnpm vitest run app/components/ui/empty-state.test.tsx`
Expected: PASS (5 tests)

- [ ] **Step 5: history.tsx 를 옮긴다**

`snapshots.length === 0` 분기의 `<div data-testid="history-empty-state" ...>` 블록 전체를 다음으로 바꾼다. 안쪽 `<Link>` 와 그 문구는 원래 있던 것을 그대로 옮긴다:

```tsx
        <EmptyState
          icon="📋"
          title="기록된 월별 스냅샷이 없습니다."
          description="다음 달부터 변화를 볼 수 있습니다."
        >
          <Link
            to="/onboarding/profile"
            className="inline-flex items-center rounded-lg bg-carix-info px-4 py-2 text-xs font-semibold text-white shadow hover:opacity-90"
          >
            온보딩 정보 확인하기
          </Link>
        </EmptyState>
```

바깥 `<div className="mt-6">` 는 옮기지 않는다 — `EmptyState` 가 `children` 을 `mt-6` 슬롯에 이미 감싼다.

`data-testid="history-empty-state"` 는 떨어져 나간다. 이 식별자를 보는 테스트는 현재 없다(확인함) — `EmptyState` 가 붙이는 `data-testid="empty-state"` 로 충분하다. 화면마다 식별자를 따로 두면 표준형을 쓰는 의미가 없다.

import 를 더한다: `import { EmptyState } from "@/components/ui/empty-state";`

- [ ] **Step 6: SkillDemandSection 을 옮긴다**

`skills.length === 0 ?` 의 `<div className="py-12 text-center ...">` 블록 전체를 다음으로 바꾼다:

```tsx
        <EmptyState
          variant="plain"
          title="등록된 보유 스킬이 없습니다."
          description="온보딩 또는 마이페이지에서 스킬을 등록하면 채용 시장 수요 지수가 표시됩니다."
        />
```

**`variant="plain"` 인 이유.** 이 자리는 이미 `rounded-xl border border-carix-border bg-carix-surface p-6` 카드 안이다. 기본 `boxed` 를 쓰면 같은 배경 위에 점선 테두리가 한 겹 더 생기고 `p-6` 안에 `p-12` 가 들어간다. 갤러리에 `SkillDemandSection` 이 없어서 Task 5 의 스냅샷도 이걸 못 잡는다 — 그래서 여기서 정한다.

import 를 더한다: `import { EmptyState } from "@/components/ui/empty-state";`

- [ ] **Step 7: 테스트와 CI 5종을 돌린다**

```bash
cd frontend
pnpm lint && pnpm typecheck && pnpm test:unit && pnpm format:check && pnpm build
```
Expected: 전부 PASS.

- [ ] **Step 8: 커밋**

```bash
git add frontend/app/components/ui/empty-state.tsx frontend/app/components/ui/empty-state.test.tsx frontend/app/routes/dashboard/history.tsx frontend/app/components/dashboard/SkillDemandSection.tsx
git commit -m "refactor: 빈 상태 두 형태를 EmptyState 표준형으로 모은다"
```

---

### Task 4: LoadingState 표준형

**Files:**
- Create: `frontend/app/components/ui/loading-state.tsx`
- Create: `frontend/app/components/ui/loading-state.test.tsx`
- Modify: `frontend/app/routes/dashboard/history.tsx`

**Interfaces:**
- Produces: `LoadingState({ message, className })`

**이행 범위.** `history.tsx` 의 로딩 박스 1곳만 옮긴다. admin 3곳은 스켈레톤 형태가 달라 P4 에서 같이 본다 — 텍스트 박스와 스켈레톤을 한 컴포넌트로 억지로 묶지 않는다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`frontend/app/components/ui/loading-state.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LoadingState } from "./loading-state";

describe("LoadingState", () => {
  it("메시지를 보여준다", () => {
    render(<LoadingState message="스냅샷 이력을 조회하고 있습니다..." />);
    expect(
      screen.getByText("스냅샷 이력을 조회하고 있습니다..."),
    ).toBeInTheDocument();
  });

  it("보조 기술에 로딩 중임을 알린다", () => {
    render(<LoadingState message="조회 중" />);
    const region = screen.getByRole("status");
    expect(region).toHaveAttribute("aria-busy", "true");
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd frontend && pnpm vitest run app/components/ui/loading-state.test.tsx`
Expected: FAIL — `Failed to resolve import "./loading-state"`

- [ ] **Step 3: 컴포넌트를 만든다**

`frontend/app/components/ui/loading-state.tsx`:

```tsx
import { cn } from "@/lib/utils";

export interface LoadingStateProps {
  message: string;
  className?: string;
}

/**
 * 로딩 박스의 표준형.
 *
 * `EmptyState` 와 테두리를 나눈다 — 빈 상태는 점선, 로딩은 실선이다. 전에는 둘 다
 * 흐린 텍스트 박스라 "비었다" 와 "아직 안 왔다" 가 화면에서 구분되지 않았다.
 */
export function LoadingState({ message, className }: LoadingStateProps) {
  return (
    <div
      role="status"
      aria-busy="true"
      className={cn(
        "rounded-xl border border-carix-border bg-carix-surface p-12 text-center text-sm text-carix-text/60 shadow-xs",
        className,
      )}
    >
      {message}
    </div>
  );
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `cd frontend && pnpm vitest run app/components/ui/loading-state.test.tsx`
Expected: PASS (2 tests)

- [ ] **Step 5: history.tsx 를 옮긴다**

로딩 분기의 다음 블록을

```tsx
        <div className="rounded-xl border border-carix-border bg-carix-surface p-12 text-center text-sm text-carix-text/60 shadow-xs">
          스냅샷 이력을 조회하고 있습니다...
        </div>
```

다음으로 바꾼다:

```tsx
        <LoadingState message="스냅샷 이력을 조회하고 있습니다..." />
```

import 를 더한다: `import { LoadingState } from "@/components/ui/loading-state";`

- [ ] **Step 6: 테스트와 CI 5종을 돌린다**

```bash
cd frontend
pnpm lint && pnpm typecheck && pnpm test:unit && pnpm format:check && pnpm build
```
Expected: 전부 PASS.

- [ ] **Step 7: 커밋**

```bash
git add frontend/app/components/ui/loading-state.tsx frontend/app/components/ui/loading-state.test.tsx frontend/app/routes/dashboard/history.tsx
git commit -m "refactor: 로딩 박스를 LoadingState 표준형으로 모은다"
```

---

### Task 5: 갤러리 확장과 계산된 스타일 단언

**Files:**
- Modify: `frontend/app/routes/dev/primitives.tsx`
- Modify: `frontend/e2e/primitives.spec.ts`

**Interfaces:**
- Consumes: `ToneBadge`, `EmptyState`, `LoadingState` (Task 1·3·4)

**왜 이 태스크가 P2 에 붙는가.** P0 의 시각 회귀 그물은 가까운 색끼리의 교체를 못 잡는다 — 검증된 사실이다(신호 색 하나를 바꿨는데 스냅샷 차이가 0 이었다). P2 가 만든 세 컴포넌트를 그물 안에 넣는 동시에, 토큰 값 자체를 직접 읽는 단언을 1급 테스트로 세운다. 스펙이 P3 선행 조건으로 지목한 항목이고, 대상이 곧 P2 의 산출물이라 여기서 같이 끝낸다.

- [ ] **Step 1: 갤러리에 세 컴포넌트를 더한다**

`frontend/app/routes/dev/primitives.tsx` 의 Badge 섹션 다음에 아래 세 섹션을 더한다. 기존 섹션의 제목·래퍼 관례를 그대로 따른다 (갤러리는 한 번만 렌더한다 — `.light`/`.dark` 래퍼를 두지 않는다. 테마는 스펙이 `documentElement` 클래스로 토글한다).

```tsx
import { ToneBadge, TONE_CLASS } from "@/components/ui/tone-badge";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingState } from "@/components/ui/loading-state";

const TONES = Object.keys(TONE_CLASS) as (keyof typeof TONE_CLASS)[];
```

```tsx
      <section data-testid="gallery-tone-badge">
        <h2 className="font-display text-xl font-bold text-carix-text">
          ToneBadge
        </h2>
        <div className="mt-4 flex flex-wrap gap-2">
          {TONES.map((tone) => (
            <ToneBadge key={tone} tone={tone}>
              {tone}
            </ToneBadge>
          ))}
        </div>
      </section>

      <section data-testid="gallery-empty-state">
        <h2 className="font-display text-xl font-bold text-carix-text">
          EmptyState
        </h2>
        <div className="mt-4 flex flex-col gap-4">
          <EmptyState title="제목만" />
          <EmptyState
            icon="📋"
            title="아이콘과 설명"
            description="중립 원형 아이콘입니다."
          />
          <EmptyState
            icon="⚠️"
            tone="warning"
            title="톤 알약 아이콘"
            description="tone 을 주면 알약이 됩니다."
          >
            <button
              type="button"
              className="text-sm text-carix-info underline"
            >
              행동 슬롯
            </button>
          </EmptyState>
          <div className="rounded-xl border border-carix-border bg-carix-surface p-6">
            <EmptyState
              variant="plain"
              title="plain — 카드 안에 놓인 경우"
              description="테두리와 배경을 벗어 박스 안의 박스를 막습니다."
            />
          </div>
        </div>
      </section>

      <section data-testid="gallery-loading-state">
        <h2 className="font-display text-xl font-bold text-carix-text">
          LoadingState
        </h2>
        <div className="mt-4">
          <LoadingState message="불러오는 중입니다..." />
        </div>
      </section>
```

- [ ] **Step 2: 갤러리가 뜨는지 확인한다**

```bash
cd frontend && pnpm typecheck && pnpm build
```
Expected: PASS.

- [ ] **Step 3: 신규 섹션의 스냅샷 테스트를 더한다**

`frontend/e2e/primitives.spec.ts` 에, 기존 테스트와 같은 `setTheme(page, theme)` 헬퍼 사용 관례를 따라 세 섹션 × 두 테마를 더한다:

```ts
for (const theme of ["light", "dark"] as const) {
  for (const section of [
    "gallery-tone-badge",
    "gallery-empty-state",
    "gallery-loading-state",
  ]) {
    test(`${section} — ${theme}`, async ({ page }) => {
      await page.goto("/dev/primitives");
      await setTheme(page, theme);
      await expect(
        page.getByTestId(section),
      ).toHaveScreenshot(`${section}-${theme}.png`);
    });
  }
}
```

- [ ] **Step 4: 계산된 스타일 단언을 더한다**

같은 파일 끝에 더한다. 이것이 스냅샷이 못 잡는 것을 잡는다 — 두 톤이 서로 다른 색을 실제로 쓰는지, 그리고 테마 전환이 토큰을 실제로 바꾸는지:

```ts
test("톤마다 실제로 다른 색을 쓴다 — 스냅샷이 못 잡는 근접색 교체 방어", async ({
  page,
}) => {
  await page.goto("/dev/primitives");
  await setTheme(page, "light");

  const colors = await page
    .getByTestId("gallery-tone-badge")
    .locator("[data-tone]")
    .evaluateAll((nodes) =>
      nodes.map((n) => ({
        tone: n.getAttribute("data-tone"),
        color: getComputedStyle(n).color,
      })),
    );

  expect(colors).toHaveLength(6);
  const distinct = new Set(colors.map((c) => c.color));
  expect(distinct.size).toBe(6);
});

test("테마를 바꾸면 토큰 값이 실제로 바뀐다", async ({ page }) => {
  await page.goto("/dev/primitives");

  const read = () =>
    page
      .getByTestId("gallery-tone-badge")
      .locator('[data-tone="action"]')
      .evaluate((n) => getComputedStyle(n).color);

  await setTheme(page, "light");
  const light = await read();
  await setTheme(page, "dark");
  const dark = await read();

  expect(light).not.toBe(dark);
});
```

- [ ] **Step 5: 베이스라인을 만든다**

```bash
cd frontend && pnpm test:e2e --update-snapshots
```

새 `.png` 6장이 `-chromium-darwin` 접미사로 생긴다. 기존 14장은 값이 허용 오차 안이면 바이트 그대로 남는다 — 변경되지 않은 것이 정상이다.

- [ ] **Step 6: 그물이 실제로 잡는지 확인한다**

`tone-badge.tsx` 의 `TONE_CLASS.info` 를 잠시 `TONE_CLASS.positive` 와 같은 값으로 바꾸고:

```bash
cd frontend && pnpm test:e2e
```
Expected: "톤마다 실제로 다른 색을 쓴다" 가 `expect(distinct.size).toBe(6)` 에서 FAIL (5 를 받는다). **원래 값으로 되돌린다.**

- [ ] **Step 7: 전체 검증**

```bash
cd frontend
pnpm lint && pnpm typecheck && pnpm test:unit && pnpm format:check && pnpm build && pnpm test:e2e
```
Expected: 전부 PASS.

- [ ] **Step 8: 커밋**

```bash
git add frontend/app/routes/dev/primitives.tsx frontend/e2e/primitives.spec.ts frontend/e2e/primitives.spec.ts-snapshots
git commit -m "test: 공용 표현 계층을 갤러리에 넣고 계산된 색 단언을 세운다"
```

스냅샷 디렉터리 이름이 다르면(`__snapshots__` 등) 실제 경로를 확인해 `git add` 한다: `git status --short frontend/e2e`

---

## 완료 후

- 스펙 `docs/plans/ui-unification-plan.md` 의 P2 절에 완료 표시를 한다 (P0·P1 과 같은 방식).
- 서버 결함(`buildSkillDemandDetails` 가 `INSUFFICIENT_SAMPLE`·`NO_RECENT_DEMAND` 를 버리는 것)을 별건 이슈로 올린다. UI 통일 축에서는 고치지 않는다.
