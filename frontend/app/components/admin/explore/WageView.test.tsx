import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { WageView } from "./WageView";
import type { WageBaseline, WageTable } from "../../../lib/collectorApi";

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

/** KOSIS 기준선처럼 모든 직무가 같은 표. */
const uniform: WageTable = {
  basePeriod: "2025",
  families: ["DEVELOPER", "DATA", "AI"],
  brackets: ["ENTRY", "MID"],
  cells: ["DEVELOPER", "DATA", "AI"].flatMap((f) => [
    {
      jobFamily: f,
      experienceBracket: "ENTRY",
      sources: ["KOSIS"],
      values: [gross(32_240_000)],
    },
    {
      jobFamily: f,
      experienceBracket: "MID",
      sources: ["KOSIS"],
      values: [gross(47_690_000)],
    },
  ]),
  missing: [],
  engineBaselines: [],
};

describe("WageView", () => {
  it("표 위에 기준 연도와 직무 구분 없음 설명을 보인다", () => {
    render(<WageView data={divergent} />);
    expect(screen.getByText("기준 연도 2025")).toBeInTheDocument();
    expect(
      screen.getByText(
        "KOSIS 연령대 기준선 — 직무 구분 없음. 직무 보정은 원티드 연봉 통계 도입 후",
      ),
    ).toBeInTheDocument();
  });

  it("세전 총액 중위값·정액급여 중위값·출처를 보인다", () => {
    render(<WageView data={divergent} />);
    const cell = screen.getByTestId("wage-DEVELOPER-MID");
    expect(cell).toHaveTextContent("6,123만원");
    expect(cell).toHaveTextContent("정액 5,113만원");
    expect(cell).toHaveTextContent("KOSIS 202508");
    expect(cell).toHaveAttribute("data-engine-differs", "false");
  });

  it("엔진 기준선이 최신 연도 값과 다르면 그 값을 보이고 강조한다", () => {
    render(<WageView data={divergent} />);
    const cell = screen.getByTestId("wage-DATA-MID");
    expect(cell).toHaveTextContent("엔진 기준선 4,800만원 (2024 KOSIS)");
    expect(cell).toHaveAttribute("data-engine-differs", "true");
    expect(cell).toHaveAttribute("data-missing", "true");
  });

  it("직무와 경력 구간을 한글로, 구간에는 근거 연령대를 붙여 보인다", () => {
    render(<WageView data={divergent} />);
    expect(screen.getByRole("rowheader", { name: "개발" })).toBeInTheDocument();
    expect(
      screen.getByRole("columnheader", { name: "미드 30~34세" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("DEVELOPER")).not.toBeInTheDocument();
  });

  it("모든 직무가 같으면 「전 직무 공통」 한 줄로 접는다", () => {
    render(<WageView data={uniform} />);
    expect(screen.getAllByRole("row")).toHaveLength(2);
    expect(screen.getByText("전 직무 공통")).toBeInTheDocument();
    expect(screen.getByText("개발, 데이터, AI")).toBeInTheDocument();
    expect(screen.getByTestId("wage-ALL-MID")).toHaveTextContent("4,769만원");
  });

  it("직무마다 값이 갈리면 접지 않는다", () => {
    render(<WageView data={divergent} />);
    expect(screen.queryByText("전 직무 공통")).not.toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(3);
  });

  it("데이터가 없으면 안내한다", () => {
    render(
      <WageView
        data={{
          basePeriod: null,
          families: [],
          brackets: [],
          cells: [],
          missing: [],
          engineBaselines: [],
        }}
      />,
    );
    expect(
      screen.getByText("해당 조건의 데이터가 없습니다"),
    ).toBeInTheDocument();
  });
});
