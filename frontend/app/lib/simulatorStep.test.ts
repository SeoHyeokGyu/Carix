import { describe, expect, it } from "vitest";

import type { EvaluateRequest } from "./api/scenario";
import { initialStep, stepStatus } from "./simulatorStep";

const STAY_ONLY: EvaluateRequest = { stay: { raisePercent: 3.5 } };
const WITH_MOVE: EvaluateRequest = {
  stay: { raisePercent: 3.5 },
  move: {
    grossSalary: 72_000_000,
    unemploymentMonths: 2,
    oneTimeCost: 0,
    benefitEligible: true,
    bonusIncluded: true,
  },
};

describe("initialStep", () => {
  it("옛 쿼리 stay·result 는 새 단계로 읽는다", () => {
    expect(initialStep(new URLSearchParams("step=stay"), WITH_MOVE)).toBe(
      "current",
    );
    expect(initialStep(new URLSearchParams("step=result"), WITH_MOVE)).toBe(
      "move",
    );
  });

  it("계산 요청 없이 move 로 가려 하면 current", () => {
    expect(initialStep(new URLSearchParams("step=move"), null)).toBe("current");
  });

  it("쿼리 없으면 move 유무로 정한다", () => {
    expect(initialStep(new URLSearchParams(), STAY_ONLY)).toBe("current");
    expect(initialStep(new URLSearchParams(), WITH_MOVE)).toBe("move");
  });
});

describe("stepStatus", () => {
  it("✓ 는 현재 단계와 무관하게 입력 유무로", () => {
    expect(stepStatus(null)).toEqual({
      moveReachable: false,
      currentDone: false,
      moveDone: false,
    });
    expect(stepStatus(STAY_ONLY)).toEqual({
      moveReachable: true,
      currentDone: true,
      moveDone: false,
    });
    expect(stepStatus(WITH_MOVE)).toEqual({
      moveReachable: true,
      currentDone: true,
      moveDone: true,
    });
  });
});
