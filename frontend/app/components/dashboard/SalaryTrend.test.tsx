import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type {
  SalaryTrendPoint,
  TrendComparison,
  TrendVerdict,
} from "../../lib/dashboardMarketApi";
import { SalaryTrend } from "./SalaryTrend";

const TREND: SalaryTrendPoint[] = [
  {
    yearMonth: "2026-04",
    median: 60_000_000,
    sampleSize: 20,
    disclosedSampleSize: 12,
    missingRate: 40,
  },
  {
    yearMonth: "2026-05",
    median: null,
    sampleSize: 6,
    disclosedSampleSize: 2,
    missingRate: 66.7,
  },
  {
    yearMonth: "2026-06",
    median: 62_000_000,
    sampleSize: 18,
    disclosedSampleSize: 11,
    missingRate: 38.9,
  },
];

function comparison(
  verdict: TrendVerdict,
  previousMedian: number | null,
  recentMedian: number | null,
  overrides: Partial<TrendComparison> = {},
): TrendComparison {
  return {
    previous: {
      from: "2026-04",
      to: "2026-06",
      median: previousMedian,
      ciLow: null,
      ciHigh: null,
      disclosedSampleSize: 35,
    },
    recent: {
      from: "2026-07",
      to: "2026-09",
      median: recentMedian,
      ciLow: null,
      ciHigh: null,
      disclosedSampleSize: 47,
    },
    verdict,
    sources: ["JOBKOREA"],
    inProgress: ["2026-10"],
    ...overrides,
  };
}

describe("SalaryTrend", () => {
  it("변화 없음: 두 기간 중앙값과 건수, 원천, 집계 중 안내를 보이고 차이 금액은 숨긴다", () => {
    render(
      <SalaryTrend
        trend={TREND}
        comparison={comparison("FLAT", 50_000_000, 41_000_000)}
      />,
    );
    expect(screen.getByText("2026-04 ~ 06")).toBeInTheDocument();
    expect(screen.getByText("5,000만 원")).toBeInTheDocument();
    expect(screen.getByText("공개 35건")).toBeInTheDocument();
    expect(screen.getByText("2026-07 ~ 09")).toBeInTheDocument();
    expect(screen.getByText("4,100만 원")).toBeInTheDocument();
    expect(screen.getByText("공개 47건")).toBeInTheDocument();
    expect(screen.getByText("뚜렷한 변화 없음")).toBeInTheDocument();
    expect(
      screen.getByText(
        "잡코리아 무작위 표본 기준 — 매달 같은 방식으로 뽑은 공고끼리 비교합니다.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("2026-10 은 집계 중이라 비교에서 제외"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/\(\+|\(−/)).not.toBeInTheDocument();
  });

  it("상승이면 차이 금액과 비율을 보인다", () => {
    render(
      <SalaryTrend
        trend={TREND}
        comparison={comparison("UP", 40_000_000, 50_000_000)}
      />,
    );
    expect(screen.getByText("상승")).toBeInTheDocument();
    expect(screen.getByText("+1,000만 원 (+25%)")).toBeInTheDocument();
  });

  it("하락이면 마이너스 부호로 차이 금액과 비율을 보인다", () => {
    render(
      <SalaryTrend
        trend={TREND}
        comparison={comparison("DOWN", 50_000_000, 40_000_000)}
      />,
    );
    expect(screen.getByText("하락")).toBeInTheDocument();
    expect(screen.getByText("−1,000만 원 (−20%)")).toBeInTheDocument();
  });

  it("한쪽 표본이 부족하면 그 칸에 표본 부족을, 판정에 판단 보류를 쓴다", () => {
    const base = comparison("INSUFFICIENT_SAMPLE", null, 41_000_000);
    render(
      <SalaryTrend
        trend={TREND}
        comparison={{
          ...base,
          previous: { ...base.previous, disclosedSampleSize: 9 },
        }}
      />,
    );
    expect(screen.getByText("표본 부족")).toBeInTheDocument();
    expect(screen.getByText("4,100만 원")).toBeInTheDocument();
    expect(screen.getByText("공개 9건")).toBeInTheDocument();
    expect(screen.getByText("판단 보류 (표본 부족)")).toBeInTheDocument();
  });

  it("1% 미만 차이도 0% 로 뭉개지 않는다", () => {
    render(
      <SalaryTrend
        trend={TREND}
        comparison={comparison("UP", 50_000_000, 50_200_000)}
      />,
    );
    expect(screen.getByText("+20만 원 (+0.4%)")).toBeInTheDocument();
  });

  it("상승·하락이어도 중앙값이 비면 차이를 숨긴다", () => {
    render(
      <SalaryTrend
        trend={TREND}
        comparison={comparison("UP", null, 50_000_000)}
      />,
    );
    expect(screen.queryByText(/\(\+/)).toBeNull();
  });
});
