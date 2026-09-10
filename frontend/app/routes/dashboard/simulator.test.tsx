import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as scenarioApi from "../../lib/api/scenario";
import { UnauthorizedError } from "../../lib/coreApi";
import { resetSession, setSession } from "../../lib/session";
import DashboardSimulator, { clientAction, clientLoader } from "./simulator";

const SAVED_INPUTS = {
  scenarioName: "A사 오퍼",
  stayExpectedRaiseRate: 3.5,
  moveTargetGrossSalary: 72_000_000,
  moveOneTimeCost: 3_000_000,
  expectedUnemploymentMonths: 2,
  isEligibleForUnemploymentBenefit: true,
};

const OK_RESULT = {
  id: "s-1",
  scenarioName: "A사 오퍼",
  stayCompensation12m: 62_100_000,
  moveCompensation12m: 60_960_000,
  estimatedUnemploymentBenefit: 3_960_000,
  unemploymentBenefitNote: "가정 기반 추정 (기준 시점: 2026-01)",
  difference: -1_140_000,
  type: "ESTIMATED_OPPORTUNITY_COST",
  typeLabel: "추정 기회비용",
  warningNote: "세금, 성과급, 퇴직금 등은 포함하지 않습니다.",
  feasibility: {
    status: "OK",
    sampleSize: 42,
    minRequired: null,
    feasibilityScore: 31,
    targetPercentile: 69,
    difficultyGrade: "CHALLENGING",
    feedbackMessage: "상위 31% 수준입니다.",
  },
};

describe("S-15 시뮬레이터 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    resetSession();
    setSession({
      status: "authenticated",
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "COMPLETED",
    });
  });

  function renderScreen() {
    const Stub = createRoutesStub([
      {
        path: "/dashboard/simulator",
        Component: DashboardSimulator,
        loader: clientLoader,
        action: clientAction,
        HydrateFallback: () => null,
      },
      { path: "/onboarding/income", Component: () => <div>소득 온보딩 화면</div> },
      { path: "/login", Component: () => <div>로그인 화면</div> },
    ]);
    return render(<Stub initialEntries={["/dashboard/simulator"]} />);
  }

  it("저장된 시나리오가 있으면 입력과 결과를 복원한다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue({
      inputs: SAVED_INPUTS,
      result: OK_RESULT,
      calculatedAt: "2026-09-07T00:00:00Z",
    } as never);

    renderScreen();

    expect(await screen.findByDisplayValue("A사 오퍼")).toBeInTheDocument();
    expect(screen.getByLabelText("이직 시 목표 세전 연봉 (원)")).toHaveValue(72_000_000);
    expect(screen.getByLabelText("이직에 드는 일회성 비용 (원)")).toHaveValue(3_000_000);
    expect(screen.getByLabelText("예상 구직 공백 (개월, 0~6)")).toHaveValue(2);
    expect(screen.getByRole("radio", { name: "있음" })).toBeChecked();

    expect(screen.getByText("추정 기회비용")).toBeInTheDocument();
    expect(screen.getByText("1,140,000원")).toBeInTheDocument();
    expect(screen.getByText(OK_RESULT.warningNote)).toBeInTheDocument();
  });

  it("204(저장분 없음)면 빈 폼을 보여주고 기본값을 채우지 않는다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue(null as never);

    renderScreen();

    expect(
      await screen.findByText("아직 계산한 시나리오가 없습니다. 값을 입력하고 계산해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("이직 시 목표 세전 연봉 (원)")).toHaveValue(null);
    expect(screen.getByLabelText("예상 인상률 (%)")).toHaveValue(null);
    expect(screen.getByRole("radio", { name: "있음" })).not.toBeChecked();
    expect(screen.getByRole("radio", { name: "없음" })).not.toBeChecked();
  });

  it("계산하면 simulate 가 불리고 결과 카드(typeLabel·difference·warningNote)가 나온다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue(null as never);
    const simulateSpy = vi.spyOn(scenarioApi, "simulate").mockResolvedValue(OK_RESULT as never);

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("예상 인상률 (%)"), "3.5");
    await user.type(screen.getByLabelText("이직 시 목표 세전 연봉 (원)"), "72000000");
    await user.type(screen.getByLabelText("이직에 드는 일회성 비용 (원)"), "3000000");
    await user.type(screen.getByLabelText("예상 구직 공백 (개월, 0~6)"), "2");
    await user.click(screen.getByRole("radio", { name: "있음" }));
    await user.click(screen.getByRole("button", { name: "계산" }));

    expect(await screen.findByText("추정 기회비용")).toBeInTheDocument();
    expect(screen.getByText("1,140,000원")).toBeInTheDocument();
    expect(screen.getByText(OK_RESULT.warningNote)).toBeInTheDocument();
    expect(simulateSpy).toHaveBeenCalledWith({
      stayExpectedRaiseRate: 3.5,
      moveTargetGrossSalary: 72_000_000,
      moveOneTimeCost: 3_000_000,
      expectedUnemploymentMonths: 2,
      isEligibleForUnemploymentBenefit: true,
    });
  });

  it("표본이 부족하면 백분위 대신 표본 부족을 보여준다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue({
      inputs: SAVED_INPUTS,
      result: {
        ...OK_RESULT,
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
      calculatedAt: "2026-09-07T00:00:00Z",
    } as never);

    renderScreen();

    expect(await screen.findByText("표본 부족")).toBeInTheDocument();
    expect(screen.queryByText(/상위 \d+%/)).not.toBeInTheDocument();
  });

  it("isEligibleForUnemploymentBenefit 이 거짓이면 실업급여 미반영이 나온다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue({
      inputs: { ...SAVED_INPUTS, isEligibleForUnemploymentBenefit: false },
      result: OK_RESULT,
      calculatedAt: "2026-09-07T00:00:00Z",
    } as never);

    renderScreen();

    expect(await screen.findByText("실업급여 미반영")).toBeInTheDocument();
    expect(screen.queryByText(OK_RESULT.unemploymentBenefitNote)).not.toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "없음" })).toBeChecked();
  });

  // isEligibleForUnemploymentBenefit 은 SimulationResult 응답에 없다 — 화면에 지금
  // 보이는 result 가 실업급여를 반영해 계산된 값인지는 그 result 가 온 자리(loader
  // 의 저장분)에 달려 있어야 한다. 라디오의 "현재 선택"을 신뢰 소스로 쓰면, 계산을
  // 다시 누르지 않고 라디오만 바꾼 순간 화면에 남은 숫자(이전 가정으로 계산된
  // 금액)에 방금 고른 가정의 안내가 잘못 붙는다 — 숫자와 문구가 서로 다른 가정을
  // 말하는 상태가 된다.
  it("계산하지 않고 라디오만 바꿔도 현재 결과에 쓰인 실업급여 반영 여부는 바뀌지 않는다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue({
      inputs: SAVED_INPUTS, // isEligibleForUnemploymentBenefit: true
      result: OK_RESULT,
      calculatedAt: "2026-09-07T00:00:00Z",
    } as never);

    renderScreen();
    const user = userEvent.setup();

    expect(await screen.findByText(OK_RESULT.unemploymentBenefitNote)).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "없음" }));

    expect(screen.getByText(OK_RESULT.unemploymentBenefitNote)).toBeInTheDocument();
    expect(screen.queryByText("실업급여 미반영")).not.toBeInTheDocument();
  });

  it("계산이 실패해도 이전 결과가 남고 오류가 함께 나온다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue({
      inputs: SAVED_INPUTS,
      result: OK_RESULT,
      calculatedAt: "2026-09-07T00:00:00Z",
    } as never);
    vi.spyOn(scenarioApi, "simulate").mockRejectedValue({
      code: "INVALID_INPUT",
      message: "입력값을 확인해 주세요.",
      status: 400,
    });

    renderScreen();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "계산" }));

    expect(await screen.findByText("입력값을 확인해 주세요.")).toBeInTheDocument();
    expect(screen.getByText("추정 기회비용")).toBeInTheDocument();
  });

  // 브리프의 여섯 케이스에는 없지만, 설계 문서·브리프가 명시적으로 요구하는 동작이다
  // (currentGrossSalary 를 생략했는데 소득 이력이 없으면 서버가 400 을 주고, 화면은
  // 원문 오류 대신/함께 소득 화면으로 가는 안내를 보여줘야 한다). 클라이언트 스키마가
  // 이 화면이 보내는 다섯 필드를 이미 전부 검증하므로, 검증을 통과한 제출이 그래도
  // 400 을 받는 현실적인 경우는 이 케이스뿐이라고 보고 status===400 을 신호로 쓴다.
  it("소득 이력이 없어 400 을 받으면 소득 입력 안내 링크를 함께 보여준다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue(null as never);
    vi.spyOn(scenarioApi, "simulate").mockRejectedValue({
      code: "INVALID_INPUT",
      message: "소득 이력이 없어 계산할 수 없습니다.",
      status: 400,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("예상 인상률 (%)"), "3.5");
    await user.type(screen.getByLabelText("이직 시 목표 세전 연봉 (원)"), "72000000");
    await user.type(screen.getByLabelText("이직에 드는 일회성 비용 (원)"), "3000000");
    await user.type(screen.getByLabelText("예상 구직 공백 (개월, 0~6)"), "2");
    await user.click(screen.getByRole("radio", { name: "있음" }));
    await user.click(screen.getByRole("button", { name: "계산" }));

    expect(await screen.findByText("소득 이력이 없어 계산할 수 없습니다.")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "소득 먼저 입력하기" });
    expect(link).toHaveAttribute("href", "/onboarding/income");
  });

  // income.test.tsx 의 "세전 연봉이 범위를 벗어나면..." 테스트와 같은 모양 — 필드별
  // 메시지·aria-describedby 배선·포커스 이동을 함께 확인한다. FIELD_ERROR_MESSAGES 의
  // 키가 scenarioSchema 의 필드명과 어긋나면(오타 등) toFieldErrors 가 조용히 _form
  // 으로 떨어뜨리는데(task-7 FIX 3), 이 테스트가 없으면 그 회귀를 아무것도 못 잡는다.
  it("이직 목표 연봉이 범위를 벗어나면 저장하지 않고 필드 오류와 포커스를 보여준다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue(null as never);
    const simulateSpy = vi.spyOn(scenarioApi, "simulate");

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("예상 인상률 (%)"), "3.5");
    await user.type(screen.getByLabelText("이직 시 목표 세전 연봉 (원)"), "1000");
    await user.type(screen.getByLabelText("이직에 드는 일회성 비용 (원)"), "3000000");
    await user.type(screen.getByLabelText("예상 구직 공백 (개월, 0~6)"), "2");
    await user.click(screen.getByRole("radio", { name: "있음" }));
    await user.click(screen.getByRole("button", { name: "계산" }));

    // 라벨("이직 시 목표 세전 연봉 (원)")도 부분 문자열을 포함해 느슨한 정규식은
    // 다중 매치로 깨진다 — 필드 에러 메시지 전체를 정확히 매칭한다.
    const errorText = await screen.findByText(
      "이직 목표 세전 연봉은 1천만 원~30억 원 사이로 입력해 주세요.",
    );
    expect(errorText).toBeInTheDocument();
    expect(simulateSpy).not.toHaveBeenCalled();

    const invalidField = screen.getByLabelText("이직 시 목표 세전 연봉 (원)");
    expect(invalidField).toHaveAttribute("aria-describedby", errorText.id);
    expect(invalidField).toHaveFocus();
  });

  // 라디오 그룹은 Field 를 쓰지 않고 직접 마크업했다 — 그 배선(aria-describedby,
  // #eligibility-error)을 이 테스트가 유일하게 확인한다.
  it("실업급여 수급 자격을 선택하지 않으면 안내와 함께 제출을 막는다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue(null as never);
    const simulateSpy = vi.spyOn(scenarioApi, "simulate");

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("예상 인상률 (%)"), "3.5");
    await user.type(screen.getByLabelText("이직 시 목표 세전 연봉 (원)"), "72000000");
    await user.type(screen.getByLabelText("이직에 드는 일회성 비용 (원)"), "3000000");
    await user.type(screen.getByLabelText("예상 구직 공백 (개월, 0~6)"), "2");
    await user.click(screen.getByRole("button", { name: "계산" }));

    const errorText = await screen.findByText("실업급여 수급 자격 여부를 선택해 주세요.");
    expect(errorText).toBeInTheDocument();
    expect(errorText.id).toBe("eligibility-error");
    expect(simulateSpy).not.toHaveBeenCalled();

    expect(screen.getByRole("radiogroup")).toHaveAttribute(
      "aria-describedby",
      "eligibility-error",
    );
  });

  it("조회 중 401 을 받으면 로그인 화면으로 보낸다", async () => {
    vi.spyOn(scenarioApi, "getLatestScenario").mockRejectedValue(new UnauthorizedError());

    renderScreen();

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });
});
