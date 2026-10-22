import { describe, expect, it } from "vitest";

import { marketGapLine, moveAdvice, moveHeadline } from "./simulatorCopy";

describe("moveHeadline", () => {
  it("역전_없으면_4년_문구", () => {
    expect(moveHeadline(-3_000_000, null)).toEqual({
      headline: "이직 첫해 -300만",
      note: "비교 기간 4년 안에 역전되지 않습니다",
    });
    expect(moveAdvice(null)).toBeNull();
  });
});

describe("marketGapLine", () => {
  it("격차가 양수면 시장 중앙값보다 덜 받는다", () => {
    // 격차 = (중앙값 − 내 연봉) / 내 연봉
    expect(marketGapLine(12.3)).toBe("시장 중앙값보다 12.3% 낮음 (세전 기준)");
    expect(marketGapLine(-4)).toBe("시장 중앙값보다 4.0% 높음 (세전 기준)");
  });
});
