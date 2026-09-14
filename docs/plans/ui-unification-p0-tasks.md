# UI 일관성 통일 P0 실행 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 결정 신호의 라벨·색 출처를 하나로 모으고, P3 프리미티브 재정의를 받아낼 시각 회귀 베이스라인을 세운다.

**Architecture:** 한글 문구는 서버 `DecisionSignalCode` 열거형 한 곳에서 나오고(`SalaryGapBucket.label()` 선례와 같은 형태), 화면 표현(라벨·색·설명)은 프런트 `decisionSignal.ts` 한 곳에서 나온다. 프런트에 매핑이 없는 코드가 오면 서버 `label` 로 떨어진다. 시각 회귀는 인증이 필요한 화면 대신 세션 가드 없는 프리미티브 갤러리 라우트에 건다.

**Tech Stack:** Spring Boot (core-api, JUnit5 + AssertJ + Mockito), React Router 7 + TypeScript + Tailwind v4 (frontend, Vitest + Testing Library), Playwright.

**Spec:** `docs/plans/ui-unification-plan.md`

## Global Constraints

- 캐노니컬 결정 신호 문구는 `협상 검토`·`이직 탐색`·`현 상태 유지`·`스킬 보강 우선`·`데이터 부족` 이다 (`docs/specs/mvp-requirements.md` §193-196, `docs/specs/data-architecture.md` §132-133). 서버의 현재 `"스킬 보강"` 이 틀렸다.
- 빈 값을 `0` 이나 `100` 으로 채우지 않는다. 값이 없으면 렌더하지 않는다.
- 토큰 밖 Tailwind 팔레트 클래스(`bg-purple-500` 등)를 새로 쓰지 않는다. **단 하나의 예외**는 Task 2 의 `action` 톤이다 — 다섯 번째 의미 토큰은 P1 에서 생기므로 그때까지만 임시로 둔다. 주석으로 그 사실을 남긴다.
- 커밋 메시지는 한글 Conventional Commits. `Co-Authored-By` 트레일러와 세션 링크는 넣지 않는다.
- 프런트 검증 5종: `pnpm lint`, `pnpm typecheck`, `pnpm format:check`, `pnpm test:unit`, `pnpm build`.
- 백엔드 검증: `./gradlew :core-api:check` (Spotless 포함).

---

## File Structure

**생성**

| 파일 | 책임 |
| :--- | :--- |
| `backend/core-api/src/test/java/com/carix/snapshot/DecisionSignalCodeTest.java` | 열거형 라벨이 캐노니컬 문구와 같은지 |
| `frontend/app/lib/decisionSignal.ts` | `code → { label, tone, description }` 단일 표현 테이블 + 톤→클래스 매핑 |
| `frontend/app/lib/decisionSignal.test.ts` | 위 테이블의 매핑·폴백 |
| `frontend/app/components/dashboard/SignalBadge.tsx` | 표현 테이블을 소비하는 뱃지 한 개 |
| `frontend/app/components/dashboard/SignalBadge.test.tsx` | 뱃지 렌더 |
| `frontend/app/routes/dev/primitives.tsx` | 프리미티브 갤러리 (세션 가드 없음) |
| `frontend/e2e/primitives.spec.ts` | 갤러리 `toHaveScreenshot` 베이스라인 |

**수정**

| 파일 | 변경 |
| :--- | :--- |
| `backend/core-api/src/main/java/com/carix/snapshot/DecisionSignalCode.java` | `label()` 추가 |
| `backend/core-api/src/main/java/com/carix/dashboard/service/DashboardSummaryService.java` | `private String label(...)` 제거, `code.label()` 사용 |
| `backend/core-api/src/main/java/com/carix/dashboard/dto/SnapshotHistoryResponse.java` | `SnapshotPoint` 에 `label` 추가 |
| `backend/core-api/src/main/java/com/carix/dashboard/service/SnapshotSeriesService.java` | `toPoint` 에서 `label` 채움 |
| `backend/core-api/src/test/java/com/carix/dashboard/service/SnapshotReadServiceTest.java` | history 라벨 검증 추가 |
| `frontend/app/lib/dashboardMarketApi.ts` | `SnapshotPoint` 타입에 `label` 추가 |
| `frontend/app/routes/dashboard/history.tsx` | `getSignalBadge` 삭제 → `SignalBadge` |
| `frontend/app/routes/dashboard/index.tsx` | `<Badge>{...label}</Badge>` → `SignalBadge` |
| `frontend/app/routes.ts` | `dev/primitives` 라우트 추가 |

**주의 — 건드리지 말 것.** `frontend/app/lib/dashboardMarketApi.ts` 는 자체 `handleResponse` 를 갖고 있고 401 에서 `UnauthorizedError` 가 아니라 일반 `Error` 를 던진다. `lib/coreApi.ts` 의 `unwrap` 과 갈린 상태다. 이 계획의 범위 밖이다 (`ui-unification-plan.md` §7). Task 2 가 이 파일을 열더라도 통합하지 않는다.

---

### Task 1: 서버 한글 문구를 `DecisionSignalCode` 한 곳으로

**Files:**
- Create: `backend/core-api/src/test/java/com/carix/snapshot/DecisionSignalCodeTest.java`
- Modify: `backend/core-api/src/main/java/com/carix/snapshot/DecisionSignalCode.java`
- Modify: `backend/core-api/src/main/java/com/carix/dashboard/service/DashboardSummaryService.java:99-114`
- Modify: `backend/core-api/src/main/java/com/carix/dashboard/dto/SnapshotHistoryResponse.java`
- Modify: `backend/core-api/src/main/java/com/carix/dashboard/service/SnapshotSeriesService.java:81-93`
- Test: `backend/core-api/src/test/java/com/carix/dashboard/service/SnapshotReadServiceTest.java`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: `DecisionSignalCode.label()` → `String`. `SnapshotHistoryResponse.SnapshotPoint(String baseYearMonth, Double purchasingPowerIndex, Double marketValueIndex, String decisionSignal, String label, String dataVersion, String ruleVersion, SnapshotRationale rationale)` — `label` 이 `decisionSignal` 바로 뒤에 온다. Task 2 의 프런트 타입이 이 순서와 이름을 따른다.

- [ ] **Step 1: 실패 테스트 작성**

`backend/core-api/src/test/java/com/carix/snapshot/DecisionSignalCodeTest.java`

```java
package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 결정 신호의 한글 문구는 여기 한 곳에만 있다.
 *
 * <p>문구가 서버 서비스와 프런트 화면에 따로 적히면 한쪽만 바뀐다 — 실제로 그랬다. 서버는 {@code "스킬 보강"} 을,
 * {@code history.tsx} 는 {@code "스킬 보강 우선"} 을 쓰고 있었다. 캐노니컬은 MVP 요구사항 §193-196 이다.
 */
class DecisionSignalCodeTest {

  @Test
  @DisplayName("다섯 신호의 문구가 MVP 요구사항의 캐노니컬 표기와 같다")
  void 캐노니컬_문구() {
    assertThat(DecisionSignalCode.SEEK_JOB.label()).isEqualTo("이직 탐색");
    assertThat(DecisionSignalCode.NEGOTIATE.label()).isEqualTo("협상 검토");
    assertThat(DecisionSignalCode.UPSKILL.label()).isEqualTo("스킬 보강 우선");
    assertThat(DecisionSignalCode.STAY.label()).isEqualTo("현 상태 유지");
    assertThat(DecisionSignalCode.INSUFFICIENT_DATA.label()).isEqualTo("데이터 부족");
  }

  @Test
  @DisplayName("문구가 비어 있는 신호는 없다")
  void 빈_문구_없음() {
    for (DecisionSignalCode code : DecisionSignalCode.values()) {
      assertThat(code.label()).isNotBlank();
    }
  }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew :core-api:test --tests '*DecisionSignalCodeTest*'`
Expected: 컴파일 실패 — `cannot find symbol: method label()`

- [ ] **Step 3: 열거형에 문구를 단다**

`backend/core-api/src/main/java/com/carix/snapshot/DecisionSignalCode.java` 전체를 아래로 바꾼다. `SalaryGapBucket` 과 같은 형태다.

```java
package com.carix.snapshot;

/**
 * 결정 신호. 값은 {@code core.index_snapshot.decision_signal} 에 그대로 들어간다 (기술 명세 §1.2 DDL 주석).
 *
 * <p>화면에 나가는 한글 문구도 여기 있다. 서비스마다 따로 {@code switch} 를 두면 문구가 갈린다 — 캐노니컬 표기는 MVP 요구사항
 * §193-196 과 데이터 아키텍처 §132-133 이다.
 */
public enum DecisionSignalCode {
  /** 이직 탐색 (규칙 2). */
  SEEK_JOB("이직 탐색"),
  /** 협상 검토 (규칙 3). */
  NEGOTIATE("협상 검토"),
  /** 스킬 보강 우선 (규칙 4). */
  UPSKILL("스킬 보강 우선"),
  /** 현 상태 유지 (규칙 5 및 기본행). */
  STAY("현 상태 유지"),
  /** 데이터 부족 (우선순위 1). */
  INSUFFICIENT_DATA("데이터 부족");

  private final String label;

  DecisionSignalCode(String label) {
    this.label = label;
  }

  /** 화면에 그대로 쓰는 한글 문구. */
  public String label() {
    return label;
  }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew :core-api:test --tests '*DecisionSignalCodeTest*'`
Expected: PASS

- [ ] **Step 5: `DashboardSummaryService` 가 열거형 문구를 쓰게 한다**

`DashboardSummaryService.java` 에서 `private String label(DecisionSignalCode code) { ... }` 메서드 전체를 지우고, 호출부를 바꾼다.

```java
    return new DecisionSignal(
        code.name(),
        code.label(),
        rationale == null ? List.of() : rationale.rulesApplied(),
        gapSentence(rationale));
```

같은 파일 222행 부근의 하드코딩된 문구도 바꾼다.

```java
        new DecisionSignal(
            DecisionSignalCode.INSUFFICIENT_DATA.name(),
            DecisionSignalCode.INSUFFICIENT_DATA.label(),
            List.of(),
            null),
```

- [ ] **Step 6: history 응답에 `label` 을 싣는 실패 테스트 작성**

`SnapshotReadServiceTest.java` 의 `// --- /dashboard/history ---` 절 아래에 추가한다.

```java
  @Test
  @DisplayName("history - 결정 신호의 한글 문구를 코드와 함께 싣는다")
  void history_라벨() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202609", "101.00", "108.00", DecisionSignalCode.UPSKILL)));

    SnapshotHistoryResponse response = seriesService.history(USER_ID);

    assertThat(response.snapshots()).hasSize(1);
    assertThat(response.snapshots().get(0).decisionSignal()).isEqualTo("UPSKILL");
    assertThat(response.snapshots().get(0).label()).isEqualTo("스킬 보강 우선");
  }
```

이 파일의 기존 헬퍼 `snapshot(String ym, String ppi, String mvi, DecisionSignalCode signal)`
(264행)을 그대로 쓴다 — 이미 신호 코드를 받으므로 넓힐 것이 없다. 스텁은 이 파일의 관용구인
BDDMockito `given(...).willReturn(...)` 를 쓴다.

- [ ] **Step 7: 실패 확인**

Run: `cd backend && ./gradlew :core-api:test --tests '*SnapshotReadServiceTest*'`
Expected: 컴파일 실패 — `cannot find symbol: method label()` on `SnapshotPoint`

- [ ] **Step 8: DTO 와 매퍼에 `label` 추가**

`SnapshotHistoryResponse.java`

```java
  /** 스냅샷 한 행. 그 달에 지수를 계산하지 못했으면 {@code null} 로 남는다 ({@code index_snapshot} DDL 주석). */
  public record SnapshotPoint(
      String baseYearMonth,
      @Schema(nullable = true) Double purchasingPowerIndex,
      @Schema(nullable = true) Double marketValueIndex,
      String decisionSignal,
      @Schema(example = "스킬 보강 우선") String label,
      String dataVersion,
      String ruleVersion,
      SnapshotRationale rationale) {}
```

`SnapshotSeriesService.java` 의 `toPoint`

```java
    return new SnapshotHistoryResponse.SnapshotPoint(
        snapshot.getBaseYearMonth(),
        toDouble(snapshot.getPurchasingPowerIndex()),
        toDouble(snapshot.getMarketValueIndex()),
        snapshot.getDecisionSignal().name(),
        snapshot.getDecisionSignal().label(),
        snapshot.getDataVersion(),
        snapshot.getRuleVersion(),
        rationale == null
            ? null
            : new SnapshotHistoryResponse.SnapshotRationale(
                rationale.rulesApplied(), rationale.excludedSkills(), rationale.aggregateAsOf()));
```

- [ ] **Step 9: 전체 백엔드 검증**

Run: `cd backend && ./gradlew :core-api:check`
Expected: PASS. Spotless 가 포맷을 잡으면 다시 돌려 확인한다.

- [ ] **Step 10: 커밋**

```bash
git add backend/core-api/src/main/java/com/carix/snapshot/DecisionSignalCode.java \
        backend/core-api/src/test/java/com/carix/snapshot/DecisionSignalCodeTest.java \
        backend/core-api/src/main/java/com/carix/dashboard/service/DashboardSummaryService.java \
        backend/core-api/src/main/java/com/carix/dashboard/dto/SnapshotHistoryResponse.java \
        backend/core-api/src/main/java/com/carix/dashboard/service/SnapshotSeriesService.java \
        backend/core-api/src/test/java/com/carix/dashboard/service/SnapshotReadServiceTest.java
git commit -m "fix(core-api): 결정 신호 한글 문구를 DecisionSignalCode 한 곳으로 모으고 history 응답에 싣는다"
```

---

### Task 2: 프런트 단일 표현 테이블과 두 화면 배선

**Files:**
- Create: `frontend/app/lib/decisionSignal.ts`
- Create: `frontend/app/lib/decisionSignal.test.ts`
- Create: `frontend/app/components/dashboard/SignalBadge.tsx`
- Create: `frontend/app/components/dashboard/SignalBadge.test.tsx`
- Modify: `frontend/app/lib/dashboardMarketApi.ts` (`SnapshotPoint`)
- Modify: `frontend/app/routes/dashboard/history.tsx:30-70, 그리고 렌더부`
- Modify: `frontend/app/routes/dashboard/index.tsx:128`

**Interfaces:**
- Consumes: Task 1 의 `SnapshotPoint.label` (서버가 내려주는 폴백 문구)
- Produces: `resolveDecisionSignal(code: string, serverLabel?: string | null): DecisionSignalView` 와 `<SignalBadge code={...} serverLabel={...} />`

- [ ] **Step 1: 실패 테스트 작성**

`frontend/app/lib/decisionSignal.test.ts`

```ts
import { describe, expect, it } from "vitest";
import { resolveDecisionSignal } from "./decisionSignal";

describe("resolveDecisionSignal", () => {
  it("아는 코드는 프런트 테이블의 문구와 톤을 돌려준다", () => {
    const view = resolveDecisionSignal("UPSKILL");

    expect(view.label).toBe("스킬 보강 우선");
    expect(view.tone).toBe("info");
  });

  it("아는 코드는 서버 문구가 달라도 프런트 테이블을 따른다", () => {
    const view = resolveDecisionSignal("UPSKILL", "스킬 보강");

    expect(view.label).toBe("스킬 보강 우선");
  });

  it("모르는 코드는 서버 문구로 떨어진다", () => {
    const view = resolveDecisionSignal("RELOCATE", "지역 이동 검토");

    expect(view.label).toBe("지역 이동 검토");
    expect(view.tone).toBe("muted");
  });

  it("모르는 코드에 서버 문구도 없으면 코드를 그대로 보여준다 — 빈 문구로 두지 않는다", () => {
    const view = resolveDecisionSignal("RELOCATE");

    expect(view.label).toBe("RELOCATE");
    expect(view.tone).toBe("muted");
  });

  it("코드가 비어 있으면 판정 대기로 본다", () => {
    const view = resolveDecisionSignal("");

    expect(view.label).toBe("판정 대기");
    expect(view.tone).toBe("muted");
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && pnpm vitest run app/lib/decisionSignal.test.ts`
Expected: FAIL — `Failed to resolve import "./decisionSignal"`

- [ ] **Step 3: 테이블 구현**

`frontend/app/lib/decisionSignal.ts`

```ts
/**
 * 결정 신호의 화면 표현 — 문구·톤·설명이 여기 한 곳에만 있다.
 *
 * 전에는 `S-10` 이 서버가 준 `label` 을, `S-11` 이 자기 파일 안의 `getSignalBadge` 를 따로 썼다.
 * 서버 문구가 바뀌면 한쪽만 따라오는 상태였다. 문구의 캐노니컬 표기는 MVP 요구사항 §193-196 이다.
 *
 * 서버 `label` 은 여기 없는 코드가 내려왔을 때만 쓴다 — 신호가 늘어도 프런트 배포 전까지 빈 뱃지가
 * 나가지 않게 한다.
 */
export type SignalTone =
  | "positive"
  | "warning"
  | "danger"
  | "info"
  | "action"
  | "muted";

export interface DecisionSignalView {
  label: string;
  tone: SignalTone;
  description: string | null;
}

const SIGNALS: Record<string, { label: string; tone: SignalTone; description: string }> = {
  SEEK_JOB: {
    label: "이직 탐색",
    tone: "warning",
    description: "연봉 격차가 크고 스킬 수요도 올라 있습니다. 적극적인 이직 탐색 구간입니다.",
  },
  NEGOTIATE: {
    label: "협상 검토",
    tone: "action",
    description: "연봉 격차가 협상 가능 구간이고 스킬 가치는 상승세입니다.",
  },
  UPSKILL: {
    label: "스킬 보강 우선",
    tone: "info",
    description: "시장가치와 구매력이 모두 낮습니다. 스킬 보강이 먼저입니다.",
  },
  STAY: {
    label: "현 상태 유지",
    tone: "positive",
    description: "지표가 시장 수준 안에 있습니다. 당장 움직일 이유가 없습니다.",
  },
  INSUFFICIENT_DATA: {
    label: "데이터 부족",
    tone: "danger",
    description: "판정에 필요한 입력이나 시장 표본이 모자랍니다.",
  },
};

/**
 * 톤 → 클래스.
 *
 * NOTE: (cjkang) `action` 만 토큰 밖 팔레트를 쓴다. 의미 색상 토큰이 넷인데 신호가 다섯이라
 * 다섯 번째가 없기 때문이다. UI 통일 계획 P1 에서 `--carix-action` 이 생기면 이 한 줄만 바꾼다.
 */
export const SIGNAL_TONE_CLASS: Record<SignalTone, string> = {
  positive: "bg-carix-positive/15 text-carix-positive border border-carix-positive/30",
  warning: "bg-carix-warning/15 text-carix-warning border border-carix-warning/30",
  danger: "bg-carix-danger/15 text-carix-danger border border-carix-danger/30",
  info: "bg-carix-info/15 text-carix-info border border-carix-info/30",
  action: "bg-purple-500/15 text-purple-600 dark:text-purple-400 border border-purple-500/30",
  muted: "bg-carix-border/40 text-carix-text/70 border border-carix-border",
};

export function resolveDecisionSignal(
  code: string,
  serverLabel?: string | null,
): DecisionSignalView {
  const known = SIGNALS[code];
  if (known) {
    return { label: known.label, tone: known.tone, description: known.description };
  }
  return {
    label: serverLabel || code || "판정 대기",
    tone: "muted",
    description: null,
  };
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd frontend && pnpm vitest run app/lib/decisionSignal.test.ts`
Expected: PASS (5 tests)

- [ ] **Step 5: 뱃지 컴포넌트 실패 테스트 작성**

`frontend/app/components/dashboard/SignalBadge.test.tsx`

```tsx
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { SignalBadge } from "./SignalBadge";

describe("SignalBadge", () => {
  it("아는 코드의 캐노니컬 문구를 렌더링한다", () => {
    render(<SignalBadge code="SEEK_JOB" />);

    expect(screen.getByText("이직 탐색")).toBeInTheDocument();
  });

  it("모르는 코드는 서버 문구로 떨어진다", () => {
    render(<SignalBadge code="RELOCATE" serverLabel="지역 이동 검토" />);

    expect(screen.getByText("지역 이동 검토")).toBeInTheDocument();
  });
});
```

- [ ] **Step 6: 실패 확인**

Run: `cd frontend && pnpm vitest run app/components/dashboard/SignalBadge.test.tsx`
Expected: FAIL — `Failed to resolve import "./SignalBadge"`

- [ ] **Step 7: 뱃지 구현**

`frontend/app/components/dashboard/SignalBadge.tsx`

```tsx
import {
  resolveDecisionSignal,
  SIGNAL_TONE_CLASS,
} from "../../lib/decisionSignal";

interface SignalBadgeProps {
  code: string;
  /** 프런트 테이블에 없는 코드일 때만 쓰는 서버 문구. */
  serverLabel?: string | null;
  className?: string;
}

/** 결정 신호 뱃지. 두 화면(`S-10`·`S-11`)이 같은 것을 쓴다. */
export function SignalBadge({ code, serverLabel, className }: SignalBadgeProps) {
  const { label, tone } = resolveDecisionSignal(code, serverLabel);

  return (
    <span
      data-testid="signal-badge"
      data-tone={tone}
      className={[
        "inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold",
        SIGNAL_TONE_CLASS[tone],
        className ?? "",
      ].join(" ")}
    >
      {label}
    </span>
  );
}
```

- [ ] **Step 8: 통과 확인**

Run: `cd frontend && pnpm vitest run app/components/dashboard/SignalBadge.test.tsx`
Expected: PASS (2 tests)

- [ ] **Step 9: 프런트 타입에 `label` 추가**

`frontend/app/lib/dashboardMarketApi.ts` 의 `SnapshotPoint` 에 필드를 넣는다. 순서와 이름은 Task 1 의 서버 레코드와 같다.

```ts
export interface SnapshotPoint {
  baseYearMonth: string;
  purchasingPowerIndex: number | null;
  marketValueIndex: number | null;
  decisionSignal: string;
  label: string;
  dataVersion: string;
  ruleVersion: string;
  rationale: SnapshotRationale | null;
}
```

- [ ] **Step 10: `S-11` 이력 화면 배선**

`frontend/app/routes/dashboard/history.tsx` — 호출부는 세 군데다.

1. 33행의 `getSignalBadge` 함수 전체(그 위 주석 포함)를 지운다.
2. import 를 추가한다: `import { SignalBadge } from "../../components/dashboard/SignalBadge";`
3. 210행 `const signalInfo = getSignalBadge(item.decisionSignal);` 줄을 지운다.
4. 258-261행의 뱃지 `<span>` 을 바꾼다.

```tsx
                          <SignalBadge
                            code={item.decisionSignal}
                            serverLabel={item.label}
                            className="px-2.5 py-1"
                          />
```

5. 294-301행의 상세 패널 뱃지 `<span>` 을 바꾼다.

```tsx
                    <SignalBadge
                      code={selectedSnapshot.decisionSignal}
                      serverLabel={selectedSnapshot.label}
                    />
```

- [ ] **Step 11: `S-10` 대시보드 배선**

`frontend/app/routes/dashboard/index.tsx:128` 의 한 줄을 바꾼다.

```tsx
<SignalBadge
  code={data.decisionSignal.code}
  serverLabel={data.decisionSignal.label}
  className="self-start"
/>
```

import 를 추가한다: `import { SignalBadge } from "../../components/dashboard/SignalBadge";`

`Badge` 는 이 파일에서 128행 한 곳에서만 쓰인다. 3행의
`import { Badge } from "@/components/ui/badge";` 도 함께 지운다.

- [ ] **Step 12: 기존 화면 테스트가 새 문구를 반영하는지 확인**

`SnapshotPoint.label` 이 필수 필드라 `history.test.tsx` 의 목 스냅샷 네 개가 타입 오류를 낸다.
47·79·92·209행의 각 객체에 `decisionSignal` 바로 아래 줄을 넣는다.

```ts
          label: "현 상태 유지",
```

문구는 그 객체의 `decisionSignal` 에 맞춘다 — `STAY`→`현 상태 유지`, `SEEK_JOB`→`이직 탐색`,
`INSUFFICIENT_DATA`→`데이터 부족`.

**단언은 고칠 것이 없다.** 이 두 파일이 기대하는 문구는 `현 상태 유지`·`이직 탐색`·`데이터 부족`
뿐이고 셋 다 캐노니컬 표기와 같다. `스킬 보강` 을 기대하는 단언은 레포에 없다.

Run: `cd frontend && pnpm test:unit`
Expected: PASS

- [ ] **Step 13: 프런트 전체 검증**

Run: `cd frontend && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test:unit && pnpm build`
Expected: 다섯 개 모두 PASS

- [ ] **Step 14: 커밋**

```bash
git add frontend/app/lib/decisionSignal.ts frontend/app/lib/decisionSignal.test.ts \
        frontend/app/components/dashboard/SignalBadge.tsx \
        frontend/app/components/dashboard/SignalBadge.test.tsx \
        frontend/app/lib/dashboardMarketApi.ts \
        frontend/app/routes/dashboard/history.tsx \
        frontend/app/routes/dashboard/index.tsx
git commit -m "fix(frontend): 결정 신호 표현을 단일 테이블로 모으고 S-10·S-11 이 같은 뱃지를 쓰게 한다"
```

---

### Task 3: 프리미티브 갤러리와 시각 회귀 베이스라인

P3 이 `Card`·`Button`·`Badge`·`Table` 의 기본값을 바꾼다. 그 변화를 받아낼 그물이다.

**한계 셋을 알고 쓴다.**

1. 인증이 필요한 화면 7개를 대신하지 않는다. `Card` 의 `gap` 변경이 실제 화면 내부를 재배치하는
   것은 갤러리의 「합성 카드」 절이 부분적으로만 잡는다. 화면 단위 베이스라인은
   `CARIX_E2E_EMAIL` 계정이 서는 7주차에 붙인다.
2. **CI 가 이걸 돌리지 않는다.** `.github/workflows/pr-check.yml` 에 `test:e2e` 가 없고
   프런트 CI 는 `lint`·`typecheck`·`format:check`·`test:unit`·`build` 다섯뿐이다. P3 을
   하는 사람이 손으로 돌려야 하는 게이트다 — P3 태스크의 첫 줄에 그 명령을 박는다.
3. Playwright 스냅샷 파일명에는 프로젝트(`chromium`)와 플랫폼(`darwin`/`linux`) 접미사가
   붙는다. 여기서 만드는 베이스라인은 만든 사람의 플랫폼 것이다. 다른 OS 에서 돌리면
   베이스라인이 없다며 새로 만들고 지나간다 — 회귀를 못 잡는다. P3 을 P0 과 같은 OS 에서
   하거나, CI 도입 시 컨테이너 안에서 다시 만든다.

**Files:**
- Create: `frontend/app/routes/dev/primitives.tsx`
- Create: `frontend/e2e/primitives.spec.ts`
- Modify: `frontend/app/routes.ts`

**Interfaces:**
- Consumes: Task 2 의 `SignalBadge`
- Produces: `/dev/primitives` 라우트. 각 절은 `data-testid` 로 잡는다 — `primitives-buttons`, `primitives-badges`, `primitives-cards`, `primitives-table`, `primitives-composed`

- [ ] **Step 1: 갤러리 라우트 작성**

`frontend/app/routes/dev/primitives.tsx`

```tsx
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { SignalBadge } from "../../components/dashboard/SignalBadge";

/**
 * 프리미티브 갤러리 — 세션 가드 없이 여는 개발 프리뷰 (`dev/skills`·`dev/trends` 와 같은 자리).
 *
 * 시각 회귀 베이스라인이 이 화면을 찍는다. 인증이 필요한 화면은 `CARIX_E2E_EMAIL` 계정이 있어야
 * 돌기 때문에 (`e2e/onboarding-flow.spec.ts`) 프리미티브 계층에서 잠근다.
 */
const BUTTON_VARIANTS = [
  "default",
  "destructive",
  "outline",
  "secondary",
  "ghost",
  "link",
] as const;

const BADGE_VARIANTS = [
  "default",
  "secondary",
  "destructive",
  "outline",
  "ghost",
  "link",
] as const;

const SIGNAL_CODES = [
  "SEEK_JOB",
  "NEGOTIATE",
  "UPSKILL",
  "STAY",
  "INSUFFICIENT_DATA",
  "RELOCATE",
] as const;

function Section({
  id,
  title,
  children,
}: {
  id: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section data-testid={id} className="flex flex-col gap-3 p-6">
      <h2 className="font-display text-sm font-bold text-carix-text/70">
        {title}
      </h2>
      {children}
    </section>
  );
}

function Gallery() {
  return (
    <div className="bg-carix-bg text-carix-text">
      <Section id="primitives-buttons" title="Button">
        <div className="flex flex-wrap items-center gap-3">
          {BUTTON_VARIANTS.map((variant) => (
            <Button key={variant} variant={variant}>
              {variant}
            </Button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {BUTTON_VARIANTS.map((variant) => (
            <Button key={`${variant}-disabled`} variant={variant} disabled>
              {variant} disabled
            </Button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button size="xs">xs</Button>
          <Button size="sm">sm</Button>
          <Button size="default">default</Button>
          <Button size="lg">lg</Button>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button size="icon-xs" aria-label="icon-xs">
            +
          </Button>
          <Button size="icon-sm" aria-label="icon-sm">
            +
          </Button>
          <Button size="icon" aria-label="icon">
            +
          </Button>
          <Button size="icon-lg" aria-label="icon-lg">
            +
          </Button>
        </div>
        <Button data-testid="button-focus-target">포커스 대상</Button>
      </Section>

      <Section id="primitives-badges" title="Badge / SignalBadge">
        <div className="flex flex-wrap items-center gap-3">
          {BADGE_VARIANTS.map((variant) => (
            <Badge key={variant} variant={variant}>
              {variant}
            </Badge>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {SIGNAL_CODES.map((code) => (
            <SignalBadge key={code} code={code} serverLabel="서버 폴백 문구" />
          ))}
        </div>
      </Section>

      <Section id="primitives-cards" title="Card">
        <div className="grid gap-4 sm:grid-cols-3">
          <Card className="p-5">
            <span className="text-xs text-carix-text/70">
              className 으로 p-5 를 준 카드
            </span>
          </Card>
          <Card>
            <CardContent>기본 카드</CardContent>
          </Card>
          <Card className="p-5 gap-3 transition-transform hover:-translate-y-0.5 duration-200">
            <span className="text-2xl font-bold tracking-tight">102.4</span>
          </Card>
        </div>
      </Section>

      <Section id="primitives-composed" title="합성 카드 — 간격 변화 감지용">
        <Card>
          <CardHeader>
            <CardTitle>
              헤더 · 본문 · 푸터를 실제 화면과 같은 순서로 쌓는다
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p>
              Card 의 gap 이 바뀌면 이 세 덩어리 사이 간격이 diff 로 드러난다.
            </p>
          </CardContent>
          <CardFooter>
            <Button size="sm">푸터 버튼</Button>
          </CardFooter>
        </Card>
      </Section>

      <Section id="primitives-table" title="Table">
        <div className="overflow-hidden rounded-xl border border-carix-border bg-carix-surface shadow-xs">
          <table className="w-full text-left text-sm text-carix-text">
            <thead className="border-b border-carix-border bg-carix-bg/60 text-xs font-medium text-carix-text/70">
              <tr>
                <th scope="col" className="px-4 py-3">
                  기준월
                </th>
                <th scope="col" className="px-4 py-3 text-center">
                  구매력 지수
                </th>
                <th scope="col" className="px-4 py-3 text-center">
                  결정 신호
                </th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-carix-border/60">
                <td className="px-4 py-3">2026-09</td>
                <td className="px-4 py-3 text-center">102.4</td>
                <td className="px-4 py-3 text-center">
                  <SignalBadge code="STAY" />
                </td>
              </tr>
              <tr>
                <td className="px-4 py-3">2026-08</td>
                <td className="px-4 py-3 text-center">-</td>
                <td className="px-4 py-3 text-center">
                  <SignalBadge code="INSUFFICIENT_DATA" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </Section>
    </div>
  );
}

export function meta() {
  return [{ title: "프리미티브 갤러리 | Carix" }];
}

export default function DevPrimitives() {
  return (
    <main className="min-h-screen">
      <Gallery />
    </main>
  );
}
```

- [ ] **Step 2: 라우트 등록**

`frontend/app/routes.ts` 의 `dev/trends` 바로 아래에 추가한다.

```ts
  route("dev/primitives", "routes/dev/primitives.tsx"),
```

- [ ] **Step 3: 라우트 개수 테스트 갱신**

`frontend/app/routes.test.ts` 가 최상위 라우트 개수를 센다. `dev/primitives` 가 늘었으므로
같이 고친다 — 안 고치면 Step 8 의 `pnpm test:unit` 이 반드시 실패한다.

```ts
    // home, login, register, protected 레이아웃(그 안에 onboarding/dashboard/mypage 3개가
    // 중첩된다), 2개 관리자 라우트(admin/collector, admin/pipeline), 3개 개발 프리뷰 라우트
    expect(routes).toHaveLength(9);
```

- [ ] **Step 4: 화면이 뜨는지 확인**

Run: `cd frontend && pnpm build && pnpm start`
그리고 다른 터미널에서: `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/dev/primitives`
Expected: `200`. 확인 후 서버를 내린다.

- [ ] **Step 5: 시각 회귀 스펙 작성**

`frontend/e2e/primitives.spec.ts`

```ts
import { expect, test, type Page } from "@playwright/test";

/**
 * 프리미티브 시각 회귀 베이스라인.
 *
 * UI 통일 계획 P3 이 `Card`·`Button`·`Badge`·`Table` 의 기본값을 B 시각 언어로 바꾼다. 그 변경이
 * 의도한 것만 바꾸는지 여기서 diff 로 본다.
 *
 * 인증이 필요한 화면 7개는 이 그물에 들어오지 않는다 — `CARIX_E2E_EMAIL` 계정이 서는 7주차에
 * 별도로 붙인다 (`onboarding-flow.spec.ts` 참고).
 *
 * 라이트/다크는 `ThemeToggle` 이 실제로 쓰는 메커니즘 그대로 `document.documentElement` 에
 * `light`/`dark` 클래스를 넣고 뺀다 — `tokens.css` 의 `.dark`/`.light` 는 `:root` 에서만
 * `--carix-*` 를 재정의하기 때문에, 중첩된 div 에 클래스를 넣는 방식은 다크 베이스라인을 못 만든다.
 */
const SECTIONS = [
  "primitives-buttons",
  "primitives-badges",
  "primitives-cards",
  "primitives-composed",
  "primitives-table",
];

async function setTheme(page: Page, theme: "light" | "dark") {
  await page.evaluate((t) => {
    document.documentElement.classList.remove("light", "dark");
    document.documentElement.classList.add(t);
  }, theme);
}

test.beforeEach(async ({ page }) => {
  await page.goto("/dev/primitives");
});

for (const section of SECTIONS) {
  test(`${section} 절이 베이스라인과 같다`, async ({ page }) => {
    const block = page.getByTestId(section);

    await setTheme(page, "light");
    await expect(block).toHaveScreenshot(`${section}-light.png`);

    await setTheme(page, "dark");
    await expect(block).toHaveScreenshot(`${section}-dark.png`);
  });
}

test("버튼 hover 상태가 베이스라인과 같다", async ({ page }) => {
  const target = page.getByTestId("button-focus-target").first();
  await target.hover();

  await setTheme(page, "light");
  await expect(target).toHaveScreenshot("button-hover-light.png");

  await setTheme(page, "dark");
  await expect(target).toHaveScreenshot("button-hover-dark.png");
});

test("버튼 focus-visible 링이 베이스라인과 같다", async ({ page }) => {
  const target = page.getByTestId("button-focus-target").first();
  await target.focus();

  await setTheme(page, "light");
  await expect(target).toHaveScreenshot("button-focus-light.png");

  await setTheme(page, "dark");
  await expect(target).toHaveScreenshot("button-focus-dark.png");
});
```

- [ ] **Step 6: 베이스라인 생성**

Run: `cd frontend && pnpm test:e2e --update-snapshots primitives.spec.ts`
Expected: 스냅샷 파일이 `frontend/e2e/primitives.spec.ts-snapshots/` 에 생성된다. 14개(5절 × 2테마 + hover 2테마 + focus 2테마) — hover·focus 도 라이트/다크를 각각 찍는다.

- [ ] **Step 7: 베이스라인이 실제로 잠그는지 확인**

`frontend/app/components/ui/button.tsx` 의 `default` size 를 `h-9` 에서 `h-12` 로 잠시 바꾼다.

Run: `cd frontend && pnpm test:e2e primitives.spec.ts`
Expected: FAIL — `primitives-buttons-light.png` 등이 diff 로 실패한다.

**되돌린다.** `h-12` 를 `h-9` 로 복구하고 다시 돌려 PASS 를 확인한다.

Run: `cd frontend && pnpm test:e2e primitives.spec.ts`
Expected: PASS

- [ ] **Step 8: 프런트 전체 검증**

Run: `cd frontend && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test:unit && pnpm build`
Expected: 다섯 개 모두 PASS

- [ ] **Step 9: 커밋**

```bash
git add frontend/app/routes/dev/primitives.tsx frontend/app/routes.ts \
        frontend/app/routes.test.ts \
        frontend/e2e/primitives.spec.ts frontend/e2e/primitives.spec.ts-snapshots
git commit -m "test(frontend): 프리미티브 갤러리와 시각 회귀 베이스라인을 세운다"
```

---

## 완료 조건

- [ ] `DecisionSignalCode.label()` 이 한글 문구의 유일한 서버 출처다. `DashboardSummaryService` 에 `switch` 가 남아 있지 않다.
- [ ] `GET /dashboard/history` 응답이 `label` 을 싣는다.
- [ ] `frontend/app/lib/decisionSignal.ts` 가 화면 표현의 유일한 프런트 출처다. `history.tsx` 에 `getSignalBadge` 가 없다.
- [ ] `S-10` 과 `S-11` 이 같은 신호에 같은 문구·같은 색을 보여준다.
- [ ] `UPSKILL` 문구가 두 화면 모두 `스킬 보강 우선` 이다.
- [ ] `/dev/primitives` 가 열리고 `pnpm test:e2e primitives.spec.ts` 가 베이스라인과 대조해 통과한다.
- [ ] 베이스라인이 실제로 잠근다는 것을 Task 3 Step 7 으로 확인했다 — 통과만 보고 넘어가지 않는다.
- [ ] 프런트 검증 5종과 `./gradlew :core-api:check` 가 통과한다.
