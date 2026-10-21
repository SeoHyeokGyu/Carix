import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as expenseApi from "../../../lib/api/expense";
import * as incomeApi from "../../../lib/api/income";
import { EXPENSE_ITEM_AMOUNT_MESSAGE } from "../../../lib/schemas/expense";
import { currentYearMonth, formatYearMonth } from "../../../lib/yearMonth";
import { expectHeadingOutline } from "../../../test-utils/headingOutline";
import RecordsExpense, {
  clientAction,
  clientLoader,
  ErrorBoundary,
} from "./expense";
import RecordsLayout from "./layout";

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
      ErrorBoundary,
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
        changeReason: "INITIAL",
        selectedChangeReason: null,
        bonusIncluded: true,
        fixedBonusAnnual: null,
      },
    ]);
  });

  it("기준월을 내림차순으로 그리고 YYYY-MM 으로 보여준다", async () => {
    renderTab();
    const cells = await screen.findAllByText(/^\d{4}-\d{2}$/);
    expect(cells[0]).toHaveTextContent("2026-07");
    expect(cells[1]).toHaveTextContent("2026-06");
    expect(
      screen.getByRole("heading", { level: 2, name: "월별 소비" }),
    ).toBeInTheDocument();
  });

  it("카테고리 상태 뱃지에 카테고리명을 함께 그려 어느 칸이 보간됐는지 구분한다", async () => {
    renderTab();
    // 클라이언트가 계산하지 않는다 — 서버 status 를 그대로 뱃지로 만들되, 카테고리
    // 이름이 없으면 여섯 칩이 전부 "직접 입력"/"통계청 평균 기준"으로만 보여 어느
    // 칸이 보간됐는지 구분이 안 된다(M2).
    expect(await screen.findByText("식비 · 직접 입력")).toBeInTheDocument();
    expect(screen.getByText("교통 · 통계청 평균 기준")).toBeInTheDocument();
  });

  it("월 단위 weightStatus 뱃지를 서버 값 그대로 그린다", async () => {
    renderTab();
    // 카테고리별 status 와 별개로 서버가 계산해 내려주는 월 종합 상태다(M1).
    // 클라이언트가 카테고리 상태들을 훑어 스스로 계산하지 않는다.
    expect(await screen.findByText("일부 보간")).toBeInTheDocument();
    expect(screen.getByText("직접 입력")).toBeInTheDocument();
  });

  // 카테고리 뱃지 여섯 묶음이 표 폭을 거의 다 가져가면 앞 두 칸이 최소 폭으로 눌려
  // "2026-/07", "직접/입력" 으로 꺾였다(운영 데스크톱 폭에서 확인). 둘 다 짧은 고정 길이 값이다.
  it("기준월·월 상태 칸은 줄을 꺾지 않는다", async () => {
    renderTab();
    const monthCell = (await screen.findByText("2026-07")).closest("td");
    expect(monthCell).toHaveClass("whitespace-nowrap");
    const statusCell = screen.getByText("일부 보간").closest("td");
    expect(statusCell).toHaveClass("whitespace-nowrap");
  });

  it("월을 고르면 6 카테고리 전부를 폼으로 연다", async () => {
    renderTab();
    const user = userEvent.setup();
    await user.click(
      (await screen.findAllByRole("button", { name: "수정" }))[0],
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
    const section = screen.getByRole("region", { name: /^\d{4}-\d{2} 소비$/ });
    expect(section).toContainElement(
      screen.getByRole("heading", { level: 2, name: /^\d{4}-\d{2} 소비$/ }),
    );
  });

  it("기본값으로 되돌리면 그 카테고리를 빼고 PUT 한다", async () => {
    vi.mocked(expenseApi.putExpenses).mockResolvedValue(months[0]);
    renderTab();
    const user = userEvent.setup();
    await user.click(
      (await screen.findAllByRole("button", { name: "수정" }))[0],
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

  it("금액이 범위를 벗어나면 그 칸에 오류를 보여주고 PUT 하지 않는다", async () => {
    renderTab();
    const user = userEvent.setup();
    await user.click(
      (await screen.findAllByRole("button", { name: "수정" }))[0],
    );

    // 202607 은 HOUSING·FOOD 만 USER_DEFINED 라 폼을 열면 이 둘만 채워진다 —
    // items 는 [HOUSING, FOOD] 순서로 나가므로 HOUSING 오류는 items.0.amount 다.
    const housing = screen.getByLabelText("주거·공공요금");
    await user.clear(housing);
    await user.type(housing, "60000000");
    await user.click(screen.getByRole("button", { name: "저장" }));

    // 이전에는 범위를 벗어난 값을 조용히 버려 items 에서 뺐다 — 그러면 사용자가
    // 입력한 값이 아무 피드백 없이 DEFAULT_CPI 로 바뀌어 버린다. 이제는 그
    // 카테고리 칸에 오류로 붙고 PUT 자체를 막는다.
    expect(
      await screen.findByText(EXPENSE_ITEM_AMOUNT_MESSAGE),
    ).toBeInTheDocument();
    expect(expenseApi.putExpenses).not.toHaveBeenCalled();
  });

  it("미래 월 추가를 거부하고 서버를 부르지 않는다", async () => {
    renderTab();
    const user = userEvent.setup();
    const monthInput = await screen.findByLabelText("추가할 기준월");
    // 1차 방어선: `<input type="month">` 의 max 로 당월 이후를 고를 수 없게 한다.
    // 2차 방어선(아래)은 max 를 우회한 직접 입력·자동화를 잡는다.
    expect(monthInput).toHaveAttribute(
      "max",
      formatYearMonth(currentYearMonth()),
    );

    await user.type(monthInput, "2099-01");
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
      (await screen.findAllByRole("button", { name: "수정" }))[0],
    );

    const housing = screen.getByLabelText("주거·공공요금");
    await user.clear(housing);
    await user.type(housing, "9000000");

    expect(
      await screen.findByText(/월 실수령액을 초과합니다/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "저장" })).toBeEnabled();
  });

  it("새 기준월을 빈칸으로 저장하면 오류를 보여주고 PUT 하지 않는다", async () => {
    // I1: 목록에 없는 새 월을 아무 카테고리도 채우지 않고 저장하면 items=[] 가
    // 스키마를 그대로 통과해 PUT 된다 — 서버가 여섯 카테고리를 전부 DEFAULT_CPI 로
    // 채운, 삭제할 수 없는(D2) 통계전용 월을 만든다.
    renderTab();
    const user = userEvent.setup();

    const monthInput = await screen.findByLabelText("추가할 기준월");
    await user.type(monthInput, "2026-05");
    await user.click(screen.getByRole("button", { name: "기준월 추가" }));
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText(
        "새 기준월은 최소 한 카테고리 이상 입력해야 합니다.",
      ),
    ).toBeInTheDocument();
    expect(expenseApi.putExpenses).not.toHaveBeenCalled();
  });

  it("기존 월은 전부 기본값으로 되돌려도 저장할 수 있다 (회귀 방어)", async () => {
    // I1 가드는 신규 월에만 걸려야 한다 — 이미 목록에 있는 월을 전부 기본값으로
    // 되돌리는 것은 D2 상 정당한 사용자 행동이다. 이 테스트가 없으면 새 월 가드를
    // isNew 조건 없이(무조건) 걸어도 그린으로 남는다.
    vi.mocked(expenseApi.putExpenses).mockResolvedValue(months[0]);
    renderTab();
    const user = userEvent.setup();

    // 202607 은 HOUSING·FOOD 가 USER_DEFINED 다 — 수정을 열면 둘 다 채워진다.
    await user.click(
      (await screen.findAllByRole("button", { name: "수정" }))[0],
    );
    await user.click(
      screen.getByRole("button", { name: "주거·공공요금 기본값으로 되돌리기" }),
    );
    await user.click(
      screen.getByRole("button", { name: "식비 기본값으로 되돌리기" }),
    );
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(expenseApi.putExpenses).toHaveBeenCalled());
    const [wire, items] = vi.mocked(expenseApi.putExpenses).mock.calls[0];
    expect(wire).toBe("202607");
    expect(items).toEqual([]);
  });

  it("기록이 없으면 추가를 유도하고 월 삭제가 없음을 알린다", async () => {
    vi.mocked(expenseApi.listExpenses).mockResolvedValue([]);
    renderTab();
    expect(
      await screen.findByText("등록된 소비 기록이 없습니다"),
    ).toBeInTheDocument();
    expect(screen.getByText(/기준월은 삭제할 수 없습니다/)).toBeInTheDocument();
  });

  it("불러오기에 실패하면 다시 시도할 수 있고, 성공하면 목록으로 돌아온다", async () => {
    vi.mocked(expenseApi.listExpenses)
      .mockRejectedValueOnce(new Error("boom"))
      .mockResolvedValueOnce(months);
    renderTab();
    const user = userEvent.setup();

    expect(
      await screen.findByText("소비 이력을 불러오지 못했습니다"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("2026-07")).toBeInTheDocument();
  });

  it("입력 관리 h1 아래로 제목 단계가 이어진다", async () => {
    const Stub = createRoutesStub([
      {
        path: "/mypage/records",
        Component: RecordsLayout,
        children: [
          {
            path: "expense",
            Component: RecordsExpense,
            ErrorBoundary,
            loader: clientLoader,
            action: clientAction,
            HydrateFallback: () => null,
          },
        ],
      },
      { path: "/login", Component: () => <div>로그인 화면</div> },
    ]);
    render(<Stub initialEntries={["/mypage/records/expense"]} />);

    expect(
      await screen.findByRole("heading", { level: 2, name: "월별 소비" }),
    ).toBeInTheDocument();
    expectHeadingOutline();
    // 제목 → 내용 12px 는 PageSection 이 정한다. 구획 안 간격을 섹션에 덮어쓰지 않는다.
    expect(
      screen
        .getByRole("heading", { level: 2, name: "월별 소비" })
        .closest("[data-page-section]"),
    ).not.toHaveClass("gap-4");
  });
});
