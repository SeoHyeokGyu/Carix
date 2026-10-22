import { render, screen, within } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";

import type { SnapshotPoint } from "../../lib/dashboardMarketApi";
import { VerdictHero } from "./VerdictHero";

const renderHero = (ui: ReactElement) =>
  render(<MemoryRouter>{ui}</MemoryRouter>);

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
    recorded: null,
    changed: false,
  };
}

const HISTORY = [
  point("202608", "STAY", 104.6),
  point("202607", "STAY", 103.1),
  point("202606", "STAY", null),
  point("202605", "NEGOTIATE", 101.0),
];

describe("VerdictHero", () => {
  it("이직 탐색 판정이면 시뮬레이터로 가는 링크를 보인다", () => {
    renderHero(<VerdictHero signal={SEEK_JOB} history={null} />);

    expect(
      screen.getByRole("link", {
        name: "옮기면 12개월간 얼마가 달라지는지 계산해 보기",
      }),
    ).toHaveAttribute("href", "/dashboard/simulator");
  });

  it("프런트 description 이 판정 문장이고 label 은 상태 뱃지다", () => {
    renderHero(<VerdictHero signal={STAY} history={[]} />);
    const statement = screen.getByTestId("verdict-statement");
    expect(statement.tagName).toBe("P");
    expect(statement).toHaveTextContent(
      "지표가 시장 수준 안에 있습니다. 당장 움직일 이유가 없습니다.",
    );
    // 문장이 제목이 아니어도 판정 구획의 이름은 그 문장이다.
    expect(
      screen.getByRole("region", { name: /지표가 시장 수준 안에/ }),
    ).toBeInTheDocument();
  });

  it("같은 신호가 이어진 개월 수를 보인다", () => {
    renderHero(<VerdictHero signal={STAY} history={HISTORY} />);
    expect(screen.getByText("3개월째")).toBeInTheDocument();
  });

  describe("처방 및 추천 블록 (US-D6 확장)", () => {
    const UPSKILL = {
      code: "UPSKILL",
      label: "스킬 보강 우선",
      rulesApplied: ["MVI < 95", "PPI < 95"],
      rationale: "시장가치와 구매력이 모두 낮습니다.",
    };

    const RECOMMENDATIONS = {
      peerGroup: { appliedConditions: "서울 전체 / 경력 MID" },
      recommendations: [
        {
          standardName: "Kubernetes",
          category: "DEVOPS",
          demandRate: 0.245,
          growthTrend: "UP",
          coOccurrence: { withSkill: "Spring Boot", rate: 0.62 },
          reason: "보유하신 Spring Boot 공고의 62%가 함께 요구합니다.",
        },
        {
          standardName: "Docker",
          category: "DEVOPS",
          demandRate: 0.18,
          growthTrend: "STABLE",
          coOccurrence: null,
          reason: "동료집단 내 공고의 18.0%에서 요구되는 핵심 스택입니다.",
        },
      ],
    };

    it("UPSKILL 신호에서 처방 타이틀과 추천 스킬 목록, 스킬 분석 이동 링크를 노출한다", () => {
      renderHero(
        <VerdictHero
          signal={UPSKILL}
          history={[]}
          recommendations={RECOMMENDATIONS}
        />,
      );

      const block = screen.getByTestId("skill-prescription-block");
      expect(block).toBeInTheDocument();
      expect(screen.getByText("처방")).toBeInTheDocument();
      expect(
        screen.getByText("시장가치 개선을 위한 우선 보강 스킬"),
      ).toBeInTheDocument();
      expect(screen.getByText("Kubernetes")).toBeInTheDocument();
      expect(screen.getByText("수요율 24.5%")).toBeInTheDocument();
      expect(screen.getByText("▲ 상승")).toBeInTheDocument();
      expect(
        screen.getByText("보유하신 Spring Boot 공고의 62%가 함께 요구합니다."),
      ).toBeInTheDocument();

      expect(screen.getByText("Docker")).toBeInTheDocument();
      expect(screen.getByText("수요율 18.0%")).toBeInTheDocument();
      expect(screen.getByText("- 유지")).toBeInTheDocument();
      expect(
        within(block).getByRole("link", {
          name: "스킬 분석 전체 보기",
        }),
      ).toHaveAttribute("href", "/dashboard/skills");
    });

    it("UPSKILL 신호에서 추천 스킬이 비어있으면 표본 부족을 노출한다", () => {
      renderHero(
        <VerdictHero
          signal={UPSKILL}
          history={[]}
          recommendations={{
            peerGroup: { appliedConditions: "" },
            recommendations: [],
          }}
        />,
      );

      const block = screen.getByTestId("skill-prescription-block");
      expect(block).toBeInTheDocument();
      expect(screen.getByText("표본 부족")).toBeInTheDocument();
      expect(
        screen.getByText(
          "동료집단 공고 표본이 부족하여 추천 스킬을 산출할 수 없습니다.",
        ),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("list", { name: "추천 스킬 목록" }),
      ).not.toBeInTheDocument();
    });

    it("STAY 신호에서 추천 스킬이 비어있으면 블록 자체를 숨긴다 (불필요한 표본 부족 경고 방지)", () => {
      renderHero(
        <VerdictHero
          signal={STAY}
          history={[]}
          recommendations={{
            peerGroup: { appliedConditions: "" },
            recommendations: [],
          }}
        />,
      );

      expect(
        screen.queryByTestId("skill-prescription-block"),
      ).not.toBeInTheDocument();
    });
  });
});
