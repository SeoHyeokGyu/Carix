import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as incomeApi from "../../../lib/api/income";
import { confirmDialog } from "../../../test-utils/confirmDialog";
import RecordsIncome, {
  clientAction,
  clientLoader,
  ErrorBoundary,
} from "./income";

vi.mock("../../../lib/api/income");

const rows: incomeApi.IncomeResponse[] = [
  {
    id: "i-old",
    grossSalary: 58_000_000,
    netSalary: 3_800_000,
    effectiveDate: "2023-01-01",
    changeReason: "INITIAL",
    selectedChangeReason: null,
    bonusIncluded: false,
    fixedBonusAnnual: 12_000_000,
  },
  {
    id: "i-new",
    grossSalary: 65_000_000,
    netSalary: 4_200_000,
    effectiveDate: "2024-01-01",
    changeReason: "RAISE",
    selectedChangeReason: "RAISE",
    bonusIncluded: true,
    fixedBonusAnnual: null,
  },
];

function renderTab() {
  // 스텁 형태는 app/routes/onboarding/income.test.tsx 를 따른다. HydrateFallback 이
  // 없으면 loader 가 붙은 라우트의 하이드레이션에서 경고가 나고, /login 라우트가
  // 없으면 clientLoader 의 UnauthorizedError → redirect(LOGIN_PATH) 경로가 스텁을
  // 터뜨린다.
  const Stub = createRoutesStub([
    {
      path: "/mypage/records",
      Component: RecordsIncome,
      ErrorBoundary,
      loader: clientLoader,
      action: clientAction,
      HydrateFallback: () => null,
    },
    { path: "/login", Component: () => <div>로그인 화면</div> },
  ]);
  return render(<Stub initialEntries={["/mypage/records"]} />);
}

describe("소득 탭 (S-12)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incomeApi.listIncome).mockResolvedValue(rows);
    vi.mocked(incomeApi.listBonuses).mockResolvedValue([]);
  });

  it("상여 조건을 저장하면 선택한 행부터 적용하도록 보낸다", async () => {
    vi.mocked(incomeApi.updateBonusTerms).mockResolvedValue(rows);
    renderTab();
    const user = userEvent.setup();

    await user.click(await screen.findByLabelText("따로 받음"));
    await user.type(screen.getByLabelText(/연간 고정상여/), "1500");
    await user.click(screen.getByRole("button", { name: "상여 조건 저장" }));

    await waitFor(() =>
      expect(incomeApi.updateBonusTerms).toHaveBeenCalledWith({
        applyFromIncomeId: "i-new",
        bonusIncluded: false,
        fixedBonusAnnual: 15_000_000,
      }),
    );
  });

  it("상여 조건 400 의 details 를 고정상여 칸에 붙인다", async () => {
    vi.mocked(incomeApi.updateBonusTerms).mockRejectedValue({
      code: "INVALID_INPUT",
      message: "입력값 형식이 올바르지 않습니다.",
      status: 400,
      details: {
        fixedBonusAnnual:
          "상여를 따로 받으면 0원보다 큰 고정 상여 금액이 필요합니다.",
      },
    });
    renderTab();
    const user = userEvent.setup();

    await user.click(await screen.findByLabelText("따로 받음"));
    await user.type(screen.getByLabelText(/연간 고정상여/), "1500");
    await user.click(screen.getByRole("button", { name: "상여 조건 저장" }));

    await screen.findByText(
      "상여를 따로 받으면 0원보다 큰 고정 상여 금액이 필요합니다.",
    );
    expect(document.getElementById("fixedBonusAnnual-error")).not.toBeNull();
  });

  it("성과급을 추가하면 목록을 다시 불러온다", async () => {
    vi.mocked(incomeApi.createBonus).mockResolvedValue({
      id: "b-1",
      paidMonth: "202603",
      amount: 5_000_000,
    });
    renderTab();
    const user = userEvent.setup();
    expect(
      await screen.findByText(
        "받은 성과급이 있으면 지급월과 금액을 기록해 두세요",
      ),
    ).toBeInTheDocument();

    vi.mocked(incomeApi.listBonuses).mockResolvedValue([
      { id: "b-1", paidMonth: "202603", amount: 5_000_000 },
    ]);
    await user.type(screen.getByLabelText("지급월"), "2026-03");
    await user.type(screen.getByLabelText(/^금액/), "500");
    await user.click(screen.getByRole("button", { name: "성과급 추가" }));

    await waitFor(() =>
      expect(incomeApi.createBonus).toHaveBeenCalledWith({
        paidMonth: "202603",
        amount: 5_000_000,
      }),
    );
    expect(await screen.findByText("2026-03")).toBeInTheDocument();
  });

  it("추가하면 만원 단위를 원 단위로 환산해 보낸다", async () => {
    vi.mocked(incomeApi.createIncome).mockResolvedValue(rows[1]);
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/세전 연봉/), "6500");
    await user.type(screen.getByLabelText(/월 실수령액/), "420");
    await user.type(screen.getByLabelText(/연봉 적용 시작일/), "2024-01-01");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(incomeApi.createIncome).toHaveBeenCalledWith({
        grossSalary: 65_000_000,
        netSalary: 4_200_000,
        effectiveDate: "2024-01-01",
      }),
    );
  });

  it("미래 날짜는 거부하고 서버를 부르지 않는다", async () => {
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/세전 연봉/), "6500");
    await user.type(screen.getByLabelText(/월 실수령액/), "420");
    await user.type(screen.getByLabelText(/연봉 적용 시작일/), "2099-01-01");
    await user.click(screen.getByRole("button", { name: "저장" }));

    // toFieldErrors 는 zod 이슈 고유 메시지가 아니라 INCOME_FIELD_MESSAGES 의
    // 필드별 문구로 치환한다(app/lib/fieldErrors.ts) — effectiveDate 의 모든
    // 위반(형식 오류든 미래 날짜든)이 같은 문구로 뜬다. 브리프 원문은 스키마의
    // refine 메시지("미래 날짜는 입력할 수 없습니다.")를 기대했지만 그 문자열은
    // 이 경로로 화면에 닿을 수 없다 — 실제로 그리는 문구로 고쳤다.
    expect(
      await screen.findByText(
        "연봉 적용 시작일은 2024-03-01 처럼 입력해 주세요. 다음 달 이후 날짜는 넣을 수 없습니다.",
      ),
    ).toBeInTheDocument();
    expect(incomeApi.createIncome).not.toHaveBeenCalled();
  });

  it("409 는 유효 시작일 필드에 붙인다", async () => {
    vi.mocked(incomeApi.createIncome).mockRejectedValue({
      code: "DUPLICATE_EFFECTIVE_DATE",
      message: "같은 유효 시작일이 이미 있습니다.",
      status: 409,
    });
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/세전 연봉/), "6500");
    await user.type(screen.getByLabelText(/월 실수령액/), "420");
    await user.type(screen.getByLabelText(/연봉 적용 시작일/), "2024-01-01");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await screen.findByText("같은 유효 시작일이 이미 있습니다.");

    // 텍스트만으로는 이 오류가 effectiveDate 필드에 붙었는지 아니면 일반
    // _form 배너로 떨어졌는지 구분되지 않는다(toUserMessage 의 _form 경로도
    // 같은 문자열을 만든다). `expect.stringContaining(errorText.id)` 로
    // aria-describedby 를 검사하는 이전 버전은 _form 회귀에서도 통과했다 —
    // _form 문단엔 id 가 없어 `errorText.id` 가 `""` 이고
    // `stringContaining("")` 은 항상 참이기 때문이다(힌트 id 는 항상 붙어
    // 있어 aria-describedby 자체는 비지 않는다). `<Field>` 가 오류를
    // `${id}-error` 로 렌더링하므로(app/components/onboarding/Field.tsx),
    // effectiveDate 필드 착지는 그 id 를 가진 요소가 실제로 존재하고 그
    // 안에 이 메시지가 있는지로 직접 확인해야 한다 — _form 배너로 떨어지면
    // 이 id 를 가진 요소 자체가 생기지 않는다.
    const fieldError = document.getElementById("effectiveDate-error");
    expect(fieldError).not.toBeNull();
    expect(fieldError).toHaveTextContent("같은 유효 시작일이 이미 있습니다.");
  });

  it("저장이 실패해도 입력값을 지우지 않는다", async () => {
    vi.mocked(incomeApi.createIncome).mockRejectedValue({
      code: "UNKNOWN",
      message: "요청을 처리하지 못했습니다.",
      status: 500,
    });
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/세전 연봉/), "6500");
    await user.type(screen.getByLabelText(/월 실수령액/), "420");
    await user.type(screen.getByLabelText(/연봉 적용 시작일/), "2024-01-01");
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText("요청을 처리하지 못했습니다."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/세전 연봉/)).toHaveValue("6,500");
  });

  it("삭제하면 해당 id 로 서버를 부른다", async () => {
    vi.mocked(incomeApi.deleteIncome).mockResolvedValue(null);
    renderTab();
    const user = userEvent.setup();

    const buttons = await screen.findAllByRole("button", { name: "삭제" });
    await user.click(buttons[0]);
    await confirmDialog(user, "삭제");

    await waitFor(() =>
      expect(incomeApi.deleteIncome).toHaveBeenCalledWith("i-new"),
    );
  });

  it("삭제가 실패하면 해당 행에 오류를 보여주고 목록은 그대로 둔다", async () => {
    vi.mocked(incomeApi.deleteIncome).mockRejectedValue({
      code: "UNKNOWN",
      message: "삭제하지 못했습니다.",
      status: 500,
    });
    renderTab();
    const user = userEvent.setup();

    const buttons = await screen.findAllByRole("button", { name: "삭제" });
    await user.click(buttons[0]);
    await confirmDialog(user, "삭제");

    expect(await screen.findByText("삭제하지 못했습니다.")).toBeInTheDocument();
    // 행별 삭제 실패가 목록 전체를 비우지 않는다 — 두 행 모두 남아 있다.
    expect(screen.getByText("2024-01-01")).toBeInTheDocument();
    expect(screen.getByText("2023-01-01")).toBeInTheDocument();
  });

  it("삭제 요청에 id 가 없으면 성공을 가장하지 않고 _form 오류를 돌려준다", async () => {
    // M5: hidden input 이 항상 id 를 실어 보내 화면에서는 닿지 않는 경로지만,
    // clientAction 자체는 id 없이 불려도 조용히 null(성공)을 돌려주면 안 된다.
    const formData = new FormData();
    formData.set("intent", "delete");
    const request = new Request("http://test/mypage/records", {
      method: "POST",
      body: formData,
    });

    const result = await clientAction({ request });

    expect(result).toEqual({
      errors: { _form: "삭제할 항목을 찾을 수 없습니다." },
    });
    expect(incomeApi.deleteIncome).not.toHaveBeenCalled();
  });

  it("생성이 성공해도 변경 사유 셀렉트가 이전 선택을 유지하지 않는다 (회귀)", async () => {
    // Important #1(최종 리뷰): 셀렉트가 uncontrolled(defaultValue)라 폼 key 가
    // 안 바뀌는 생성→생성 경로에서 리마운트가 없다 — 이직을 고르고 저장한 뒤
    // 셀렉트를 건드리지 않고 다시 저장하면 두 번째 행에도 이직이 실린다.
    vi.mocked(incomeApi.createIncome).mockResolvedValue(rows[1]);
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/세전 연봉/), "6000");
    await user.type(screen.getByLabelText(/월 실수령액/), "400");
    await user.type(screen.getByLabelText(/연봉 적용 시작일/), "2024-06-01");
    await user.selectOptions(screen.getByLabelText("변경 사유"), "JOB_CHANGE");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(incomeApi.createIncome).toHaveBeenNthCalledWith(1, {
        grossSalary: 60_000_000,
        netSalary: 4_000_000,
        effectiveDate: "2024-06-01",
        changeReason: "JOB_CHANGE",
      }),
    );

    // 폼이 성공 후 리셋됐는지 확인하고서 다음 입력을 시작한다.
    await waitFor(() =>
      expect(screen.getByLabelText(/세전 연봉/)).toHaveValue(""),
    );

    await user.type(screen.getByLabelText(/세전 연봉/), "7000");
    await user.type(screen.getByLabelText(/월 실수령액/), "450");
    await user.type(screen.getByLabelText(/연봉 적용 시작일/), "2025-01-01");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(incomeApi.createIncome).toHaveBeenNthCalledWith(2, {
        grossSalary: 70_000_000,
        netSalary: 4_500_000,
        effectiveDate: "2025-01-01",
      }),
    );
  });

  it("불러오기에 실패하면 다시 시도할 수 있고, 성공하면 목록으로 돌아온다", async () => {
    vi.mocked(incomeApi.listIncome)
      .mockRejectedValueOnce(new Error("boom"))
      .mockResolvedValueOnce(rows);
    renderTab();
    const user = userEvent.setup();

    expect(
      await screen.findByText("소득 이력을 불러오지 못했습니다"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("2024-01-01")).toBeInTheDocument();
  });

  it("표시값이 INITIAL 인 행을 수정해도 저장된 원값이 그대로 실려 간다", async () => {
    // 가장 이른 행이라 표시는 INITIAL 이지만 컬럼에는 JOB_CHANGE 가 있다. 폼이
    // 표시값만 보면 셀렉트가 비고, PUT 이 전체 교체라 그 JOB_CHANGE 가 지워진다.
    vi.mocked(incomeApi.listIncome).mockResolvedValue([
      {
        ...rows[0],
        changeReason: "INITIAL",
        selectedChangeReason: "JOB_CHANGE",
        bonusIncluded: true,
        fixedBonusAnnual: null,
      },
    ]);
    vi.mocked(incomeApi.updateIncome).mockResolvedValue(rows[0]);
    renderTab();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "수정" }));
    expect(screen.getByLabelText("변경 사유")).toHaveValue("JOB_CHANGE");

    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(incomeApi.updateIncome).toHaveBeenCalledWith(
        "i-old",
        expect.objectContaining({ changeReason: "JOB_CHANGE" }),
      ),
    );
  });
});
