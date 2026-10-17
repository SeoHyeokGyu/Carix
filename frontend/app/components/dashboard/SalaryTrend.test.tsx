import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import type { SalaryTrendPoint } from "../../lib/dashboardMarketApi";
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

describe("SalaryTrend", () => {
  it("스파크라인과 첫·마지막 값, 값 없는 달 수를 보인다", () => {
    render(<SalaryTrend trend={TREND} />);
    expect(screen.getByTestId("sparkline")).toBeInTheDocument();
    expect(screen.getByText("최근 3개월")).toBeInTheDocument();
    expect(screen.getByText("6,000만 원 → 6,200만 원")).toBeInTheDocument();
    expect(
      screen.getByText("값 없는 달 1개월 (공개 연봉 표본 부족)"),
    ).toBeInTheDocument();
  });

  it("월별 표는 접혀 있고 토글로 연다", async () => {
    const user = userEvent.setup();
    render(<SalaryTrend trend={TREND} />);

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
  });

  it("값 있는 달이 둘 미만이면 선 대신 표 토글만 둔다", () => {
    render(<SalaryTrend trend={TREND.slice(0, 2)} />);
    expect(screen.queryByTestId("sparkline")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "월별 보기" }),
    ).toBeInTheDocument();
  });
});
