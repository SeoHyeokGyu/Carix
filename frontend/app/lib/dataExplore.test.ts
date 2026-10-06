import { describe, expect, it } from "vitest";
import {
  formatManwon,
  formatSalaryRange,
  isThinFamily,
  isThinSample,
  parseTab,
} from "./dataExplore";

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

  it("원 단위 금액을 만원 단위로 보인다", () => {
    expect(formatManwon(61_234_567)).toBe("6,123만원");
    expect(formatManwon(26_920_000)).toBe("2,692만원");
  });

  it("제시 연봉 범위는 있는 쪽만, 둘 다 없으면 미공개", () => {
    expect(formatSalaryRange(50_000_000, 70_000_000)).toBe(
      "5,000만원~7,000만원",
    );
    expect(formatSalaryRange(50_000_000, null)).toBe("5,000만원~");
    expect(formatSalaryRange(null, 70_000_000)).toBe("~7,000만원");
    expect(formatSalaryRange(60_000_000, 60_000_000)).toBe("6,000만원");
    expect(formatSalaryRange(null, null)).toBe("미공개");
  });
});
