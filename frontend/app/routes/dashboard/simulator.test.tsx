import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as incomeApi from "../../lib/api/income";
import * as scenarioApi from "../../lib/api/scenario";
import { UnauthorizedError } from "../../lib/coreApi";
import { resetSession, setSession } from "../../lib/session";
import { expectHeadingOutline } from "../../test-utils/headingOutline";
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

/** 저장분 한 건. id·이름은 입력·결과에서 따온다 — 테스트마다 다른 건 입력·결과뿐이다. */
function saved(scenario: {
  inputs: { scenarioName?: string | null } & Record<string, unknown>;
  result: { id: string } & Record<string, unknown>;
  calculatedAt: string;
}) {
  return {
    id: scenario.result.id,
    scenarioName: scenario.inputs.scenarioName ?? null,
    ...scenario,
  };
}

const OLDER_INPUTS = {
  scenarioName: null,
  stayExpectedRaiseRate: 5,
  moveTargetGrossSalary: 80_000_000,
  moveOneTimeCost: 0,
  expectedUnemploymentMonths: 0,
  isEligibleForUnemploymentBenefit: false,
};

const OLDER_RESULT = {
  ...OK_RESULT,
  id: "s-0",
  scenarioName: null,
  stayCompensation12m: 63_000_000,
  moveCompensation12m: 80_000_000,
  difference: 17_000_000,
  type: "ESTIMATED_GAIN",
  typeLabel: "추정 추가보상",
};

/** 최근 순 두 건 — 2026-09-07 09:00(KST) 「A사 오퍼」, 2026-09-01 10:00(KST) 이름 없음. */
const TWO_SAVED = [
  saved({
    inputs: SAVED_INPUTS,
    result: OK_RESULT,
    calculatedAt: "2026-09-07T00:00:00Z",
  }),
  saved({
    inputs: OLDER_INPUTS,
    result: OLDER_RESULT,
    calculatedAt: "2026-09-01T01:00:00Z",
  }),
];

const PICKER = "저장된 시나리오 불러오기";

describe("S-15 시뮬레이터 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([]);
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
      { path: "/mypage/records", Component: () => <div>이력 관리 화면</div> },
      { path: "/login", Component: () => <div>로그인 화면</div> },
    ]);
    return render(<Stub initialEntries={["/dashboard/simulator"]} />);
  }

  it("저장된 시나리오가 있으면 입력과 결과를 복원한다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
      saved({
        inputs: SAVED_INPUTS,
        result: OK_RESULT,
        calculatedAt: "2026-09-07T00:00:00Z",
      }),
    ] as never);

    renderScreen();

    expect(await screen.findByDisplayValue("A사 오퍼")).toBeInTheDocument();
    expect(screen.getByLabelText("이직 시 목표 세전 연봉 (만원)")).toHaveValue(
      "7,200",
    );
    expect(screen.getByLabelText("이직에 드는 일회성 비용 (원)")).toHaveValue(
      "3,000,000",
    );
    expect(screen.getByLabelText("예상 구직 공백 (개월, 0~6)")).toHaveValue(2);
    expect(screen.getByRole("radio", { name: "있음" })).toBeChecked();

    expect(screen.getByText("추정 기회비용")).toBeInTheDocument();
    expect(
      screen.getByText("남으면 12개월간 약 114만 원 더 받습니다"),
    ).toBeInTheDocument();
    expect(screen.getByText(OK_RESULT.warningNote)).toBeInTheDocument();
  });

  it("제목은 메뉴 이름 「시뮬레이터」 h1 이다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([]);

    renderScreen();

    expect(
      await screen.findByRole("heading", { level: 1, name: "시뮬레이터" }),
    ).toBeInTheDocument();
    expectHeadingOutline();
    expect(
      screen.getByRole("heading", { level: 2, name: "시나리오 입력" }),
    ).toBeInTheDocument();
  });

  it("저장분이 없으면(빈 목록) 빈 폼을 보여주고 기본값을 채우지 않는다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([]);

    renderScreen();

    expect(
      await screen.findByText(
        "아직 계산한 시나리오가 없습니다. 값을 입력하고 계산해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("이직 시 목표 세전 연봉 (만원)")).toHaveValue(
      "",
    );
    expect(screen.getByLabelText("예상 인상률 (%)")).toHaveValue(null);
    expect(screen.getByRole("radio", { name: "있음" })).not.toBeChecked();
    expect(screen.getByRole("radio", { name: "없음" })).not.toBeChecked();
  });

  it("계산하면 simulate 가 불리고 결과 카드(typeLabel·difference·warningNote)가 나온다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([]);
    const simulateSpy = vi
      .spyOn(scenarioApi, "simulate")
      .mockResolvedValue(OK_RESULT as never);

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("예상 인상률 (%)"), "3.5");
    await user.type(
      screen.getByLabelText("이직 시 목표 세전 연봉 (만원)"),
      "7200",
    );
    await user.type(
      screen.getByLabelText("이직에 드는 일회성 비용 (원)"),
      "3000000",
    );
    await user.type(screen.getByLabelText("예상 구직 공백 (개월, 0~6)"), "2");
    await user.click(screen.getByRole("radio", { name: "있음" }));
    await user.click(screen.getByRole("button", { name: "계산" }));

    expect(await screen.findByText("추정 기회비용")).toBeInTheDocument();
    expect(
      screen.getByText("남으면 12개월간 약 114만 원 더 받습니다"),
    ).toBeInTheDocument();
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
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
      saved({
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
      }),
    ] as never);

    renderScreen();

    expect(await screen.findByText("표본 부족")).toBeInTheDocument();
    expect(screen.queryByText(/상위 \d+%/)).not.toBeInTheDocument();
  });

  // status가 OK 인데 targetPercentile·difficultyGrade 가 비어 있거나(서버가 이
  // 화면이 아는 네 등급 밖의 값을 주는 등) 값을 찾을 수 없는 경우를 실측 없이도
  // 방어한다 — 빈 자리("상위 %")나 구분자만 남는 자리(" · ")를 그대로 보여주지
  // 않는다.
  it("OK 인데 백분위나 등급을 찾을 수 없으면 빈 자리를 만들지 않는다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
      saved({
        inputs: SAVED_INPUTS,
        result: {
          ...OK_RESULT,
          feasibility: {
            status: "OK",
            sampleSize: 42,
            minRequired: null,
            feasibilityScore: 31,
            targetPercentile: null,
            difficultyGrade: "UNKNOWN_GRADE",
            feedbackMessage: "상위권 수준입니다.",
          },
        },
        calculatedAt: "2026-09-07T00:00:00Z",
      }),
    ] as never);

    renderScreen();

    await screen.findByText("추정 기회비용");
    expect(screen.queryByText(/상위\s*%/)).not.toBeInTheDocument();
    expect(screen.queryByText("·")).not.toBeInTheDocument();
    expect(screen.getByText("상위권 수준입니다.")).toBeInTheDocument();
  });

  it("isEligibleForUnemploymentBenefit 이 거짓이면 실업급여 미반영이 나온다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
      saved({
        inputs: { ...SAVED_INPUTS, isEligibleForUnemploymentBenefit: false },
        result: OK_RESULT,
        calculatedAt: "2026-09-07T00:00:00Z",
      }),
    ] as never);

    renderScreen();

    expect(await screen.findByText("실업급여 미반영")).toBeInTheDocument();
    expect(
      screen.queryByText(OK_RESULT.unemploymentBenefitNote),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "없음" })).toBeChecked();
  });

  // isEligibleForUnemploymentBenefit 은 SimulationResult 응답에 없다 — 화면에 지금
  // 보이는 result 가 실업급여를 반영해 계산된 값인지는 그 result 가 온 자리(loader
  // 의 저장분)에 달려 있어야 한다. 라디오의 "현재 선택"을 신뢰 소스로 쓰면, 계산을
  // 다시 누르지 않고 라디오만 바꾼 순간 화면에 남은 숫자(이전 가정으로 계산된
  // 금액)에 방금 고른 가정의 안내가 잘못 붙는다 — 숫자와 문구가 서로 다른 가정을
  // 말하는 상태가 된다.
  it("계산하지 않고 라디오만 바꿔도 현재 결과에 쓰인 실업급여 반영 여부는 바뀌지 않는다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
      saved({
        inputs: SAVED_INPUTS, // isEligibleForUnemploymentBenefit: true
        result: OK_RESULT,
        calculatedAt: "2026-09-07T00:00:00Z",
      }),
    ] as never);

    renderScreen();
    const user = userEvent.setup();

    expect(
      await screen.findByText(OK_RESULT.unemploymentBenefitNote),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "없음" }));

    expect(
      screen.getByText(OK_RESULT.unemploymentBenefitNote),
    ).toBeInTheDocument();
    expect(screen.queryByText("실업급여 미반영")).not.toBeInTheDocument();
  });

  it("계산이 실패해도 이전 결과가 남고 오류가 함께 나온다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
      saved({
        inputs: SAVED_INPUTS,
        result: OK_RESULT,
        calculatedAt: "2026-09-07T00:00:00Z",
      }),
    ] as never);
    vi.spyOn(scenarioApi, "simulate").mockRejectedValue({
      code: "INVALID_INPUT",
      message: "입력값을 확인해 주세요.",
      status: 400,
    });

    renderScreen();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "계산" }));

    expect(
      await screen.findByText("입력값을 확인해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByText("추정 기회비용")).toBeInTheDocument();
  });

  // 복합 경로 — 위의 "라디오만 바꿔도" 테스트와 "계산 실패해도 이전 결과 유지" 테스트를
  // 하나로 잇는다: 라디오를 바꾸고, 실제로 제출하고, 그 계산이 실패한다. success 가
  // 실패 시 완전히 null 이 되므로(all-or-nothing) resultEligible 도 result 와 함께
  // 정확히 같은 자리(scenario)로 폴백해야 한다 — 이 세 단계를 한 테스트로 pin 해 둔다.
  // 저장된 시나리오가 있어야 폴백할 대상이 있으므로 loader 는 null 이 아니라 SAVED_INPUTS
  // 를 돌려준다.
  it("라디오를 바꾸고 제출했는데 계산이 실패하면, 화면 숫자와 안내가 여전히 같은 가정을 말한다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
      saved({
        inputs: SAVED_INPUTS, // isEligibleForUnemploymentBenefit: true
        result: OK_RESULT,
        calculatedAt: "2026-09-07T00:00:00Z",
      }),
    ] as never);
    vi.spyOn(scenarioApi, "simulate").mockRejectedValue({
      code: "INVALID_INPUT",
      message: "입력값을 확인해 주세요.",
      status: 400,
    });

    renderScreen();
    const user = userEvent.setup();

    expect(
      await screen.findByText(OK_RESULT.unemploymentBenefitNote),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "없음" }));
    await user.click(screen.getByRole("button", { name: "계산" }));

    expect(
      await screen.findByText("입력값을 확인해 주세요."),
    ).toBeInTheDocument();

    // 화면에 남은 결과는 여전히 실업급여를 반영해 계산된 이전 값(OK_RESULT)이다 —
    // 라디오는 "없음"이지만 그건 다음 제출에 실릴 값일 뿐, 지금 보이는 숫자를 만든
    // 가정은 아니다. 안내도 그 숫자를 따라가야 한다.
    expect(
      screen.getByText(OK_RESULT.unemploymentBenefitNote),
    ).toBeInTheDocument();
    expect(screen.queryByText("실업급여 미반영")).not.toBeInTheDocument();
    expect(screen.getByText("추정 기회비용")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "없음" })).toBeChecked();
  });

  // 브리프의 여섯 케이스에는 없지만, 설계 문서·브리프가 명시적으로 요구하는 동작이다
  // (currentGrossSalary 를 생략했는데 소득 이력이 없으면 서버가 400 을 주고, 화면은
  // 원문 오류와 함께 소득 화면으로 가는 안내를 보여줘야 한다). 클라이언트 스키마가
  // 이 화면이 보내는 다섯 필드를 이미 전부 검증하므로, 검증을 통과한 제출이 그래도
  // 400 을 받는 현실적인 경우는 이 케이스뿐이라고 보고 status===400 을 신호로 쓴다.
  // 링크는 /onboarding/income 이 아니라 /mypage/records 다 — guardOnboarding 이
  // 온보딩 완료 사용자의 /onboarding/* 진입을 막아 앞쪽은 죽은 링크다(팀 리드 판정).
  it("소득 이력이 없어 400 을 받으면 소득 입력 안내 링크를 함께 보여준다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([]);
    vi.spyOn(scenarioApi, "simulate").mockRejectedValue({
      code: "INVALID_INPUT",
      message: "소득 이력이 없어 계산할 수 없습니다.",
      status: 400,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("예상 인상률 (%)"), "3.5");
    await user.type(
      screen.getByLabelText("이직 시 목표 세전 연봉 (만원)"),
      "7200",
    );
    await user.type(
      screen.getByLabelText("이직에 드는 일회성 비용 (원)"),
      "3000000",
    );
    await user.type(screen.getByLabelText("예상 구직 공백 (개월, 0~6)"), "2");
    await user.click(screen.getByRole("radio", { name: "있음" }));
    await user.click(screen.getByRole("button", { name: "계산" }));

    expect(
      await screen.findByText("소득 이력이 없어 계산할 수 없습니다."),
    ).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "입력 관리" });
    expect(link).toHaveAttribute("href", "/mypage/records");
  });

  // 팀 리드 판정 — 소득과 무관한 400 도 같은 status===400 판별을 타지만, 그래도
  // 서버 원문 메시지가 대체되지 않고 그대로 보여야 한다(소득 안내는 그 아래 보조
  // 문구로만 얹힌다, 위 렌더 로직 참고). 이 테스트는 그 "원문이 우선"이라는 계약을
  // 소득과 명백히 무관한 메시지로 고정한다.
  it("소득과 무관한 400 오류도 서버 원문 메시지를 그대로 보여준다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([]);
    vi.spyOn(scenarioApi, "simulate").mockRejectedValue({
      code: "INVALID_INPUT",
      message: "예상 인상률이 유효 범위를 벗어났습니다.",
      status: 400,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("예상 인상률 (%)"), "3.5");
    await user.type(
      screen.getByLabelText("이직 시 목표 세전 연봉 (만원)"),
      "7200",
    );
    await user.type(
      screen.getByLabelText("이직에 드는 일회성 비용 (원)"),
      "3000000",
    );
    await user.type(screen.getByLabelText("예상 구직 공백 (개월, 0~6)"), "2");
    await user.click(screen.getByRole("radio", { name: "있음" }));
    await user.click(screen.getByRole("button", { name: "계산" }));

    expect(
      await screen.findByText("예상 인상률이 유효 범위를 벗어났습니다."),
    ).toBeInTheDocument();
  });

  // income.test.tsx 의 "세전 연봉이 범위를 벗어나면..." 테스트와 같은 모양 — 필드별
  // 메시지·aria-describedby 배선·포커스 이동을 함께 확인한다. FIELD_ERROR_MESSAGES 의
  // 키가 scenarioSchema 의 필드명과 어긋나면(오타 등) toFieldErrors 가 조용히 _form
  // 으로 떨어뜨리는데(task-7 FIX 3), 이 테스트가 없으면 그 회귀를 아무것도 못 잡는다.
  it("이직 목표 연봉이 범위를 벗어나면 저장하지 않고 필드 오류와 포커스를 보여준다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([]);
    const simulateSpy = vi.spyOn(scenarioApi, "simulate");

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("예상 인상률 (%)"), "3.5");
    await user.type(
      screen.getByLabelText("이직 시 목표 세전 연봉 (만원)"),
      "999",
    );
    await user.type(
      screen.getByLabelText("이직에 드는 일회성 비용 (원)"),
      "3000000",
    );
    await user.type(screen.getByLabelText("예상 구직 공백 (개월, 0~6)"), "2");
    await user.click(screen.getByRole("radio", { name: "있음" }));
    await user.click(screen.getByRole("button", { name: "계산" }));

    // 라벨("이직 시 목표 세전 연봉 (만원)")도 부분 문자열을 포함해 느슨한 정규식은
    // 다중 매치로 깨진다 — 필드 에러 메시지 전체를 정확히 매칭한다.
    const errorText = await screen.findByText(
      "이직 목표 세전 연봉은 1천만 원~30억 원 사이로 입력해 주세요.",
    );
    expect(errorText).toBeInTheDocument();
    expect(simulateSpy).not.toHaveBeenCalled();

    const invalidField = screen.getByLabelText("이직 시 목표 세전 연봉 (만원)");
    expect(invalidField.getAttribute("aria-describedby")).toContain(
      errorText.id,
    );
    expect(invalidField).toHaveFocus();
  });

  // 라디오 그룹은 Field 를 쓰지 않고 직접 마크업했다 — 그 배선(aria-describedby,
  // #eligibility-error)을 이 테스트가 유일하게 확인한다.
  it("실업급여 수급 자격을 선택하지 않으면 안내와 함께 제출을 막는다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([]);
    const simulateSpy = vi.spyOn(scenarioApi, "simulate");

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("예상 인상률 (%)"), "3.5");
    await user.type(
      screen.getByLabelText("이직 시 목표 세전 연봉 (만원)"),
      "7200",
    );
    await user.type(
      screen.getByLabelText("이직에 드는 일회성 비용 (원)"),
      "3000000",
    );
    await user.type(screen.getByLabelText("예상 구직 공백 (개월, 0~6)"), "2");
    await user.click(screen.getByRole("button", { name: "계산" }));

    const errorText = await screen.findByText(
      "실업급여 수급 자격 여부를 선택해 주세요.",
    );
    expect(errorText).toBeInTheDocument();
    expect(errorText.id).toBe("eligibility-error");
    expect(simulateSpy).not.toHaveBeenCalled();

    const group = screen.getByRole("radiogroup");
    expect(group.getAttribute("aria-describedby")).toContain(
      "eligibility-error",
    );
    // tabIndex={-1} 가 있어야 focusFirstInvalidField 가 이 그룹을 포커스할 수 있다
    // — 그 속성이 빠지면 이 어서션만 조용히 깨진다(다른 열한 건은 무관).
    expect(group).toHaveFocus();
  });

  it("조회 중 401 을 받으면 로그인 화면으로 보낸다", async () => {
    vi.spyOn(scenarioApi, "listScenarios").mockRejectedValue(
      new UnauthorizedError(),
    );

    renderScreen();

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });

  describe("저장 시나리오 목록 (US-S1)", () => {
    it("저장분이 1건 이하면 선택 컨트롤을 감춘다", async () => {
      vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
        TWO_SAVED[0],
      ] as never);

      renderScreen();

      expect(await screen.findByDisplayValue("A사 오퍼")).toBeInTheDocument();
      expect(
        screen.queryByRole("combobox", { name: PICKER }),
      ).not.toBeInTheDocument();
    });

    it("2건 이상이면 이름·계산 시각 라벨로 고를 수 있고, 최근 시나리오가 선택돼 있다", async () => {
      vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue(
        TWO_SAVED as never,
      );

      renderScreen();

      const picker = await screen.findByRole("combobox", { name: PICKER });
      expect(
        screen.getByRole("option", { name: "A사 오퍼 · 2026. 09. 07. 09:00" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("option", { name: "2026. 09. 01. 10:00 계산" }),
      ).toBeInTheDocument();
      expect(picker).toHaveValue("s-1");
      expect(
        screen.getByText(/다시 계산하면 현재 소득 이력의 연봉으로 계산합니다/),
      ).toBeInTheDocument();
    });

    it("고르면 그 입력 가정으로 폼을 채우고 결과도 그 시나리오의 저장분으로 바꾼다", async () => {
      vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue(
        TWO_SAVED as never,
      );

      renderScreen();
      const user = userEvent.setup();

      await user.selectOptions(
        await screen.findByRole("combobox", { name: PICKER }),
        "s-0",
      );

      expect(screen.getByLabelText("시나리오 이름 (선택)")).toHaveValue("");
      expect(screen.getByLabelText("예상 인상률 (%)")).toHaveValue(5);
      expect(
        screen.getByLabelText("이직 시 목표 세전 연봉 (만원)"),
      ).toHaveValue("8,000");
      expect(screen.getByRole("radio", { name: "없음" })).toBeChecked();
      expect(screen.getByText("추정 추가보상")).toBeInTheDocument();
      expect(
        screen.getByText("옮기면 12개월간 약 1,700만 원 더 받습니다"),
      ).toBeInTheDocument();
      expect(screen.getByText("실업급여 미반영")).toBeInTheDocument();
    });

    // 계산 직후 fetcher 성공분이 남아 있어도, 그 뒤에 고른 시나리오가 이긴다 — 아니면
    // 예전 입력 옆에 방금 계산한 숫자가 붙는다.
    it("계산한 뒤 예전 시나리오를 고르면 결과도 예전 저장분으로 바뀐다", async () => {
      vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue(
        TWO_SAVED as never,
      );
      vi.spyOn(scenarioApi, "simulate").mockResolvedValue({
        ...OK_RESULT,
        id: "s-2",
        difference: -2_220_000,
      } as never);

      renderScreen();
      const user = userEvent.setup();

      await user.click(await screen.findByRole("button", { name: "계산" }));
      expect(
        await screen.findByText("남으면 12개월간 약 222만 원 더 받습니다"),
      ).toBeInTheDocument();

      await user.selectOptions(
        screen.getByRole("combobox", { name: PICKER }),
        "s-0",
      );

      expect(
        screen.getByText("옮기면 12개월간 약 1,700만 원 더 받습니다"),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("남으면 12개월간 약 222만 원 더 받습니다"),
      ).not.toBeInTheDocument();
    });

    it("예전 시나리오를 고른 뒤 계산하면 방금 계산한 결과가 보인다", async () => {
      const fresh = { ...OLDER_RESULT, id: "s-2", difference: 16_000_000 };
      // 계산이 성공하면 저장분을 다시 부른다 — 방금 저장된 행이 첫 행이 된다
      vi.spyOn(scenarioApi, "listScenarios")
        .mockResolvedValueOnce(TWO_SAVED as never)
        .mockResolvedValue([
          saved({
            inputs: OLDER_INPUTS,
            result: fresh,
            calculatedAt: "2026-09-08T00:00:00Z",
          }),
          ...TWO_SAVED,
        ] as never);
      vi.spyOn(scenarioApi, "simulate").mockResolvedValue(fresh as never);

      renderScreen();
      const user = userEvent.setup();

      await user.selectOptions(
        await screen.findByRole("combobox", { name: PICKER }),
        "s-0",
      );
      await user.click(screen.getByRole("button", { name: "계산" }));

      expect(
        await screen.findByText("옮기면 12개월간 약 1,600만 원 더 받습니다"),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("옮기면 12개월간 약 1,700만 원 더 받습니다"),
      ).not.toBeInTheDocument();
      expect(screen.getByText("실업급여 미반영")).toBeInTheDocument();
    });

    it("계산해도 폼이 다시 그려지지 않아 계산 버튼 포커스가 남는다", async () => {
      const fresh = { ...OK_RESULT, id: "s-2", difference: -2_220_000 };
      vi.spyOn(scenarioApi, "listScenarios")
        .mockResolvedValueOnce(TWO_SAVED as never)
        .mockResolvedValue([
          saved({
            inputs: SAVED_INPUTS,
            result: fresh,
            calculatedAt: "2026-09-08T00:00:00Z",
          }),
          ...TWO_SAVED,
        ] as never);
      vi.spyOn(scenarioApi, "simulate").mockResolvedValue(fresh as never);

      renderScreen();
      const user = userEvent.setup();

      const button = await screen.findByRole("button", { name: "계산" });
      await user.click(button);
      await screen.findByText("남으면 12개월간 약 222만 원 더 받습니다");

      expect(screen.getByRole("button", { name: "계산" })).toHaveFocus();
    });

    // 서버가 이름 없는 계산에 "기본 시나리오" 를 채워 저장하던 시절의 행이다 — 그대로
    // 채우면 다시 계산할 때 그 이름으로 또 저장된다.
    it("예전 기본명 「기본 시나리오」는 이름 칸에 채우지 않는다", async () => {
      vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
        saved({
          inputs: { ...SAVED_INPUTS, scenarioName: "기본 시나리오" },
          result: OK_RESULT,
          calculatedAt: "2026-09-07T00:00:00Z",
        }),
      ] as never);

      renderScreen();

      await screen.findByText("추정 기회비용");
      expect(screen.getByLabelText("시나리오 이름 (선택)")).toHaveValue("");
    });
  });
  describe("현재 연봉 기준", () => {
    const INCOME = {
      id: "i-1",
      grossSalary: 65_000_000,
      netSalary: 4_200_000,
      effectiveDate: "2025-03-01",
      changeReason: "INITIAL",
      selectedChangeReason: null,
    } as never;

    it("현재 세전 연봉을 보여주고 목표 연봉이 현재보다 몇 % 높은지 알려준다", async () => {
      vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([]);
      vi.spyOn(incomeApi, "listIncome").mockResolvedValue([INCOME]);

      renderScreen();
      const user = userEvent.setup();

      expect(
        await screen.findByText(/현재 세전 연봉은 6,500만 원입니다/),
      ).toBeInTheDocument();
      await user.type(
        screen.getByLabelText("이직 시 목표 세전 연봉 (만원)"),
        "7200",
      );
      expect(
        screen.getByText("= 72,000,000원 · 현재보다 +10.8%"),
      ).toBeInTheDocument();
    });

    it("소득 조회가 실패해도 화면은 그대로 쓴다", async () => {
      vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([]);
      vi.spyOn(incomeApi, "listIncome").mockRejectedValue(new Error("down"));

      renderScreen();

      expect(
        await screen.findByText("모든 칸을 채워야 계산할 수 있습니다."),
      ).toBeInTheDocument();
    });

    it("두 보상이 같으면 차이 대신 같다고 말한다", async () => {
      vi.spyOn(scenarioApi, "listScenarios").mockResolvedValue([
        saved({
          inputs: SAVED_INPUTS,
          result: { ...OK_RESULT, difference: 0 },
          calculatedAt: "2026-09-07T00:00:00Z",
        }),
      ] as never);

      renderScreen();

      expect(
        await screen.findByText("두 경우의 12개월 보상이 같습니다"),
      ).toBeInTheDocument();
    });
  });
});
