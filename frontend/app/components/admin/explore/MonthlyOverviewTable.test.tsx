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
  it("머리글이 원천별 1단과 열별 2단으로 나뉘고 기타 열은 없다", () => {
    renderTable();
    for (const name of [
      "잡코리아",
      "사람인",
      "원티드",
      "직무별 표본 (잡코리아)",
      "표본",
      "상한 적용",
      "정기",
      "개발",
      "데이터",
      "AI",
    ]) {
      expect(
        screen.getAllByRole("columnheader", { name }).length,
      ).toBeGreaterThan(0);
    }
    expect(screen.queryByText("OTHER")).not.toBeInTheDocument();
    expect(screen.queryByText("기타")).not.toBeInTheDocument();
  });

  it("2단 머리글이 묶음 순서대로 나열된다", () => {
    renderTable();
    const row = document.querySelectorAll("thead tr")[1];
    expect(
      Array.from(row.querySelectorAll("th")).map((th) => th.textContent),
    ).toEqual([
      "표본",
      "상한 적용",
      "정기",
      "정기",
      "표본",
      "상한 적용",
      "정기",
      "개발",
      "데이터",
      "AI",
    ]);
  });

  it("직무별 묶음 머리글은 선택한 원천 이름을 따른다", () => {
    renderTable("WANTED");
    expect(
      screen.getByRole("columnheader", { name: "직무별 표본 (원티드)" }),
    ).toBeInTheDocument();
  });

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

  it("범례와 월 링크 툴팁을 보인다", () => {
    renderTable();
    expect(
      screen.getByText(/주황 = 상한 적용 표본 200 미만 또는 직무 표본 10 미만/),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "202609" })).toHaveAttribute(
      "title",
      "이 달 공고 보기",
    );
  });
});
