import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as expenseApi from "../../lib/api/expense";
import * as incomeApi from "../../lib/api/income";
import * as onboardingApi from "../../lib/onboardingApi";
import { EXPENSE_CATEGORIES } from "../../lib/schemas/expense";
import { resetSession, setSession } from "../../lib/session";
import Expense, { clientAction, clientLoader } from "./expense";

// baseYearMonth 는 항상 "당월"이어야 한다(화면에 월 선택 UI 자체가 없다) — 실제
// 구현과 별개로, 여기서 독립적으로 계산해 기대값 오라클로 쓴다. 구현의 계산
// 함수를 그대로 가져와 비교하면 구현이 틀려도 테스트가 항상 통과하는 동어반복이
// 된다.
function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, "0")}`;
}
import { UnauthorizedError } from "../../lib/coreApi";

describe("S-07 소비 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    resetSession();
    setSession({
      status: "authenticated",
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "EXPENSE",
      guest: false,
    });
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([]);
  });

  function renderScreen(initialEntry = "/onboarding/expense") {
    const Stub = createRoutesStub([
      {
        path: "/onboarding/expense",
        Component: Expense,
        loader: clientLoader,
        action: clientAction,
        HydrateFallback: () => null,
      },
      { path: "/onboarding/skills", Component: () => <div>스킬 화면</div> },
      { path: "/onboarding/income", Component: () => <div>소득 화면</div> },
      { path: "/onboarding/review", Component: () => <div>검토 화면</div> },
      { path: "/login", Component: () => <div>로그인 화면</div> },
    ]);
    return render(<Stub initialEntries={[initialEntry]} />);
  }

  // 브리프 1 — 저장된 당월 지출이 카테고리별 초기값으로 들어간다.
  it("저장된 당월 지출이 카테고리별 초기값으로 들어간다", async () => {
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue([
      {
        baseYearMonth: currentYearMonth(),
        items: [
          { category: "HOUSING", amount: 750_000, status: "USER_DEFINED" },
          { category: "FOOD", amount: 250_000, status: "USER_DEFINED" },
        ],
        weightStatus: "OK",
      },
    ]);

    renderScreen();

    expect(await screen.findByLabelText(/주거·공공요금/)).toHaveValue(
      "750,000",
    );
    expect(screen.getByLabelText(/식비/)).toHaveValue("250,000");
    expect(screen.getByLabelText(/교통/)).toHaveValue("");
  });

  // advisor 지적 — DEFAULT_CPI 값은 서버가 통계청 평균으로 보간한 것이지
  // 사용자가 입력한 값이 아니다. 입력칸을 채워 두면 다음 저장에서 그대로
  // 재제출돼 "직접 입력"으로 굳는다 — 빈 칸을 0 으로 보내는 것과 같은 종류의
  // 실수가 다른 경로로 들어온 것이다. 와이어프레임(03-expenses.html)도 뱃지가
  // 붙은 교통·의료 입력칸을 비워 둔다.
  it("DEFAULT_CPI 카테고리는 입력칸이 비어 있고, 그대로 두고 저장해도 items 에서 빠진다", async () => {
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue([
      {
        baseYearMonth: currentYearMonth(),
        items: [
          { category: "HOUSING", amount: 750_000, status: "USER_DEFINED" },
          { category: "TRANSPORT", amount: 120_000, status: "DEFAULT_CPI" },
        ],
        weightStatus: "OK",
      },
    ]);
    const put = vi.spyOn(expenseApi, "putExpenses").mockResolvedValue({
      baseYearMonth: currentYearMonth(),
      items: [],
      weightStatus: "OK",
    });
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "SKILLS",
      completed: false,
    });

    renderScreen();
    const user = userEvent.setup();

    expect(await screen.findByLabelText(/주거·공공요금/)).toHaveValue(
      "750,000",
    );
    expect(screen.getByLabelText(/교통/)).toHaveValue("");

    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("스킬 화면")).toBeInTheDocument();
    expect(put).toHaveBeenCalledWith(currentYearMonth(), [
      { category: "HOUSING", amount: 750_000 },
    ]);
  });

  // 브리프 4 — 합계가 월 실수령액을 넘으면 경고가 뜨지만 저장은 진행된다.
  it("합계가 월 실수령액을 넘으면 경고가 뜨지만 저장은 진행된다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([
      {
        id: "i-1",
        grossSalary: 50_000_000,
        netSalary: 3_000_000,
        effectiveDate: "2024-01-01",
        changeReason: "INITIAL",
        selectedChangeReason: null,
        bonusIncluded: true,
        fixedBonusAnnual: null,
      },
    ]);
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue([]);
    const put = vi.spyOn(expenseApi, "putExpenses").mockResolvedValue({
      baseYearMonth: currentYearMonth(),
      items: [],
      weightStatus: "OK",
    });
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "SKILLS",
      completed: false,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/주거·공공요금/), "2000000");
    await user.type(screen.getByLabelText(/식비/), "2000000");

    expect(
      await screen.findByText(/월 실수령액을 초과했습니다/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("스킬 화면")).toBeInTheDocument();
    expect(put).toHaveBeenCalled();
  });

  // 팀 리드 경고 — 소득·소비 화면과 달리 이 화면은 한 폼에서 여섯 카테고리를
  // 동시에 제출한다. toFieldErrors 의 행 인덱스 키(items.N.amount)가 실제로
  // 맞는 카테고리에 붙는지 여기서 처음 검증한다. HOUSING 을 비워 "고정 위치"와
  // "제출된 항목 내 위치"가 어긋나도록 만든다 — FOOD·TRANSPORT 만 채우면 제출되는
  // items 배열에서는 FOOD 가 0번, TRANSPORT 가 1번이지만, EXPENSE_CATEGORIES 안의
  // 고정 위치는 FOOD=1, TRANSPORT=2 다. 화면이 "고정 위치"를 오류 키 조회에
  // 쓰면(흔한 실수) 이 테스트가 깨진다.
  it("여러 카테고리를 동시에 제출하면 잘못된 카테고리에만 오류가 정확히 붙는다", async () => {
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue([]);
    const put = vi.spyOn(expenseApi, "putExpenses");

    renderScreen();
    const user = userEvent.setup();

    // HOUSING 은 비운다 — FOOD·TRANSPORT 만 입력해 고정 위치(1,2)와 제출 위치
    // (0,1)를 어긋나게 만든다.
    await user.type(await screen.findByLabelText(/식비/), "1000000");
    await user.type(screen.getByLabelText(/교통/), "60000000"); // 5천만 원 초과 — 무효
    await user.click(screen.getByRole("button", { name: "다음" }));

    const errorText = await screen.findByText(
      "0원~5천만 원 사이로 입력해 주세요.",
    );
    expect(errorText).toBeInTheDocument();

    const transportInput = screen.getByLabelText(/교통/);
    const foodInput = screen.getByLabelText(/식비/);
    // 오류는 교통(2번째로 제출된 항목, items.1)에 붙어야 하고, 식비(1번째로
    // 제출된 항목, items.0)에는 붙지 않아야 한다 — 텍스트 존재만으로는 어느
    // 필드에 붙었는지 구분되지 않으므로 aria-describedby 로 필드 착지 자체를
    // 확인한다.
    expect(transportInput.getAttribute("aria-describedby")).toContain(
      errorText.id,
    );
    expect(foodInput.getAttribute("aria-describedby") ?? "").not.toContain(
      errorText.id,
    );
    expect(put).not.toHaveBeenCalled();
  });

  // 팀 리드 판정 — 소득 이력이 없는 사용자도 이 화면에 정상 도달할 수 있다
  // (온보딩 흐름이 순서를 강제하지 않는다). 비교 기준이 없으므로 경고 자체를
  // 내지 않아야 하고, 에러가 나거나 저장을 막아서도 안 된다.
  it("소득 이력이 없으면 합계가 아무리 커도 경고를 내지 않는다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([]);
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue([]);
    const put = vi.spyOn(expenseApi, "putExpenses").mockResolvedValue({
      baseYearMonth: currentYearMonth(),
      items: [],
      weightStatus: "OK",
    });
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "SKILLS",
      completed: false,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/주거·공공요금/), "50000000");

    expect(
      screen.queryByText(/월 실수령액을 초과했습니다/),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("스킬 화면")).toBeInTheDocument();
    expect(put).toHaveBeenCalled();
  });

  // 팀 리드 판정 — 도넛은 서버가 돌려준 값(추정치 포함)도 반영해야 한다. 저장된
  // 행이 전부 DEFAULT_CPI 인 재방문 사용자가 아무것도 새로 입력하지 않아도,
  // 서버는 완전한 6카테고리 그림을 갖고 있으므로 도넛은 "계산할 수 없음"이
  // 아니라 실제 비중을 보여줘야 한다. 다만 그 조각들은 추정치라는 걸 텍스트로
  // 구분해야 한다.
  it("전부 DEFAULT_CPI 로 저장된 재방문 사용자는 도넛이 계산된 비중을 보여준다", async () => {
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue([
      {
        baseYearMonth: currentYearMonth(),
        items: EXPENSE_CATEGORIES.map(({ value: category }, index) => ({
          category,
          amount: (index + 1) * 100_000,
          status: "DEFAULT_CPI" as const,
        })),
        weightStatus: "OK",
      },
    ]);

    renderScreen();

    await screen.findByLabelText(/주거·공공요금/);

    // 계산 불가 안내가 아니라 실제 비중이 나와야 한다.
    expect(
      screen.queryByText(/비중을 계산할 수 없습니다/),
    ).not.toBeInTheDocument();
    // 여섯 조각 전부 추정치라는 게 텍스트로 드러나야 한다(색으로만 구분하지 않는다).
    expect(screen.getAllByText(/\(추정\)/)).toHaveLength(6);
  });

  it("'다음' 성공 후 세션 캐시의 onboardingStep 을 SKILLS 로 갱신한다", async () => {
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue([]);
    vi.spyOn(expenseApi, "putExpenses").mockResolvedValue({
      baseYearMonth: currentYearMonth(),
      items: [],
      weightStatus: "OK",
    });
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "SKILLS",
      completed: false,
    });

    renderScreen();
    const user = userEvent.setup();

    await screen.findByRole("heading", { level: 1, name: "소비" });
    await user.click(screen.getByRole("button", { name: "다음" }));

    await screen.findByText("스킬 화면");

    const { requireSession } = await import("../../lib/session");
    await expect(requireSession()).resolves.toMatchObject({
      status: "authenticated",
      onboardingStep: "SKILLS",
    });
  });

  it("저장이 API 오류로 실패하면 입력을 유지한 채 오류 메시지를 보여준다", async () => {
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue([]);
    vi.spyOn(expenseApi, "putExpenses").mockRejectedValue({
      code: "SERVER_ERROR",
      message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      status: 500,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/주거·공공요금/), "750000");
    await user.click(screen.getByRole("button", { name: "다음" }));

    const banner = await screen.findByRole("alert");
    expect(banner).toHaveTextContent(
      "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
    // Field 의 필드별 오류 문단은 항상 id={field}-error 를 갖는다 — id 가 없어야
    // 이 오류가 특정 필드가 아니라 폼 전체(_form) 배너로 착지했다는 뜻이다
    // (income.test.tsx 와 같은 패턴 — 텍스트만으로는 착지 위치가 구분되지 않는다).
    expect(banner).not.toHaveAttribute("id");
    expect(screen.getByLabelText(/주거·공공요금/)).toHaveValue("750,000");
  });

  it("조회 중 401 을 받으면 로그인 화면으로 보낸다", async () => {
    vi.spyOn(expenseApi, "listExpenses").mockRejectedValue(
      new UnauthorizedError(),
    );

    renderScreen();

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });
});
