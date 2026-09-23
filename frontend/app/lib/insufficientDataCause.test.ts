import { describe, expect, it } from "vitest";

import { MARKET_COVERAGE_GAP, isUserActionable } from "./insufficientDataCause";

describe("isUserActionable", () => {
  it("수집 공백이 원인이면 입력 유도를 띄우지 않는다", () => {
    expect(isUserActionable([MARKET_COVERAGE_GAP])).toBe(false);
  });

  it("사용자 입력이 원인이면 유도를 띄운다", () => {
    expect(isUserActionable(["현재 연봉 미확보"])).toBe(true);
  });

  it("규칙 문구가 없으면 기존 동작을 유지한다", () => {
    expect(isUserActionable(undefined)).toBe(true);
    expect(isUserActionable([])).toBe(true);
  });

  // 서버 DecisionSignalEngine.MARKET_COVERAGE_GAP 과 같은 문자열이어야 한다.
  it("서버와 맞춘 문구를 고정한다", () => {
    expect(MARKET_COVERAGE_GAP).toBe("시장 집계 미수집 구간");
  });
});
