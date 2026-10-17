import { describe, expect, it } from "vitest";

import type { SalaryTrendPoint } from "./dashboardMarketApi";
import { toSalaryTrendSeries } from "./salaryTrend";

function point(yearMonth: string, median: number | null): SalaryTrendPoint {
  return {
    yearMonth,
    median,
    sampleSize: 20,
    disclosedSampleSize: 10,
    missingRate: 50,
  };
}

describe("toSalaryTrendSeries", () => {
  it("가장 오래된 달부터 달력 칸에 놓는다", () => {
    expect(
      toSalaryTrendSeries([
        point("2026-06", 51_000_000),
        point("2026-05", 50_000_000),
      ]),
    ).toEqual({
      slots: 2,
      segments: [
        [
          { index: 0, value: 50_000_000 },
          { index: 1, value: 51_000_000 },
        ],
      ],
    });
  });

  it("표본 부족(null)인 달과 빠진 달에서 선을 끊는다", () => {
    const series = toSalaryTrendSeries([
      point("2026-01", 50_000_000),
      point("2026-02", null),
      point("2026-03", 51_000_000),
      point("2026-05", 52_000_000),
    ]);
    expect(series.slots).toBe(5);
    expect(series.segments).toEqual([
      [{ index: 0, value: 50_000_000 }],
      [{ index: 2, value: 51_000_000 }],
      [{ index: 4, value: 52_000_000 }],
    ]);
  });

  it("해가 바뀌어도 달 칸이 이어진다", () => {
    const series = toSalaryTrendSeries([
      point("2025-12", 50_000_000),
      point("2026-01", 51_000_000),
    ]);
    expect(series.slots).toBe(2);
    expect(series.segments).toHaveLength(1);
  });

  it("0 이하는 값이 아니다", () => {
    const series = toSalaryTrendSeries([point("2026-01", 0)]);
    expect(series.segments).toEqual([]);
  });

  it("형식이 아닌 달은 버린다", () => {
    expect(toSalaryTrendSeries([point("oops", 50_000_000)])).toEqual({
      slots: 0,
      segments: [],
    });
  });
});
