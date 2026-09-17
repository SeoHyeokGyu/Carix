import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as incomeApi from "../../../lib/api/income";
import RecordsIncome, { clientAction, clientLoader } from "./income";

vi.mock("../../../lib/api/income");

const rows: incomeApi.IncomeResponse[] = [
  {
    id: "i-old",
    grossSalary: 58_000_000,
    netSalary: 3_800_000,
    effectiveDate: "2023-01-01",
  },
  {
    id: "i-new",
    grossSalary: 65_000_000,
    netSalary: 4_200_000,
    effectiveDate: "2024-01-01",
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

    expect(await screen.findByText("삭제하지 못했습니다.")).toBeInTheDocument();
    // 행별 삭제 실패가 목록 전체를 비우지 않는다 — 두 행 모두 남아 있다.
    expect(screen.getByText("2024-01-01")).toBeInTheDocument();
    expect(screen.getByText("2023-01-01")).toBeInTheDocument();
  });
});
