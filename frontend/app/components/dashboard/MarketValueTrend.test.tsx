import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import type { SnapshotPoint } from "../../lib/dashboardMarketApi";
import { MarketValueTrend } from "./MarketValueTrend";

function point(
  baseYearMonth: string,
  marketValueIndex: number | null,
): SnapshotPoint {
  return {
    baseYearMonth,
    purchasingPowerIndex: null,
    marketValueIndex,
    decisionSignal: "STAY",
    label: "",
    dataVersion: "",
    ruleVersion: "",
    rationale: null,
    recorded: null,
    changed: false,
  };
}

// 최신 달이 앞에 온다 (/v1/dashboard/history 순서)
const HISTORY = [
  point("202610", 104.6),
  point("202609", 104.6),
  point("202608", 104.6),
  point("202607", 105.2),
  point("202606", 105.2),
  point("202605", null),
  point("202604", null),
  point("202603", 100),
];

function renderTrend(history: SnapshotPoint[]) {
  return render(
    <MemoryRouter>
      <MarketValueTrend history={history} />
    </MemoryRouter>,
  );
}

describe("MarketValueTrend", () => {
  it("창의 달 수와 시작 → 끝 값을 숫자로 보인다", () => {
    renderTrend(HISTORY);

    expect(screen.getByText("최근 8개월")).toBeInTheDocument();
    expect(screen.getByText("100.0 → 104.6")).toBeInTheDocument();
  });

  it("값 없는 달이 있으면 개수와 이유를 적는다", () => {
    renderTrend(HISTORY);

    expect(
      screen.getByText("값 없는 달 2개월 (수집 공백·표본 부족)"),
    ).toBeInTheDocument();
  });

  it("값 없는 달이 없으면 그 줄이 없다", () => {
    renderTrend([point("202610", 104.6), point("202609", 103)]);

    expect(screen.queryByText(/값 없는 달/)).not.toBeInTheDocument();
  });

  it("스크린리더용 요약 문장을 둔다", () => {
    renderTrend(HISTORY);

    expect(
      screen.getByText(
        "최근 8개월 시장가치 지수가 100.0에서 104.6까지 변했습니다. 값이 없는 달 2개월.",
      ),
    ).toHaveClass("sr-only");
  });

  it("지수 기록으로 링크한다", () => {
    renderTrend(HISTORY);

    expect(screen.getByRole("link", { name: "지수 기록 →" })).toHaveAttribute(
      "href",
      "/dashboard/history",
    );
  });

  it("값 있는 달이 2개 미만이면 그리지 않는다", () => {
    const { container } = renderTrend([
      point("202610", 104.6),
      point("202609", null),
    ]);

    expect(container).toBeEmptyDOMElement();
  });
});
