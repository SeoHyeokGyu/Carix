import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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

const stayOnly: EvaluateRequest = { stay: { raisePercent: 3 }, move: null };

function savedScenario(request: EvaluateRequest, name: string | null) {
  return {
    id: "s1",
    name,
    engineVersion: 2 as const,
    calculatedAt: "2026-10-22T00:00:00Z",
    request,
    result: evaluation,
    legacy: null,
  };
}

describe("이직 시뮬레이터 라우트", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.scrollTo = vi.fn();
    vi.mocked(scenario.getContext).mockResolvedValue(context);
    vi.mocked(scenario.getDraft).mockResolvedValue(null);
    vi.mocked(scenario.listScenarios).mockResolvedValue([]);
    vi.mocked(scenario.putDraft).mockResolvedValue(null);
    vi.mocked(scenario.evaluate).mockResolvedValue(evaluation);
    vi.mocked(incomeApi.listIncome).mockResolvedValue([]);
  });
  afterEach(() => {
    vi.useRealTimers();
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

  // 스펙 「빌더 점검 후 정한 것」 4 — 들어오기만 해도 서버 초안이 써지면 다른 기기의 초안을 덮는다.
  it("처음 들어오면 기본 인상률로 평가만 하고 서버 초안은 쓰지 않는다", async () => {
    renderRoute();

    await waitFor(() =>
      expect(scenario.evaluate).toHaveBeenCalledWith(
        expect.objectContaining({
          stay: expect.objectContaining({ raisePercent: 3 }),
        }),
      ),
    );
    expect(scenario.putDraft).not.toHaveBeenCalled();
  });

  // 초안 저장은 입력 컴포넌트에서 라우트로 옮겨 왔다(옛 CurrentJobStep 테스트의 새 위치).
  it("초안 저장이 실패하면 오류를 보인다", async () => {
    vi.mocked(scenario.putDraft).mockRejectedValue(new Error("저장 서버 오류"));

    renderRoute();
    await userEvent
      .setup()
      .click(
        await screen.findByRole("button", { name: "새 회사와 비교하기 →" }),
      );

    expect(
      await screen.findByText(/초안을 저장하지 못했습니다/),
    ).toBeInTheDocument();
  });

  it("소득 이력이 있으면 저장분을 채택할 때 옛 이번만 바꾸기 연봉을 비운다", async () => {
    const saved: EvaluateRequest = {
      ...draft,
      stay: { raisePercent: 3, currentGrossOverride: 80_000_000 },
    };
    vi.mocked(scenario.listScenarios).mockResolvedValue([
      savedScenario(saved, null),
    ]);

    renderRoute();

    await waitFor(() =>
      expect(scenario.evaluate).toHaveBeenCalledWith(
        expect.objectContaining({
          stay: expect.objectContaining({ currentGrossOverride: null }),
        }),
      ),
    );
    expect(scenario.evaluate).not.toHaveBeenCalledWith(
      expect.objectContaining({
        stay: expect.objectContaining({ currentGrossOverride: 80_000_000 }),
      }),
    );
  });

  // 불러오기가 입력 중이던 초안을 덮어도 되돌릴 수 있어야 한다(스펙 「실패로 보는 것」).
  it("불러온 뒤 되돌리면 직전 계산 요청을 서버 초안에 다시 저장한다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(stayOnly);
    // 메뉴로 불러올 때도 옛 이번만 바꾸기 연봉을 비워야 한다 — 소득 이력이 있으면 그 연봉이 기준이다.
    vi.mocked(scenario.listScenarios).mockResolvedValue([
      savedScenario(
        {
          ...draft,
          stay: { raisePercent: 3, currentGrossOverride: 80_000_000 },
        },
        "B사",
      ),
    ]);
    const user = userEvent.setup();

    renderRoute();
    await user.click(
      await screen.findByRole("button", { name: "저장된 시나리오 1" }),
    );
    await user.click(await screen.findByRole("button", { name: "불러오기" }));
    await waitFor(() =>
      expect(scenario.putDraft).toHaveBeenLastCalledWith(
        expect.objectContaining({
          move: draft.move,
          stay: expect.objectContaining({ currentGrossOverride: null }),
        }),
      ),
    );

    await user.click(
      screen.getByRole("button", { name: "이전 입력으로 되돌리기" }),
    );

    await waitFor(() =>
      expect(scenario.putDraft).toHaveBeenLastCalledWith(stayOnly),
    );
  });

  // 스펙 「실패로 보는 것」 1 — 상여 금액은 칸을 벗어날 때 저장된다. 그 전에 결과가 새 금액을 반영한 것처럼 보이면 안 된다.
  it("저장 전 따로 받는 상여 금액은 결과를 흐리게 두고, 저장된 뒤 다시 계산해 선명하게 한다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(scenario.getContext).mockResolvedValue({
      ...context,
      bonusIncluded: false,
      fixedBonusAnnual: 5_000_000,
    });
    vi.mocked(scenario.getDraft).mockResolvedValue(stayOnly);
    vi.mocked(incomeApi.listIncome).mockResolvedValue([
      {
        id: "i-1",
        grossSalary: 60_000_000,
        netSalary: 48_000_000,
        effectiveDate: "2026-01-01",
        changeReason: null,
        selectedChangeReason: null,
        bonusIncluded: false,
        fixedBonusAnnual: 5_000_000,
      },
    ]);
    vi.mocked(incomeApi.updateBonusTerms).mockResolvedValue([]);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    const { container } = renderRoute();
    const amount = await screen.findByLabelText(/연간 고정상여/);
    const resultArea = () => container.querySelector("[aria-busy]");
    await waitFor(() =>
      expect(resultArea()).toHaveAttribute("aria-busy", "false"),
    );

    await user.clear(amount);
    await user.type(amount, "600");
    act(() => {
      vi.advanceTimersByTime(400);
    });

    expect(resultArea()).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText(/소득 기록에 저장됨/)).not.toBeInTheDocument();

    await user.tab();

    expect(incomeApi.updateBonusTerms).toHaveBeenCalledWith({
      applyFromIncomeId: "i-1",
      bonusIncluded: false,
      fixedBonusAnnual: 6_000_000,
    });
    await waitFor(() =>
      expect(resultArea()).toHaveAttribute("aria-busy", "false"),
    );
  });
});
