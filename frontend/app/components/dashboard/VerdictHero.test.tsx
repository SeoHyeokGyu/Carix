import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

  it("적용 규칙을 칩으로 보인다", () => {
    renderHero(<VerdictHero signal={STAY} history={[]} />);
    const rules = screen.getByRole("list", { name: "적용 규칙" });
    expect(within(rules).getAllByRole("listitem")).toHaveLength(2);
  });

  it("서버 rationale 은 1층에 두지 않는다", () => {
    renderHero(<VerdictHero signal={STAY} history={[]} />);
    expect(screen.queryByText(STAY.rationale)).not.toBeInTheDocument();
  });

  it("같은 신호가 이어진 개월 수를 보인다", () => {
    renderHero(<VerdictHero signal={STAY} history={HISTORY} />);
    expect(screen.getByText("3개월째")).toBeInTheDocument();
  });

  it("판정이 이어진 기간 옆에 지난 판정으로 가는 링크를 둔다", () => {
    renderHero(<VerdictHero signal={STAY} history={HISTORY} />);
    expect(
      screen.getByRole("link", { name: "지난 판정 보기" }),
    ).toHaveAttribute("href", "/dashboard/history");
  });

  it("이력이 없으면 연속 개월 줄이 없다", () => {
    renderHero(<VerdictHero signal={STAY} history={[]} />);
    expect(screen.queryByText(/개월째/)).not.toBeInTheDocument();
  });

  it("이력을 못 받았으면 프리뷰 전체가 없다", () => {
    renderHero(<VerdictHero signal={STAY} history={null} />);
    expect(screen.queryByText("이 판정이 이어진 기간")).not.toBeInTheDocument();
  });

  // 시장가치 추세는 판정의 근거라 2층 시장가치 카드(MarketValueTrend)가 갖는다.
  it("판정 히어로에는 시장가치 추세 그래프가 없다", () => {
    const { container } = renderHero(
      <VerdictHero signal={STAY} history={HISTORY} />,
    );
    expect(container.querySelector("svg[data-testid='sparkline']")).toBeNull();
    expect(screen.queryByText(/시장가치 \d+개월/)).not.toBeInTheDocument();
    expect(screen.getByText("3개월째")).toBeInTheDocument();
  });

  it("경고 신호(이직 탐색)의 연속 개월 점은 경고 톤이지 긍정 톤이 아니다", () => {
    const seekJobHistory = [point("202608", "SEEK_JOB", 104.6)];
    renderHero(<VerdictHero signal={SEEK_JOB} history={seekJobHistory} />);
    const dots = screen.getAllByTestId("streak-dot");
    expect(dots.length).toBeGreaterThan(0);
    for (const dot of dots) {
      expect(dot).toHaveClass("bg-carix-warning");
      expect(dot).not.toHaveClass("bg-carix-positive");
    }
  });

  it("판정이 없어도 판정 문장이 있다", () => {
    renderHero(<VerdictHero signal={null} history={[]} />);
    expect(screen.getByTestId("verdict-statement")).toHaveTextContent(
      "판정 대기",
    );
  });

  it("모르는 코드면 서버 label 을 제목으로 쓴다", () => {
    render(
      <VerdictHero
        signal={{ ...STAY, code: "NEW_CODE", label: "새 신호" }}
        history={[]}
      />,
    );
    expect(screen.getByTestId("verdict-statement")).toHaveTextContent(
      "새 신호",
    );
  });

  describe("처방 및 추천 블록 (US-D6 확장)", () => {
    const UPSKILL = {
      code: "UPSKILL",
      label: "스킬 보강 우선",
      rulesApplied: ["MVI < 95", "PPI < 95"],
      rationale: "시장가치와 구매력이 모두 낮습니다.",
    };

    const NEGOTIATE = {
      code: "NEGOTIATE",
      label: "협상 검토",
      rulesApplied: ["MVI > 105"],
      rationale: "시장가치가 높습니다.",
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

    it("UPSKILL 신호에서 처방 타이틀과 추천 스킬 목록을 노출한다", () => {
      render(
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
    });

    it("UPSKILL 신호에서 추천 스킬이 비어있으면 표본 부족을 노출한다", () => {
      render(
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

    it("STAY 신호에서도 추천 스킬이 있으면 성장 가이드로 노출한다", () => {
      render(
        <VerdictHero
          signal={STAY}
          history={[]}

          recommendations={RECOMMENDATIONS}
        />,
      );

      const block = screen.getByTestId("skill-prescription-block");
      expect(block).toBeInTheDocument();
      expect(screen.getByText("성장 가이드")).toBeInTheDocument();
      expect(
        screen.getByText("동료집단 수요 기반 추천 스킬"),
      ).toBeInTheDocument();
      expect(screen.getByText("Kubernetes")).toBeInTheDocument();
    });

    it("SEEK_JOB 및 NEGOTIATE 신호에서 상황에 맞는 타이틀로 노출한다", () => {
      const { rerender } = render(
        <VerdictHero
          signal={SEEK_JOB}
          history={[]}

          recommendations={RECOMMENDATIONS}
        />,
      );
      expect(screen.getByText("이직 대비")).toBeInTheDocument();
      expect(
        screen.getByText("동료집단 공고에서 자주 요구하는 스킬"),
      ).toBeInTheDocument();

      rerender(
        <VerdictHero
          signal={NEGOTIATE}
          history={[]}

          recommendations={RECOMMENDATIONS}
        />,
      );
      expect(screen.getByText("시장 수요")).toBeInTheDocument();
      expect(
        screen.getByText("동료집단 공고에서 자주 요구하는 스킬"),
      ).toBeInTheDocument();
    });

    it("STAY 신호에서 추천 스킬이 비어있으면 블록 자체를 숨긴다 (불필요한 표본 부족 경고 방지)", () => {
      render(
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

    it("추천 스킬 접기 및 펼치기 토글이 정상 작동한다", async () => {
      const user = userEvent.setup();
      render(
        <VerdictHero
          signal={STAY}
          history={[]}

          recommendations={RECOMMENDATIONS}
        />,
      );

      expect(
        screen.getByRole("list", { name: "추천 스킬 목록" }),
      ).toBeInTheDocument();

      const toggleButton = screen.getByRole("button", {
        name: "추천 스킬 접기",
      });
      await user.click(toggleButton);

      expect(
        screen.queryByRole("list", { name: "추천 스킬 목록" }),
      ).not.toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "추천 스킬 펼치기" }),
      ).toBeInTheDocument();

      await user.click(
        screen.getByRole("button", { name: "추천 스킬 펼치기" }),
      );
      expect(
        screen.getByRole("list", { name: "추천 스킬 목록" }),
      ).toBeInTheDocument();
    });

    it("추천 스킬이 3개 초과일 때 다른 추천 보기(로테이션) 버튼이 노출되고 클릭 시 다음 페이지 스킬로 전환된다", async () => {
      const user = userEvent.setup();
      const multiRecommendations = {
        peerGroup: { appliedConditions: "서울" },
        recommendations: [
          {
            standardName: "Skill-1",
            category: "DEV",
            demandRate: 0.5,
            growthTrend: "UP",
            coOccurrence: null,
            reason: "수요율 50%",
          },
          {
            standardName: "Skill-2",
            category: "DEV",
            demandRate: 0.4,
            growthTrend: "STABLE",
            coOccurrence: null,
            reason: "수요율 40%",
          },
          {
            standardName: "Skill-3",
            category: "DEV",
            demandRate: 0.3,
            growthTrend: "DOWN",
            coOccurrence: null,
            reason: "수요율 30%",
          },
          {
            standardName: "Skill-4",
            category: "DEV",
            demandRate: 0.2,
            growthTrend: "UP",
            coOccurrence: null,
            reason: "수요율 20%",
          },
        ],
      };

      render(
        <VerdictHero
          signal={STAY}
          history={[]}
          recommendations={multiRecommendations}
        />,
      );

      expect(screen.getByText("Skill-1")).toBeInTheDocument();
      expect(screen.getByText("Skill-2")).toBeInTheDocument();
      expect(screen.getByText("Skill-3")).toBeInTheDocument();
      expect(screen.queryByText("Skill-4")).not.toBeInTheDocument();

      const rotateBtn = screen.getByRole("button", {
        name: "다른 추천 보기 (1/2)",
      });
      expect(rotateBtn).toBeInTheDocument();

      await user.click(rotateBtn);

      expect(screen.getByText("Skill-4")).toBeInTheDocument();
      expect(screen.queryByText("Skill-1")).not.toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "다른 추천 보기 (2/2)" }),
      ).toBeInTheDocument();

      await user.click(
        screen.getByRole("button", { name: "다른 추천 보기 (2/2)" }),
      );
      expect(screen.getByText("Skill-1")).toBeInTheDocument();
    });
  });
});
