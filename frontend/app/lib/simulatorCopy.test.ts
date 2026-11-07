import { describe, expect, it } from "vitest";

import { inflationNote, marketGapLine, moveAdvice } from "./simulatorCopy";

describe("moveAdvice", () => {
  it("역전이 없으면 조언을 내지 않는다", () => {
    expect(moveAdvice(null)).toBeNull();
  });
});

describe("marketGapLine", () => {
  it("격차가 양수면 통계청 중앙값보다 덜 받는다", () => {
    // 격차 = (중앙값 − 내 연봉) / 내 연봉
    expect(marketGapLine(12.3)).toBe(
      "통계청 전 직무 중앙값보다 12.3% 낮음 (세전 기준)",
    );
    expect(marketGapLine(-4)).toBe(
      "통계청 전 직무 중앙값보다 4.0% 높음 (세전 기준)",
    );
  });
});

describe("inflationNote", () => {
  // 운영 화면: 차이 0.27%p 가 소수 한 자리로 반올림돼 "차이 0.3%p — 평균과 비슷" 으로 보였다.
  // 비슷함의 기준이 0.3%p 미만이라 문장이 스스로 모순된다.
  it("기준 바로 아래 차이를 기준값으로 반올림해 보이지 않는다", () => {
    const note = inflationNote({
      governmentPercent: 2.5,
      personalPercent: 2.23,
      gapPp: -0.27,
      topGapCategory: null,
      similar: true,
      asOf: "2026-08",
      source: "한국은행 경제전망보고서",
    });

    expect(note).toBe(
      "개인물가와 한국은행 전망 차이 0.3%p 미만 — 소비구조가 평균과 비슷합니다",
    );
  });
});
