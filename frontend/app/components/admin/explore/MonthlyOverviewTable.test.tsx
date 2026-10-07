import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import type { MonthlyOverview } from "../../../lib/collectorApi";
import { MonthlyOverviewTable } from "./MonthlyOverviewTable";

const data: MonthlyOverview = {
  from: "202609",
  to: "202609",
  months: [
    {
      month: "202609",
      jobkoreaSampled: 250,
      jobkoreaSampledCapped: 250,
      jobkoreaCrawled: 0,
      saraminCrawled: 0,
      sampledByFamily: { DEVELOPER: 50, DATA: 50, AI: 50, OTHER: 0 },
      wantedSampled: 199,
      wantedSampledCapped: 199,
      wantedCrawled: 0,
    },
  ],
};

describe("MonthlyOverviewTable", () => {
  it("원티드 표본이 200 미만이면 잡코리아와 같이 강조한다", () => {
    render(
      <MemoryRouter>
        <MonthlyOverviewTable data={data} />
      </MemoryRouter>,
    );
    const cells = screen.getAllByText("199");
    expect(cells.some((c) => c.getAttribute("data-thin") === "true")).toBe(
      true,
    );
    expect(screen.getAllByText("250")[0]).toHaveAttribute("data-thin", "false");
  });
});
