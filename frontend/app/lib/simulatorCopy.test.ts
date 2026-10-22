import { describe, expect, it } from "vitest";

import { moveAdvice, moveHeadline } from "./simulatorCopy";

describe("moveHeadline", () => {
  it("역전_없으면_4년_문구", () => {
    expect(moveHeadline(-3_000_000, null)).toEqual({
      headline: "이직 첫해 -300만",
      note: "비교 기간 4년 안에 역전되지 않습니다",
    });
    expect(moveAdvice(null)).toBeNull();
  });
});
