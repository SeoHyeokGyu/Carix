import { describe, expect, it } from "vitest";
import { isThinFamily, isThinSample, parseTab } from "./dataExplore";

describe("dataExplore", () => {
  it("unknown tab falls back to monthly", () => {
    expect(parseTab(null)).toBe("monthly");
    expect(parseTab("nope")).toBe("monthly");
    expect(parseTab("postings")).toBe("postings");
  });

  it("표본 200 미만과 직무 표본 10 미만을 강조한다", () => {
    expect(isThinSample(199)).toBe(true);
    expect(isThinSample(200)).toBe(false);
    expect(isThinFamily(9)).toBe(true);
    expect(isThinFamily(10)).toBe(false);
  });
});
