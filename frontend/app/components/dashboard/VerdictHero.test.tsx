import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { SnapshotPoint } from "../../lib/dashboardMarketApi";
import { VerdictHero } from "./VerdictHero";

const STAY = {
  code: "STAY",
  label: "잔류",
  rulesApplied: ["연봉이 시장 수준에 근접", "스킬 수요 안정"],
  rationale: "공식 임금통계 중앙값과 비슷한 수준입니다.",
};

const SEEK_JOB = {
  code: "SEEK_JOB",
  label: "이직 탐색",
  rulesApplied: ["연봉 격차가 큼", "스킬 수요 상승"],
  rationale: "연봉 격차가 크고 스킬 수요도 올라 있습니다.",
};

function point(
  baseYearMonth: string,
  decisionSignal: string,
  marketValueIndex: number | null,
): SnapshotPoint {
  return {
    baseYearMonth,
    purchasingPowerIndex: null,
    marketValueIndex,
    decisionSignal,
    label: "",
    dataVersion: "",
    ruleVersion: "",
    rationale: null,
  };
}

const HISTORY = [
  point("202608", "STAY", 104.6),
  point("202607", "STAY", 103.1),
  point("202606", "STAY", null),
  point("202605", "NEGOTIATE", 101.0),
];

describe("VerdictHero", () => {
  it("프런트 description 이 h2 제목이고 label 은 상태 뱃지다", () => {
    render(<VerdictHero signal={STAY} history={[]} isNarrow={false} />);
    expect(
      screen.getByRole("heading", {
        level: 2,
        name: "지표가 시장 수준 안에 있습니다. 당장 움직일 이유가 없습니다.",
      }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("signal-badge")).toHaveTextContent(
      "현 상태 유지",
    );
  });

  it("적용 규칙을 칩으로 보인다", () => {
    render(<VerdictHero signal={STAY} history={[]} isNarrow={false} />);
    const rules = screen.getByRole("list", { name: "적용 규칙" });
    expect(within(rules).getAllByRole("listitem")).toHaveLength(2);
  });

  it("서버 rationale 은 1층에 두지 않는다", () => {
    render(<VerdictHero signal={STAY} history={[]} isNarrow={false} />);
    expect(screen.queryByText(STAY.rationale)).not.toBeInTheDocument();
  });

  it("같은 신호가 이어진 개월 수를 보인다", () => {
    render(<VerdictHero signal={STAY} history={HISTORY} isNarrow={false} />);
    expect(screen.getByText("3개월째")).toBeInTheDocument();
  });

  it("이력이 없으면 연속 개월 줄이 없다", () => {
    render(<VerdictHero signal={STAY} history={[]} isNarrow={false} />);
    expect(screen.queryByText(/개월째/)).not.toBeInTheDocument();
  });

  it("이력을 못 받았으면 프리뷰 전체가 없다", () => {
    render(<VerdictHero signal={STAY} history={null} isNarrow={false} />);
    expect(screen.queryByText("이 판정이 이어진 기간")).not.toBeInTheDocument();
  });

  it("스파크라인은 값이 없는 달에서 끊긴다", () => {
    const { container } = render(
      <VerdictHero signal={STAY} history={HISTORY} isNarrow={false} />,
    );
    // 101.0 | null | 103.1 → 104.6 : 점 하나 + 선 하나
    // 컨테이너 전체가 아니라 스파크라인 svg 안에서만 센다 — 결정 신호 도움말 아이콘
    // (lucide HelpCircle)도 <circle> 을 그려서 컨테이너 전체를 세면 겹친다.
    const sparkline = container.querySelector("svg[data-testid='sparkline']");
    expect(sparkline).not.toBeNull();
    expect(sparkline!.querySelectorAll("polyline")).toHaveLength(1);
    expect(sparkline!.querySelectorAll("circle")).toHaveLength(1);
    expect(screen.getByText("시장가치 3개월")).toBeInTheDocument();
  });

  it("모바일 폭에서는 스파크라인을 빼고 신호 점만 남긴다", () => {
    const { container } = render(
      <VerdictHero signal={STAY} history={HISTORY} isNarrow={true} />,
    );
    expect(container.querySelector("svg[data-testid='sparkline']")).toBeNull();
    expect(screen.getByText("3개월째")).toBeInTheDocument();
  });

  it("경고 신호(이직 탐색)의 연속 개월 점은 경고 톤이지 긍정 톤이 아니다", () => {
    const seekJobHistory = [point("202608", "SEEK_JOB", 104.6)];
    render(
      <VerdictHero
        signal={SEEK_JOB}
        history={seekJobHistory}
        isNarrow={false}
      />,
    );
    const dots = screen.getAllByTestId("streak-dot");
    expect(dots.length).toBeGreaterThan(0);
    for (const dot of dots) {
      expect(dot).toHaveClass("bg-carix-warning");
      expect(dot).not.toHaveClass("bg-carix-positive");
    }
  });

  it("판정이 없어도 h2 가 있다", () => {
    render(<VerdictHero signal={null} history={[]} isNarrow={false} />);
    expect(
      screen.getByRole("heading", { level: 2, name: "판정 대기" }),
    ).toBeInTheDocument();
  });

  it("모르는 코드면 서버 label 을 제목으로 쓴다", () => {
    render(
      <VerdictHero
        signal={{ ...STAY, code: "NEW_CODE", label: "새 신호" }}
        history={[]}
        isNarrow={false}
      />,
    );
    expect(
      screen.getByRole("heading", { level: 2, name: "새 신호" }),
    ).toBeInTheDocument();
  });
});
