import { describe, expect, it } from "vitest";

import type { EvaluateRequest } from "./api/scenario";
import { initialStep } from "./simulatorStep";

const STAY_ONLY: EvaluateRequest = { stay: { raisePercent: 3.5 } };

describe("initialStep", () => {
  it("이직_없는_초안은_stay_단계로", () => {
    expect(initialStep(new URLSearchParams(), STAY_ONLY)).toBe("stay");
  });
});
