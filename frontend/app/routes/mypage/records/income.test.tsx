import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as incomeApi from "../../../lib/api/income";
import { cancelDialog, confirmDialog } from "../../../test-utils/confirmDialog";
import { expectHeadingOutline } from "../../../test-utils/headingOutline";
import RecordsIncome, {
  clientAction,
  clientLoader,
  ErrorBoundary,
} from "./income";
import RecordsLayout from "./layout";

vi.mock("../../../lib/api/income");

const rows: incomeApi.IncomeResponse[] = [
  {
    id: "i-old",
    grossSalary: 58_000_000,
    netSalary: 3_800_000,
    effectiveDate: "2023-01-01",
    changeReason: "INITIAL",
    selectedChangeReason: null,
  },
  {
    id: "i-new",
    grossSalary: 65_000_000,
    netSalary: 4_200_000,
    effectiveDate: "2024-01-01",
    changeReason: "RAISE",
    selectedChangeReason: "RAISE",
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
  });

  it("유효 시작일 내림차순으로 그린다", async () => {
    renderTab();
    const cells = await screen.findAllByText(/^\d{4}-\d{2}-\d{2}$/);
    expect(cells[0]).toHaveTextContent("2024-01-01");
    expect(cells[1]).toHaveTextContent("2023-01-01");
    expect(
      screen.getByRole("heading", { level: 2, name: "소득 이력" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "소득 추가" }),
    ).toBeInTheDocument();
  });

  it("이력이 없으면 추가를 유도한다", async () => {
    vi.mocked(incomeApi.listIncome).mockResolvedValue([]);
    renderTab();
    expect(
      await screen.findByText("등록된 연봉 이력이 없습니다"),
    ).toBeInTheDocument();
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
        "연봉 적용 시작일은 YYYY-MM-DD 형식의 과거~당월 날짜여야 합니다.",
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

  it("확인을 취소하면 삭제 API 를 부르지 않는다", async () => {
    // 확인창에서 취소하면 요청이 나가지 않는다.
    renderTab();
    const user = userEvent.setup();

    const buttons = await screen.findAllByRole("button", { name: "삭제" });
    await user.click(buttons[0]);
    await cancelDialog(user);

    expect(incomeApi.deleteIncome).not.toHaveBeenCalled();
    expect(screen.getByText("2024-01-01")).toBeInTheDocument();
  });

  it("삭제 확인창은 시작일과 재계산을 알리고, 요청이 끝날 때까지 다시 열 수 없다", async () => {
    let resolveDelete: (v: null) => void = () => {};
    vi.mocked(incomeApi.deleteIncome).mockReturnValue(
      new Promise((r) => {
        resolveDelete = r;
      }),
    );
    renderTab();
    const user = userEvent.setup();

    const trigger = (await screen.findAllByRole("button", { name: "삭제" }))[0];
    await user.click(trigger);
    expect(
      screen.getByRole("alertdialog", { name: "소득 이력을 삭제할까요?" }),
    ).toHaveAccessibleDescription(/지수가 다시 계산됩니다/);
    await confirmDialog(user, "삭제");
    await waitFor(() => expect(trigger).toBeDisabled());
    resolveDelete(null);
  });

  it("이력이 없으면 변경 사유 셀렉트를 그리지 않는다", async () => {
    // US-O3: 그 사용자의 첫 소득 행은 서버가 INITIAL 로 확정하고 사용자에게 묻지
    // 않는다. 화면이 아는 정보(이력 0건)만으로 그 조항을 지킨다.
    vi.mocked(incomeApi.listIncome).mockResolvedValue([]);
    renderTab();

    await screen.findByText("등록된 연봉 이력이 없습니다");
    expect(screen.queryByLabelText("변경 사유")).toBeNull();
  });

  it("고른 변경 사유가 createIncome 인자에 실린다", async () => {
    vi.mocked(incomeApi.createIncome).mockResolvedValue(rows[1]);
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/세전 연봉/), "7000");
    await user.type(screen.getByLabelText(/월 실수령액/), "450");
    await user.type(screen.getByLabelText(/연봉 적용 시작일/), "2025-01-01");
    await user.selectOptions(screen.getByLabelText("변경 사유"), "JOB_CHANGE");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(incomeApi.createIncome).toHaveBeenCalledWith({
        grossSalary: 70_000_000,
        netSalary: 4_500_000,
        effectiveDate: "2025-01-01",
        changeReason: "JOB_CHANGE",
      }),
    );
  });

  it("선택 안 함이면 changeReason 없이 보낸다", async () => {
    // 기본값을 채우면 고르지 않은 것이 고른 것으로 기록된다 (API 규격 §4.2).
    // 빈 문자열도 보내면 안 된다 — enum 검증에서 떨어진다.
    vi.mocked(incomeApi.createIncome).mockResolvedValue(rows[1]);
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/세전 연봉/), "7000");
    await user.type(screen.getByLabelText(/월 실수령액/), "450");
    await user.type(screen.getByLabelText(/연봉 적용 시작일/), "2025-01-01");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(incomeApi.createIncome).toHaveBeenCalledWith({
        grossSalary: 70_000_000,
        netSalary: 4_500_000,
        effectiveDate: "2025-01-01",
      }),
    );
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

  it("표시값이 INITIAL 인 행을 수정해도 저장된 원값이 그대로 실려 간다", async () => {
    // 가장 이른 행이라 표시는 INITIAL 이지만 컬럼에는 JOB_CHANGE 가 있다. 폼이
    // 표시값만 보면 셀렉트가 비고, PUT 이 전체 교체라 그 JOB_CHANGE 가 지워진다.
    vi.mocked(incomeApi.listIncome).mockResolvedValue([
      {
        ...rows[0],
        changeReason: "INITIAL",
        selectedChangeReason: "JOB_CHANGE",
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

  it("목록 표가 변경 사유를 문구로 낸다", async () => {
    // 명세 §7 — 표는 최초 입력·라벨·— 셋을 모두 낼 수 있어야 한다. changeReason
    // 이 null 인 행(과거 회차 혼재 데이터 등)까지 포함해 세 갈래를 함께 본다.
    vi.mocked(incomeApi.listIncome).mockResolvedValue([
      ...rows,
      {
        id: "i-null",
        grossSalary: 70_000_000,
        netSalary: 4_500_000,
        effectiveDate: "2025-01-01",
        changeReason: null,
        selectedChangeReason: null,
      },
    ]);
    renderTab();

    // 표로 좁힌다 — "연봉 인상" 은 셀렉트의 <option> 에도 있어서 화면 전체로
    // 찾으면 둘이 잡힌다. 여기서 보려는 것은 표가 그리는 쪽이다.
    const table = await screen.findByRole("table");
    expect(within(table).getByText("최초 입력")).toBeInTheDocument();
    expect(within(table).getByText("연봉 인상")).toBeInTheDocument();
    expect(within(table).getByText("—")).toBeInTheDocument();
  });

  it("열이 다섯인 표를 가로 스크롤 래퍼 안에 둔다 (c13)", async () => {
    // 래퍼가 없으면 375px 에서 한글이 글자 단위로 쪼개진다("변/경/사/유").
    vi.mocked(incomeApi.listIncome).mockResolvedValue(rows);
    renderTab();

    const table = await screen.findByRole("table");
    expect(table.parentElement).toHaveClass("overflow-x-auto");
  });

  it("최소 유효일 행에 고른 값이 있으면 표가 둘 다 낸다", async () => {
    // 가장 이른 행이라 표시는 INITIAL 이지만 컬럼에는 JOB_CHANGE 가 있다. 파생값만
    // 내면 사용자가 고른 값이 화면 어디에도 안 남는다 — 수정 화면에 들어가야만 보인다.
    vi.mocked(incomeApi.listIncome).mockResolvedValue([
      {
        ...rows[0],
        changeReason: "INITIAL",
        selectedChangeReason: "JOB_CHANGE",
      },
      rows[1],
    ]);
    renderTab();

    const table = await screen.findByRole("table");
    expect(within(table).getByText("최초 입력 · 이직")).toBeInTheDocument();
    // 고른 값이 없는 최소 유효일 행은 그대로 "최초 입력" 하나만 낸다.
    expect(within(table).queryByText("최초 입력")).toBeNull();
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

  it("입력 관리 h1 아래로 제목 단계가 이어진다", async () => {
    const Stub = createRoutesStub([
      {
        path: "/mypage/records",
        Component: RecordsLayout,
        children: [
          {
            index: true,
            Component: RecordsIncome,
            ErrorBoundary,
            loader: clientLoader,
            action: clientAction,
            HydrateFallback: () => null,
          },
        ],
      },
      { path: "/login", Component: () => <div>로그인 화면</div> },
    ]);
    render(<Stub initialEntries={["/mypage/records"]} />);

    expect(
      await screen.findByRole("heading", { level: 2, name: "소득 이력" }),
    ).toBeInTheDocument();
    expectHeadingOutline();
  });
});
