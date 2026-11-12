import { describe, expect, it } from "vitest";

import { evaluationResponseSchema } from "./scenario";

const base = {
  stay: {
    realChangePercent: 0.2,
    realChangeGovernmentPercent: 0.7,
    breakevenPersonalPercent: 2.8,
    breakevenGovernmentPercent: 2.3,
    marketGapPercent: null,
  },
  move: {
    stayCumulative: [1],
    moveCumulative: [2],
    crossoverMonth: null,
    firstYear: {
      salaryDiff: 100,
      gap: 0,
      oneTimeCost: 0,
      benefit: 0,
      total: 100,
    },
    expectedRange: { min: 0, max: 0 },
    stock: null,
    equalizingRaisePercent: null,
    feasibility: {
      status: "INSUFFICIENT_SAMPLE",
      sampleSize: 0,
      minRequired: 10,
      feasibilityScore: null,
      targetPercentile: null,
      difficultyGrade: null,
      feedbackMessage: null,
    },
  },
  inflation: {
    governmentPercent: 2,
    personalPercent: null,
    gapPp: null,
    topGapCategory: null,
    similar: false,
    asOf: "2026-09",
    source: "한국은행",
  },
  assumptions: [],
  unreflected: [],
  typeLabel: null,
};

describe("evaluationResponseSchema", () => {
  // 옛 모양에서 파싱이 던지면 시뮬레이터가 결과 대신 오류를 보인다.
  it("옛 퇴직금 경고와 퇴직금 항 없는 첫해를 던지지 않고 읽는다", () => {
    const parsed = evaluationResponseSchema.parse({
      ...base,
      severance: { monthsToAnniversary: 3, estimatedAmount: 5_000_000 },
    });
    expect(parsed.severance).toBeNull();
    expect(parsed.move?.firstYear.severance).toBe(0);
  });

  it("유형·상태가 빈 퇴직금도 null 로 접는다", () => {
    const parsed = evaluationResponseSchema.parse({
      ...base,
      severance: { pensionType: null, status: null, points: [] },
    });
    expect(parsed.severance).toBeNull();
  });
});
