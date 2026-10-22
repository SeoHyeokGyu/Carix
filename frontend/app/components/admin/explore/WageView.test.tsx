import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { WageBaseline, WageTable } from "../../../lib/collectorApi";
import { WageView } from "./WageView";

const gross = (wageMedian: number, statVintage = "202508") => ({
  wageDefinition: "ANNUAL_GROSS_MEDIAN",
  source: "KOSIS",
  statVintage,
  wageMedian,
});

const base = (wageMedian: number) => ({
  ...gross(wageMedian),
  wageDefinition: "ANNUAL_BASE_MEDIAN",
});

const baseline = (
  jobFamily: string,
  overrides: Partial<WageBaseline> = {},
): WageBaseline => ({
  jobFamily,
  experienceBracket: "MID",
  region: "ALL",
  basePeriod: "2025",
  wageDefinition: "ANNUAL_GROSS_MEDIAN",
  source: "KOSIS",
  statVintage: "202508",
  wageMedian: 61_234_567,
  ...overrides,
});

/** 직무마다 값이 갈리는 표 — DATA/MID 가 비고 엔진 기준선도 다르다. */
const divergent: WageTable = {
  basePeriod: "2025",
  families: ["DEVELOPER", "DATA"],
  brackets: ["MID"],
  cells: [
    {
      jobFamily: "DEVELOPER",
      experienceBracket: "MID",
      sources: ["KOSIS"],
      values: [gross(61_234_567), base(51_130_000)],
    },
  ],
  missing: ["DATA/MID"],
  engineBaselines: [
    baseline("DEVELOPER"),
    baseline("DATA", {
      basePeriod: "2024",
      statVintage: "202408",
      wageMedian: 48_000_000,
    }),
  ],
};

describe("WageView", () => {
  it("엔진 기준선이 최신 연도 값과 다르면 그 값을 보이고 강조한다", () => {
    render(<WageView data={divergent} />);
    const cell = screen.getByTestId("wage-DATA-MID");
    expect(cell).toHaveTextContent("엔진 기준선 4,800만원 (2024 KOSIS)");
    expect(cell).toHaveAttribute("data-engine-differs", "true");
    expect(cell).toHaveAttribute("data-missing", "true");
  });
});
