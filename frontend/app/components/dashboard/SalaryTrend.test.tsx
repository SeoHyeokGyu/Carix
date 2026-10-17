import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    expect(screen.getByText("판단 보류 (표본 부족)")).toBeInTheDocument();
  });

  it("집계 중인 달이 없으면 안내 줄을 두지 않는다", () => {
    render(
      <SalaryTrend
        trend={TREND}
        comparison={comparison("FLAT", 50_000_000, 41_000_000, {
          inProgress: [],
        })}
      />,
    );
    expect(screen.queryByText(/집계 중/)).not.toBeInTheDocument();
  });

  it("집계 중인 달이 둘이면 가운뎃점으로 잇는다", () => {
    render(
      <SalaryTrend
        trend={TREND}
        comparison={comparison("FLAT", 50_000_000, 41_000_000, {
          inProgress: ["2026-10", "2026-11"],
        })}
      />,
    );
    expect(
      screen.getByText("2026-10·2026-11 은 집계 중이라 비교에서 제외"),
    ).toBeInTheDocument();
  });

  it("연도를 넘는 기간은 끝 연도를 함께 쓴다", () => {
    const base = comparison("FLAT", 50_000_000, 41_000_000);
    render(
      <SalaryTrend
        trend={TREND}
        comparison={{
          ...base,
          previous: { ...base.previous, from: "2026-12", to: "2027-02" },
        }}
      />,
    );
    expect(screen.getByText("2026-12 ~ 2027-02")).toBeInTheDocument();
  });

  it("비교가 없으면 판정 문구 없이 월별 보기 버튼만 둔다", () => {
    render(<SalaryTrend trend={TREND} comparison={null} />);
    expect(screen.queryByText("뚜렷한 변화 없음")).not.toBeInTheDocument();
    expect(screen.queryByText(/판단 보류/)).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "월별 보기" }),
    ).toBeInTheDocument();
  });

  it("스크린리더 요약에 판정과 두 중앙값이 들어간다", () => {
    const { container } = render(
      <SalaryTrend
        trend={TREND}
        comparison={comparison("UP", 40_000_000, 50_000_000)}
      />,
    );
    const summary = container.querySelector("p.sr-only");
    expect(summary).not.toBeNull();
    expect(summary).toHaveTextContent("상승");
    expect(summary).toHaveTextContent("4,000만 원");
    expect(summary).toHaveTextContent("5,000만 원");
  });

  it("월별 표는 접혀 있고 토글로 연다", async () => {
    const user = userEvent.setup();
    render(<SalaryTrend trend={TREND} comparison={null} />);

    const toggle = screen.getByRole("button", { name: "월별 보기" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByText("2026-04")).not.toBeVisible();

    await user.click(toggle);

    expect(screen.getByRole("button", { name: "월별 접기" })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(screen.getByText("2026-04")).toBeVisible();
    expect(screen.getByText("표본 부족 (미산출)")).toBeVisible();
    expect(screen.getByText("38.9%")).toBeVisible();
    expect(screen.getByText(/월별 표는 전체 수집 공고 기준/)).toBeVisible();
  });
});
