import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";

import type { MonthlyOverview } from "../../../lib/collectorApi";
import { MonthlyOverviewTable } from "./MonthlyOverviewTable";

const data: MonthlyOverview = {
  from: "202609",
  to: "202610",
  months: [
    {
      month: "202609",
      jobkoreaSampled: 250,
      jobkoreaSampledCapped: 198,
      jobkoreaCrawled: 1111,
      saraminCrawled: 2222,
      sampledByFamily: { DEVELOPER: 50, DATA: 7, AI: 50 },
      wantedSampled: 301,
      wantedSampledCapped: 302,
      wantedCrawled: 3333,
      current: false,
    },
    {
      month: "202610",
      jobkoreaSampled: 0,
      jobkoreaSampledCapped: 0,
      jobkoreaCrawled: 4444,
      saraminCrawled: 5555,
      sampledByFamily: { DEVELOPER: 0, DATA: 0, AI: 0 },
      wantedSampled: 0,
      wantedSampledCapped: 0,
      wantedCrawled: 6666,
      current: true,
    },
  ],
};

function renderTable(sampleSource?: string) {
  return render(
    <MemoryRouter>
      <MonthlyOverviewTable data={data} sampleSource={sampleSource} />
    </MemoryRouter>,
  );
}

describe("MonthlyOverviewTable", () => {
  it("상한 적용 200 미만만 강조하고 표본 원본은 강조하지 않는다", () => {
    renderTable();
    expect(screen.getByText("198")).toHaveAttribute("data-thin", "true");
    expect(screen.getByText("250")).not.toHaveAttribute("data-thin", "true");
    expect(screen.getByText("302")).toHaveAttribute("data-thin", "false");
    expect(screen.getByText("7")).toHaveAttribute("data-thin", "true");
  });

  it("당월 행은 「수집 중」을 달고 강조하지 않는다", () => {
    renderTable();
    const row = screen.getByRole("link", { name: "202610" }).closest("tr")!;
    expect(within(row).getByText("수집 중")).toBeInTheDocument();
    expect(row.querySelectorAll('td[data-thin="false"]')).toHaveLength(5);
    expect(row.querySelectorAll('td[data-thin="true"]')).toHaveLength(0);
    expect(screen.getAllByText("수집 중")).toHaveLength(1);
  });
});
