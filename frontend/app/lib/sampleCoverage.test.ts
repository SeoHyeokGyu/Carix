import { describe, expect, it } from "vitest";

import { toSampleCoverage } from "./sampleCoverage";

describe("toSampleCoverage", () => {
  it("누락률(퍼센트)에서 연봉이 적힌 건수와 비율을 낸다", () => {
    expect(toSampleCoverage(308, 69.81)).toEqual({
      sampleSize: 308,
      withSalary: 93,
      withSalaryPercent: 30,
    });
  });

  it("표본이 0 이거나 없으면 null — 0 으로 나누지 않는다", () => {
    expect(toSampleCoverage(0, 50)).toBeNull();
    expect(toSampleCoverage(undefined, 50)).toBeNull();
  });
});
