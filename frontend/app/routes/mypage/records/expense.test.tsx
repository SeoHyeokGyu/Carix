import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as expenseApi from "../../../lib/api/expense";
import * as incomeApi from "../../../lib/api/income";
import RecordsExpense, { clientAction, clientLoader } from "./expense";

vi.mock("../../../lib/api/expense");
vi.mock("../../../lib/api/income");

const months: expenseApi.ExpenseHistoryResponse[] = [
  {
    baseYearMonth: "202607",
    weightStatus: "PARTIALLY_ESTIMATED",
    items: [
      { category: "HOUSING", amount: 900_000, status: "USER_DEFINED" },
      { category: "FOOD", amount: 600_000, status: "USER_DEFINED" },
      { category: "TRANSPORT", amount: 200_000, status: "DEFAULT_CPI" },
    ],
  },
  {
    baseYearMonth: "202606",
    weightStatus: "USER_DEFINED",
    items: [{ category: "HOUSING", amount: 850_000, status: "USER_DEFINED" }],
  },
];

function renderTab() {
  // 스텁 형태는 app/routes/onboarding/income.test.tsx 를 따른다 — HydrateFallback 과
  // /login 라우트가 둘 다 필요한 이유는 Task 5 의 같은 주석을 보라.
  const Stub = createRoutesStub([
    {
      path: "/mypage/records/expense",
      Component: RecordsExpense,
      loader: clientLoader,
      action: clientAction,
      HydrateFallback: () => null,
    },
    { path: "/login", Component: () => <div>로그인 화면</div> },
  ]);
  return render(<Stub initialEntries={["/mypage/records/expense"]} />);
}

describe("소비 탭 (S-12)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(expenseApi.listExpenses).mockResolvedValue(months);
    vi.mocked(incomeApi.listIncome).mockResolvedValue([
      {
        id: "i-1",
        grossSalary: 65_000_000,
        netSalary: 4_200_000,
        effectiveDate: "2024-01-01",
      },
    ]);
  });

  it("기준월을 내림차순으로 그리고 YYYY-MM 으로 보여준다", async () => {
    renderTab();
    const cells = await screen.findAllByText(/^\d{4}-\d{2}$/);
    expect(cells[0]).toHaveTextContent("2026-07");
    expect(cells[1]).toHaveTextContent("2026-06");
  });

  it("서버가 준 상태 뱃지를 그대로 그린다", async () => {
    renderTab();
    // 클라이언트가 계산하지 않는다 — 서버 status 를 그대로 뱃지로 만든다.
    expect(await screen.findByText("통계청 평균 기준")).toBeInTheDocument();
  });

  it("월을 고르면 6 카테고리 전부를 폼으로 연다", async () => {
    renderTab();
    const user = userEvent.setup();
    await user.click(
      (await screen.findAllByRole("button", { name: "편집" }))[0],
    );

    for (const label of [
      "주거·공공요금",
      "식비",
      "교통",
      "의료",
      "교육·자기계발",
      "여가·기타",
    ]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
  });

  it("기본값으로 되돌리면 그 카테고리를 빼고 PUT 한다", async () => {
    vi.mocked(expenseApi.putExpenses).mockResolvedValue(months[0]);
    renderTab();
    const user = userEvent.setup();
    await user.click(
      (await screen.findAllByRole("button", { name: "편집" }))[0],
    );
    await user.click(
      screen.getByRole("button", { name: "식비 기본값으로 되돌리기" }),
    );
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(expenseApi.putExpenses).toHaveBeenCalled());
    const [, items] = vi.mocked(expenseApi.putExpenses).mock.calls[0];
    expect(items.map((i) => i.category)).not.toContain("FOOD");
    expect(items.map((i) => i.category)).toContain("HOUSING");
  });

  it("미래 월 추가를 거부하고 서버를 부르지 않는다", async () => {
    renderTab();
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText("추가할 기준월"), "2099-01");
    await user.click(screen.getByRole("button", { name: "기준월 추가" }));

    expect(
      await screen.findByText("미래 기준월은 추가할 수 없습니다."),
    ).toBeInTheDocument();
    expect(expenseApi.putExpenses).not.toHaveBeenCalled();
  });

  it("합계가 월 실수령액을 넘으면 경고하되 저장은 막지 않는다", async () => {
    vi.mocked(expenseApi.putExpenses).mockResolvedValue(months[0]);
    renderTab();
    const user = userEvent.setup();
    await user.click(
      (await screen.findAllByRole("button", { name: "편집" }))[0],
    );

    const housing = screen.getByLabelText("주거·공공요금");
    await user.clear(housing);
    await user.type(housing, "9000000");

    expect(
      await screen.findByText(/월 실수령액을 초과합니다/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "저장" })).toBeEnabled();
  });

  it("기록이 없으면 추가를 유도하고 월 삭제가 없음을 알린다", async () => {
    vi.mocked(expenseApi.listExpenses).mockResolvedValue([]);
    renderTab();
    expect(
      await screen.findByText("등록된 소비 기록이 없습니다"),
    ).toBeInTheDocument();
    expect(screen.getByText(/기준월은 삭제할 수 없습니다/)).toBeInTheDocument();
  });
});
