import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as incomeApi from "../../lib/api/income";
import type {
  EvaluateRequest,
  EvaluationResponse,
  ScenarioContext,
} from "../../lib/api/scenario";
import * as scenario from "../../lib/api/scenario";
import { UnauthorizedError } from "../../lib/coreApi";
import DashboardSimulator, { clientLoader } from "./simulator";

vi.mock("../../lib/api/scenario", () => ({
  getContext: vi.fn(),
  getDraft: vi.fn(),
  listScenarios: vi.fn(),
  evaluate: vi.fn(),
  putDraft: vi.fn(),
  saveScenario: vi.fn(),
  getFeasibility: vi.fn(),
}));
vi.mock("../../lib/api/income", () => ({
  listIncome: vi.fn(),
  createBonus: vi.fn(),
  updateBonusTerms: vi.fn(),
}));

const inflation = {
  governmentPercent: 2.3,
  personalPercent: 2.8,
  gapPp: 0.5,
  topGapCategory: null,
  similar: false,
  asOf: "2026-09",
  source: "KOSTAT",
};

const context: ScenarioContext = {
  currentAnnualGross: 60_000_000,
  monthlyNet: 4_000_000,
  hireDate: "2022-03-01",
  bonusIncluded: true,
  fixedBonusAnnual: null,
  defaultRaisePercent: 3,
  performanceBonusRange: null,
  inflation,
  marketGapPercent: null,
};

const draft: EvaluateRequest = {
  stay: { raisePercent: 3 },
  move: {
    grossSalary: 70_000_000,
    unemploymentMonths: 1,
    oneTimeCost: 0,
    benefitEligible: false,
    bonusIncluded: true,
  },
};

const evaluation: EvaluationResponse = {
  stay: {
    realChangePercent: 0.2,
    realChangeGovernmentPercent: 0.7,
    breakevenPersonalPercent: 2.8,
    breakevenGovernmentPercent: 2.3,
    marketGapPercent: null,
  },
  move: {
    stayCumulative: [1, 2, 3],
    moveCumulative: [0, 3, 6],
    crossoverMonth: 2,
    firstYear: {
      salaryDiff: 3_000_000,
      gap: -4_000_000,
      oneTimeCost: 0,
      benefit: 0,
      total: -1_000_000,
    },
    expectedRange: { min: 0, max: 0 },
    stock: null,
    equalizingRaisePercent: 6.1,
    feasibility: {
      status: "INSUFFICIENT_SAMPLE",
      sampleSize: 3,
      minRequired: 10,
      feasibilityScore: null,
      targetPercentile: null,
      difficultyGrade: null,
      feedbackMessage: null,
    },
  },
  inflation,
  assumptions: [],
  unreflected: [],
  severance: null,
  typeLabel: "추정 기회비용",
};

function renderRoute() {
  const Stub = createRoutesStub([
    {
      path: "/dashboard/simulator",
      Component: DashboardSimulator,
      loader: clientLoader,
      HydrateFallback: () => null,
    },
    { path: "/login", Component: () => <div>로그인 화면</div> },
  ]);
  return render(<Stub initialEntries={["/dashboard/simulator"]} />);
}

describe("이직 시뮬레이터 라우트", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(scenario.getContext).mockResolvedValue(context);
    vi.mocked(scenario.getDraft).mockResolvedValue(null);
    vi.mocked(scenario.listScenarios).mockResolvedValue([]);
    vi.mocked(scenario.putDraft).mockResolvedValue(null);
    vi.mocked(incomeApi.listIncome).mockResolvedValue([]);
  });

  it("초안이 없으면 현 직장 단계로 시작한다", async () => {
    renderRoute();

    expect(
      await screen.findByRole("button", {
        name: "남으면 어떻게 되는지 보기",
      }),
    ).toBeInTheDocument();
  });

  it("컨텍스트 조회가 401 이면 로그인으로 보낸다", async () => {
    vi.mocked(scenario.getContext).mockRejectedValue(new UnauthorizedError());

    renderRoute();

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });

  it("평가 API 가 실패하면 결과 영역에 오류가 보인다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    vi.mocked(scenario.evaluate).mockRejectedValue(new Error("평가 서버 오류"));

    renderRoute();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "요청을 처리하지 못했습니다",
    );
  });

  it("시나리오 저장 버튼을 누르기 전에는 저장하지 않는다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    vi.mocked(scenario.evaluate).mockResolvedValue(evaluation);
    vi.mocked(scenario.saveScenario).mockResolvedValue({
      id: "s1",
      name: "A사",
      engineVersion: 2,
      calculatedAt: "2026-10-22T00:00:00Z",
      request: draft,
      result: evaluation,
      legacy: null,
    });

    renderRoute();
    const save = await screen.findByRole("button", { name: "시나리오 저장" });
    expect(scenario.saveScenario).not.toHaveBeenCalled();

    await userEvent.setup().click(save);

    expect(scenario.saveScenario).toHaveBeenCalledWith(null, draft);
  });
  it("소득 이력이 있으면 저장분을 채택할 때 옛 이번만 바꾸기 연봉을 비운다", async () => {
    const saved: EvaluateRequest = {
      ...draft,
      stay: { raisePercent: 3, currentGrossOverride: 80_000_000 },
    };
    vi.mocked(scenario.listScenarios).mockResolvedValue([
      {
        id: "s1",
        name: null,
        engineVersion: 2,
        calculatedAt: "2026-10-22T00:00:00Z",
        request: saved,
        result: evaluation,
        legacy: null,
      },
    ]);
    vi.mocked(scenario.evaluate).mockResolvedValue(evaluation);

    renderRoute();
    await screen.findByRole("button", { name: "시나리오 저장" });

    expect(scenario.evaluate).toHaveBeenCalledWith(
      expect.objectContaining({
        stay: expect.objectContaining({ currentGrossOverride: null }),
      }),
    );
  });
});
