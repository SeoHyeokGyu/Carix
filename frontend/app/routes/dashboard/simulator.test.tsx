import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { createRoutesStub, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as incomeApi from "../../lib/api/income";
import type {
  EvaluateRequest,
  EvaluationResponse,
  SavedScenario,
  ScenarioContext,
} from "../../lib/api/scenario";
import * as scenario from "../../lib/api/scenario";
import { UnauthorizedError } from "../../lib/coreApi";
import { resetSession, type Session, setSession } from "../../lib/session";
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
  typeLabel: "남으면 추정 이득",
};

function Where() {
  const { pathname, search } = useLocation();
  return <div data-testid="where">{pathname + search}</div>;
}

function renderRoute(entry = "/dashboard/simulator", strict = false) {
  const Stub = createRoutesStub([
    {
      path: "/dashboard/simulator",
      Component: () => (
        <>
          <DashboardSimulator />
          <Where />
        </>
      ),
      loader: clientLoader,
      HydrateFallback: () => null,
    },
    { path: "/login", Component: () => <div>로그인 화면</div> },
    { path: "/register", Component: Where },
  ]);
  const ui = <Stub initialEntries={[entry]} />;
  return render(strict ? <StrictMode>{ui}</StrictMode> : ui);
}

const stayOnly: EvaluateRequest = { stay: { raisePercent: 3 }, move: null };

function savedScenario(
  request: EvaluateRequest,
  name: string | null,
): SavedScenario {
  return {
    id: "s1",
    name,
    engineVersion: 2,
    calculatedAt: "2026-10-22T00:00:00Z",
    request,
    result: evaluation,
    legacy: null,
  };
}

const legacyScenario: SavedScenario = {
  id: "l1",
  name: "옛 계산",
  engineVersion: 1,
  calculatedAt: "2025-05-01T00:00:00Z",
  request: null,
  result: null,
  legacy: {
    inputs: {
      scenarioName: "옛 계산",
      currentGrossSalary: 55_000_000,
      stayExpectedRaiseRate: 4,
      moveTargetGrossSalary: 80_000_000,
      moveOneTimeCost: 0,
      expectedUnemploymentMonths: 1,
      isEligibleForUnemploymentBenefit: false,
    },
    result: {
      id: "r1",
      scenarioName: "옛 계산",
      stayCompensation12m: 0,
      moveCompensation12m: 0,
      estimatedUnemploymentBenefit: 0,
      unemploymentBenefitNote: "",
      difference: 1_000_000,
      type: "ESTIMATED_GAIN",
      typeLabel: "옮기면 추정 이득",
      warningNote: "",
      feasibility: evaluation.move!.feasibility,
    },
  },
};

/** 남을 때 결과·비교 결과가 각각 들어 있는 결과 자리(흐림 표시를 가진 가장 가까운 조상). */
const stayArea = () =>
  screen
    .getByRole("heading", { name: /지금 회사 실질 소득/ })
    .closest("[aria-busy]");
const compareArea = () =>
  screen.getByText("따라잡는 시점").closest("[aria-busy]");

const fakeTimerUser = () => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
};
const debounce = () =>
  act(() => {
    vi.advanceTimersByTime(400);
  });

const member: Session = {
  status: "authenticated",
  userId: "u1",
  email: "a@b.c",
  onboardingStep: "COMPLETED",
  guest: false,
};
const guest: Session = { ...member, email: null, guest: true };

describe("이직 시뮬레이터 라우트", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.scrollTo = vi.fn();
    vi.mocked(scenario.getContext).mockResolvedValue(context);
    vi.mocked(scenario.getDraft).mockResolvedValue(null);
    vi.mocked(scenario.listScenarios).mockResolvedValue([]);
    vi.mocked(scenario.getFeasibility).mockResolvedValue({
      status: "INSUFFICIENT_SAMPLE",
      sampleSize: 0,
      minRequired: 10,
      feasibilityScore: null,
      targetPercentile: null,
      difficultyGrade: null,
      feedbackMessage: null,
    });
    vi.mocked(scenario.putDraft).mockResolvedValue(null);
    vi.mocked(scenario.evaluate).mockResolvedValue(evaluation);
    vi.mocked(incomeApi.listIncome).mockResolvedValue([]);
    setSession(member);
  });
  afterEach(() => {
    vi.useRealTimers();
    resetSession();
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
    // 오류는 남을 때 결과 자리에만 보인다. 비교 자리가 끝나지 않는 로딩 문장을 남기면 안 된다.
    expect(
      screen.queryByText("결과를 불러오는 중입니다."),
    ).not.toBeInTheDocument();
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
    const user = fakeTimerUser();

    renderRoute();
    const raise = await screen.findByLabelText(/올해 예상 인상률/);
    await user.clear(raise);
    await user.type(raise, "5");
    debounce();

    expect(
      await screen.findByText(/초안을 저장하지 못했습니다/),
    ).toBeInTheDocument();
  });

  it("초안에 오퍼가 있으면 오퍼 입력 카드, 없으면 빈 카드로 시작한다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    const first = renderRoute();

    expect(await screen.findByLabelText(/새 회사 연봉/)).toBeInTheDocument();
    expect(screen.queryByText("받은 오퍼가 있나요?")).not.toBeInTheDocument();
    first.unmount();

    vi.mocked(scenario.getDraft).mockResolvedValue(stayOnly);
    renderRoute();

    expect(await screen.findByText("받은 오퍼가 있나요?")).toBeInTheDocument();
    expect(screen.queryByLabelText(/새 회사 연봉/)).not.toBeInTheDocument();
  });

  // 실업급여는 기본값이 없다. 안 고른 채 비교가 나오면 받지 못하는 사람에게 실업급여를 더한 결과가 된다.
  it("실업급여를 안 고르면 비교 대신 안내가 보인다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(stayOnly);
    const user = fakeTimerUser();

    renderRoute();
    await user.click(await screen.findByRole("button", { name: "오퍼 넣기" }));
    // 누른 버튼이 사라지므로 포커스를 첫 입력칸으로 옮긴다. 안 옮기면 body 로 빠진다.
    expect(screen.getByLabelText(/새 회사 연봉/)).toHaveFocus();
    await user.type(screen.getByLabelText(/새 회사 연봉/), "7200");
    debounce();

    expect(
      await screen.findByText(
        "실업급여를 받을 수 있는지 고르면 비교가 나옵니다",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("따라잡는 시점")).not.toBeInTheDocument();
  });

  // 스펙 「동작」 재계산 — 오퍼만 무효면 지금 회사 결과는 최신이라 흐리지 않는다.
  it("오퍼만_무효면_남을_때_결과는_선명하고_비교만_흐리다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    const user = fakeTimerUser();

    renderRoute();
    await screen.findByText("따라잡는 시점");
    await waitFor(() =>
      expect(compareArea()).toHaveAttribute("aria-busy", "false"),
    );
    const gap = screen.getByLabelText(/쉬는 기간/);
    await user.clear(gap);
    await user.type(gap, "9");
    debounce();

    expect(compareArea()).toHaveAttribute("aria-busy", "true");
    expect(
      within(compareArea() as HTMLElement).getByText(
        "입력을 고치면 다시 계산합니다",
      ),
    ).toBeInTheDocument();
    expect(stayArea()).toHaveAttribute("aria-busy", "false");
    expect(
      within(stayArea() as HTMLElement).queryByText(
        "입력을 고치면 다시 계산합니다",
      ),
    ).not.toBeInTheDocument();
  });

  // 평가가 실패해도 직전 결과는 남는다. 비교 자리가 그 결과를 지금 입력의 결과처럼 보이면 안 된다.
  it("다시 계산이 실패하면 직전 비교를 흐리고 실패를 적는다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    const user = fakeTimerUser();

    renderRoute();
    await screen.findByText("따라잡는 시점");
    await waitFor(() =>
      expect(compareArea()).toHaveAttribute("aria-busy", "false"),
    );
    vi.mocked(scenario.evaluate).mockRejectedValue(new Error("평가 서버 오류"));
    const gap = screen.getByLabelText(/쉬는 기간/);
    await user.clear(gap);
    await user.type(gap, "2");
    debounce();

    await screen.findByRole("alert");
    expect(compareArea()).toHaveAttribute("aria-busy", "true");
    expect(
      within(compareArea() as HTMLElement).getByText(
        "계산하지 못했습니다 — 위 오류를 확인하세요",
      ),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("alert")).toHaveLength(1);
  });

  it("빈_오퍼_카드면_지금_회사만_저장한다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(stayOnly);
    vi.mocked(scenario.saveScenario).mockResolvedValue(
      savedScenario(stayOnly, null),
    );
    const user = userEvent.setup();

    renderRoute();
    await user.click(await screen.findByRole("button", { name: "오퍼 넣기" }));
    await user.click(
      screen.getAllByRole("button", { name: "시나리오 저장" })[0],
    );
    await user.click(await screen.findByRole("button", { name: "저장" }));

    expect(scenario.saveScenario).toHaveBeenCalledWith(
      null,
      expect.objectContaining({
        stay: expect.objectContaining({ raisePercent: 3 }),
        move: null,
      }),
    );
    // 저장 팝오버는 성공하면 닫히므로 확인 문장은 라우트가 남긴다.
    expect(await screen.findByText("시나리오를 저장했습니다.")).toHaveAttribute(
      "role",
      "status",
    );

    // 두 번째 저장도 다시 알린다 — 팝오버를 열면 지난 확인 문장을 지운다.
    await user.click(
      screen.getAllByRole("button", { name: "시나리오 저장" })[0],
    );
    expect(
      screen.queryByText("시나리오를 저장했습니다."),
    ).not.toBeInTheDocument();
  });

  // 직전에 유효했던 오퍼로 몰래 저장하지 않는다(스펙 「동작」 저장 버튼).
  it("오퍼가 무효면 저장하지 않는다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    const user = fakeTimerUser();

    renderRoute();
    const gap = await screen.findByLabelText(/쉬는 기간/);
    await user.clear(gap);
    await user.type(gap, "9");
    debounce();
    await user.click(
      screen.getAllByRole("button", { name: "시나리오 저장" })[0],
    );

    expect(screen.queryByLabelText(/시나리오 이름/)).not.toBeInTheDocument();
    expect(scenario.saveScenario).not.toHaveBeenCalled();
  });

  it("지우기_뒤_되돌리면_오퍼가_돌아온다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    const user = userEvent.setup();

    renderRoute();
    await user.click(
      await screen.findByRole("button", { name: "새 회사 오퍼 지우기" }),
    );
    expect(await screen.findByText("받은 오퍼가 있나요?")).toBeInTheDocument();
    // 누른 `지우기` 가 사라지므로 포커스를 `오퍼 넣기` 로 옮긴다.
    expect(screen.getByRole("button", { name: "오퍼 넣기" })).toHaveFocus();
    await waitFor(() =>
      expect(scenario.putDraft).toHaveBeenLastCalledWith(
        expect.objectContaining({ move: null }),
      ),
    );

    await user.click(
      screen.getByRole("button", { name: "이전 입력으로 되돌리기" }),
    );

    expect(await screen.findByLabelText(/새 회사 연봉/)).toHaveValue("7,000");
  });

  it("지금_회사만_저장분을_불러오면_빈_카드가_되고_되돌리면_열린다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    vi.mocked(scenario.listScenarios).mockResolvedValue([
      savedScenario(stayOnly, "남는 안"),
    ]);
    const user = userEvent.setup();

    renderRoute();
    await user.click(
      await screen.findByRole("button", { name: "저장된 시나리오 1" }),
    );
    await user.click(await screen.findByRole("button", { name: "불러오기" }));
    expect(await screen.findByText("받은 오퍼가 있나요?")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "이전 입력으로 되돌리기" }),
    );

    expect(await screen.findByLabelText(/새 회사 연봉/)).toHaveValue("7,000");
  });

  // 선택 항목은 값이 있으면 펼친 채 그린다. 펼침이 포커스를 옮기면 불러오기가 제목으로 보낸 포커스를 빼앗는다.
  it("선택 항목이 채워진 저장분을 불러와도 제목 포커스를 빼앗지 않는다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    vi.mocked(scenario.listScenarios).mockResolvedValue([
      savedScenario(
        {
          stay: { ...draft.stay, stockVesting12m: 5_000_000 },
          move: { ...draft.move!, signingBonus: 3_000_000 },
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
      expect(screen.getByLabelText(/사이닝 보너스 \(만원\)/)).toHaveValue(
        "300",
      ),
    );
    expect(
      screen.getByLabelText(/1년 안에 받을 주식/, {
        selector: "#stockVesting12m",
      }),
    ).toHaveValue("500");
    await waitFor(() =>
      expect(screen.getByRole("heading", { level: 1 })).toHaveFocus(),
    );
  });

  it("오퍼를 먼저 채우고 지금 회사 연봉을 나중에 넣어도 오퍼가 계산에 들어간다", async () => {
    vi.mocked(scenario.getContext).mockResolvedValue({
      ...context,
      currentAnnualGross: null,
    });
    const user = fakeTimerUser();

    renderRoute();
    await user.click(await screen.findByRole("button", { name: "오퍼 넣기" }));
    await user.type(screen.getByLabelText(/새 회사 연봉/), "7200");
    await user.click(screen.getByRole("radio", { name: "못 받음" }));
    debounce();
    await user.type(screen.getByLabelText(/현재 세전 연봉/), "6000");
    debounce();

    await waitFor(() =>
      expect(vi.mocked(scenario.evaluate).mock.lastCall?.[0]).toMatchObject({
        move: { grossSalary: 72_000_000 },
      }),
    );
  });

  it("지금 회사 연봉 전에 채운 오퍼는 지운 뒤 되살아나지 않는다", async () => {
    vi.mocked(scenario.getContext).mockResolvedValue({
      ...context,
      currentAnnualGross: null,
    });
    const user = fakeTimerUser();

    renderRoute();
    await user.click(await screen.findByRole("button", { name: "오퍼 넣기" }));
    await user.type(screen.getByLabelText(/새 회사 연봉/), "7200");
    await user.click(screen.getByRole("radio", { name: "못 받음" }));
    debounce();
    // 지금 회사 요청이 없을 때는 비교 안내 대신 지금 회사 안내만 보인다.
    expect(
      screen.queryByText(/넣으면 비교가 나옵니다|고르면 비교가 나옵니다/),
    ).not.toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "새 회사 오퍼 지우기" }),
    );
    await user.type(screen.getByLabelText(/현재 세전 연봉/), "6000");
    debounce();

    await waitFor(() => expect(scenario.evaluate).toHaveBeenCalled());
    expect(
      vi.mocked(scenario.evaluate).mock.lastCall?.[0].move ?? null,
    ).toBeNull();
  });

  it("예전 계산 저장분을 다시 계산하면 두 카드가 채워진다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(stayOnly);
    vi.mocked(scenario.listScenarios).mockResolvedValue([legacyScenario]);
    const user = userEvent.setup();

    renderRoute();
    await user.click(
      await screen.findByRole("button", { name: "저장된 시나리오 1" }),
    );
    await user.click(await screen.findByRole("button", { name: "다시 계산" }));

    const offerGross = await screen.findByLabelText(/새 회사 연봉/);
    expect(offerGross).toHaveValue("8,000");
    expect(offerGross).toBeVisible();
    expect(screen.getByLabelText(/올해 예상 인상률/)).toHaveValue("4");
  });

  it("예전 ?step= 링크로 들어와도 한 화면이 열린다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(stayOnly);

    renderRoute("/dashboard/simulator?step=move");

    expect(await screen.findByLabelText(/올해 예상 인상률/)).toBeVisible();
    expect(screen.getByText("받은 오퍼가 있나요?")).toBeVisible();
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
    expect(screen.queryByText(/소득 이력에 저장됨/)).not.toBeInTheDocument();

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

  // 금액 칸은 만원 단위로 채워진다. 만원 단위가 아닌 저장 상여를 "고친 값"으로 읽으면 손대지 않아도
  // 결과가 흐려지고, 칸을 벗어나면 반올림한 값이 기록에 저장된다.
  it("만원 단위가 아닌 저장 상여는 손대지 않으면 결과를 흐리지도 다시 저장하지도 않는다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(scenario.getContext).mockResolvedValue({
      ...context,
      bonusIncluded: false,
      fixedBonusAnnual: 5_555_555,
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
        fixedBonusAnnual: 5_555_555,
      },
    ]);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    const { container } = renderRoute();
    const amount = await screen.findByLabelText(/연간 고정상여/);
    const resultArea = () => container.querySelector("[aria-busy]");
    await waitFor(() => expect(resultArea()).not.toBeNull());
    act(() => {
      vi.advanceTimersByTime(400);
    });

    expect(resultArea()).toHaveAttribute("aria-busy", "false");

    await user.click(amount);
    await user.tab();

    expect(incomeApi.updateBonusTerms).not.toHaveBeenCalled();
  });

  // 지우기는 오퍼 카드만 다시 그린다. 지금 회사 카드까지 다시 그리면 디바운스 전 편집이 사라지고,
  // 되돌리기가 지운 뒤 고친 지금 회사 값을 지우기 전 값으로 덮는다.
  it("지우기와 그 되돌리기는 지금 회사 입력을 건드리지 않는다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    const user = fakeTimerUser();

    renderRoute();
    const raise = await screen.findByLabelText(/올해 예상 인상률/);
    await user.clear(raise);
    await user.type(raise, "5");
    await user.click(
      screen.getByRole("button", { name: "새 회사 오퍼 지우기" }),
    );
    expect(screen.getByLabelText(/올해 예상 인상률/)).toHaveValue("5");
    debounce();

    await user.click(
      screen.getByRole("button", { name: "이전 입력으로 되돌리기" }),
    );

    expect(await screen.findByLabelText(/새 회사 연봉/)).toHaveValue("7,000");
    expect(screen.getByLabelText(/올해 예상 인상률/)).toHaveValue("5");
    await waitFor(() =>
      expect(vi.mocked(scenario.evaluate).mock.lastCall?.[0]).toMatchObject({
        stay: { raisePercent: 5 },
        move: { grossSalary: 70_000_000 },
      }),
    );
  });

  it("유효한 오퍼가 있으면 오퍼까지 저장한다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    vi.mocked(scenario.saveScenario).mockResolvedValue(
      savedScenario(draft, null),
    );
    const user = userEvent.setup();

    renderRoute();
    await screen.findByText("따라잡는 시점");
    await user.click(
      screen.getAllByRole("button", { name: "시나리오 저장" })[0],
    );
    await user.click(await screen.findByRole("button", { name: "저장" }));

    expect(scenario.saveScenario).toHaveBeenCalledWith(
      null,
      expect.objectContaining({
        move: expect.objectContaining({ grossSalary: 70_000_000 }),
      }),
    );
  });

  // 기록 저장이 끝나기 전에 평가하면 저장 전 기록으로 계산한 결과가 최신처럼 보인다.
  it("상여 기록을 저장하는 동안에는 다시 계산하지 않고, 저장이 끝나면 계산한다", async () => {
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
    let finishSave: (value: []) => void = () => {};
    vi.mocked(incomeApi.updateBonusTerms).mockReturnValue(
      new Promise((resolve) => {
        finishSave = resolve;
      }),
    );
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    renderRoute();
    const amount = await screen.findByLabelText(/연간 고정상여/);
    await waitFor(() => expect(scenario.evaluate).toHaveBeenCalled());
    await user.clear(amount);
    await user.type(amount, "600");
    debounce();
    await user.tab();
    expect(incomeApi.updateBonusTerms).toHaveBeenCalled();
    const before = vi.mocked(scenario.evaluate).mock.calls.length;

    const raise = screen.getByLabelText(/올해 예상 인상률/);
    await user.clear(raise);
    await user.type(raise, "5");
    debounce();
    expect(scenario.evaluate).toHaveBeenCalledTimes(before);

    await act(async () => finishSave([]));
    await waitFor(() =>
      expect(vi.mocked(scenario.evaluate).mock.lastCall?.[0]).toMatchObject({
        stay: { raisePercent: 5 },
      }),
    );
  });

  it("상단 시나리오 템플릿 메뉴에서 템플릿을 선택하면 오퍼 카드가 열리고 해당 조건이 적용된다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(stayOnly);
    const user = userEvent.setup();

    renderRoute();
    await screen.findByText("지금 회사");

    // 상단 템플릿 메뉴 열기
    await user.click(screen.getByRole("button", { name: /시나리오 템플릿/ }));
    // 첫 번째 템플릿('안정적 연봉 점프')의 '적용' 버튼 클릭
    const applyButtons = screen.getAllByRole("button", { name: "적용" });
    await user.click(applyButtons[0]);

    // 오퍼 카드가 열리고 템플릿 적용 알림 및 연봉 값이 반영되었는지 검증
    expect(
      await screen.findByText(/「안정적 연봉 점프」 템플릿을 적용했습니다/),
    ).toBeInTheDocument();
    expect(await screen.findByLabelText(/새 회사 연봉/)).toHaveValue("6,900");
  });

  it("닫힌 오퍼 카드의 템플릿 버튼을 클릭하면 오퍼가 열리고 템플릿 조건이 반영된다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(stayOnly);
    const user = userEvent.setup();

    renderRoute();
    await screen.findByText("받은 오퍼가 있나요?");

    // 닫힌 카드 내부의 '스타트업 보상 (스톡·사이닝)' 버튼 클릭 (6,000만 원 기준 +5% -> 6,300만 원)
    await user.click(
      screen.getByRole("button", { name: "스타트업 보상 (스톡·사이닝)" }),
    );

    expect(
      await screen.findByText(
        /「스타트업 보상 \(스톡·사이닝\)」 템플릿을 적용했습니다/,
      ),
    ).toBeInTheDocument();
    expect(await screen.findByLabelText(/새 회사 연봉/)).toHaveValue("6,300");
  });

  // 게스트의 저장은 서버에 시나리오를 만들지 않는다. 초안만 확정하고 가입 뒤 이어서 저장한다.
  it("게스트가_저장하면_초안을_보낸_뒤_가입으로_간다", async () => {
    setSession(guest);
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    const user = userEvent.setup();

    renderRoute();
    await screen.findByText("따라잡는 시점");
    await user.click(
      screen.getAllByRole("button", { name: "시나리오 저장" })[0],
    );
    await user.type(
      await screen.findByLabelText("시나리오 이름 (선택)"),
      "A안",
    );
    await user.click(await screen.findByRole("button", { name: "저장" }));

    expect(await screen.findByTestId("where")).toHaveTextContent(
      "/register?next=%2Fdashboard%2Fsimulator%3FresumeSave%3D1%26name%3D" +
        encodeURIComponent(encodeURIComponent("A안")),
    );
    expect(scenario.saveScenario).not.toHaveBeenCalled();
    expect(scenario.putDraft).toHaveBeenCalledWith(
      expect.objectContaining({
        move: expect.objectContaining({ grossSalary: 70_000_000 }),
      }),
    );
  });

  // StrictMode 의 이중 effect 로 같은 시나리오가 두 번 저장되면 목록에 중복이 쌓인다.
  it("가입_후_돌아오면_저장을_이어서_한다", async () => {
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);
    vi.mocked(scenario.saveScenario).mockResolvedValue(
      savedScenario(draft, "A안"),
    );

    renderRoute(
      `/dashboard/simulator?resumeSave=1&name=${encodeURIComponent("A안")}`,
      true,
    );

    expect(await screen.findByText("시나리오를 저장했습니다.")).toBeVisible();
    expect(scenario.saveScenario).toHaveBeenCalledTimes(1);
    expect(scenario.saveScenario).toHaveBeenCalledWith("A안", draft);
    await waitFor(() =>
      expect(screen.getByTestId("where")).toHaveTextContent(
        /^\/dashboard\/simulator$/,
      ),
    );
  });

  it("게스트로_복귀_주소를_열면_저장하지_않는다", async () => {
    setSession(guest);
    vi.mocked(scenario.getDraft).mockResolvedValue(draft);

    renderRoute("/dashboard/simulator?resumeSave=1&name=A", true);
    await screen.findByText("따라잡는 시점");

    expect(scenario.saveScenario).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(screen.getByTestId("where")).toHaveTextContent(
        /^\/dashboard\/simulator$/,
      ),
    );
  });
});
