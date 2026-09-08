import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { UnauthorizedError } from "../../lib/coreApi";
import { resetSession, setSession } from "../../lib/session";
import Income, { clientLoader, clientAction } from "./income";
import * as incomeApi from "../../lib/api/income";
import * as onboardingApi from "../../lib/onboardingApi";

describe("S-06 소득 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    // 세션 캐시는 모듈 전역이라 앞 테스트 값을 물려받을 수 있다 — 매번 지우고 화면이
    // 실제로 렌더되는 상황과 같게 채운다.
    resetSession();
    setSession({
      status: "authenticated",
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "INCOME",
    });
  });

  function renderScreen() {
    const Stub = createRoutesStub([
      {
        path: "/onboarding/income",
        Component: Income,
        loader: clientLoader,
        action: clientAction,
        HydrateFallback: () => null,
      },
      { path: "/onboarding/expense", Component: () => <div>소비 화면</div> },
      { path: "/onboarding/profile", Component: () => <div>프로필 화면</div> },
      { path: "/login", Component: () => <div>로그인 화면</div> },
    ]);
    return render(<Stub initialEntries={["/onboarding/income"]} />);
  }

  it("기존 이력이 표에 유효 시작일 내림차순으로 나온다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([
      { id: "i-1", grossSalary: 58_000_000, netSalary: 3_800_000, effectiveDate: "2023-01-01" },
      { id: "i-2", grossSalary: 65_000_000, netSalary: 4_200_000, effectiveDate: "2024-01-01" },
    ]);

    renderScreen();

    const rows = await screen.findAllByRole("row");
    // 첫 행은 헤더 — 본문 행은 2024-01-01, 2023-01-01 순으로 나와야 한다(내림차순).
    expect(rows[1]).toHaveTextContent("2024-01-01");
    expect(rows[2]).toHaveTextContent("2023-01-01");
  });

  it("이력을 추가하면 createIncome 이 불리고 표가 갱신된다", async () => {
    vi.spyOn(incomeApi, "listIncome")
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        { id: "i-1", grossSalary: 50_000_000, netSalary: 3_500_000, effectiveDate: "2024-01-01" },
      ]);
    const create = vi.spyOn(incomeApi, "createIncome").mockResolvedValue({
      id: "i-1",
      grossSalary: 50_000_000,
      netSalary: 3_500_000,
      effectiveDate: "2024-01-01",
    });

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("세전 연봉 (원)"), "50000000");
    await user.type(screen.getByLabelText("월 실수령액 (원)"), "3500000");
    await user.type(screen.getByLabelText("유효 시작일"), "2024-01-01");
    await user.click(screen.getByRole("button", { name: "추가" }));

    expect(create).toHaveBeenCalledWith({
      grossSalary: 50_000_000,
      netSalary: 3_500_000,
      effectiveDate: "2024-01-01",
    });
    expect(await screen.findByText("2024-01-01")).toBeInTheDocument();
  });

  it("같은 유효 시작일로 추가하면 필드 오류를 보여주고 입력값을 지우지 않는다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([
      { id: "i-1", grossSalary: 50_000_000, netSalary: 3_500_000, effectiveDate: "2024-01-01" },
    ]);
    vi.spyOn(incomeApi, "createIncome").mockRejectedValue({
      code: "DUPLICATE_EFFECTIVE_DATE",
      message: "같은 유효 시작일이 이미 있습니다.",
      status: 409,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("세전 연봉 (원)"), "52000000");
    await user.type(screen.getByLabelText("월 실수령액 (원)"), "3600000");
    await user.type(screen.getByLabelText("유효 시작일"), "2024-01-01");
    await user.click(screen.getByRole("button", { name: "추가" }));

    const errorText = await screen.findByText("같은 유효 시작일이 이미 있습니다.");
    expect(errorText).toBeInTheDocument();

    // 입력값이 그대로 남아 있어야 한다.
    expect(screen.getByLabelText("세전 연봉 (원)")).toHaveValue(52_000_000);
    expect(screen.getByLabelText("월 실수령액 (원)")).toHaveValue(3_600_000);
    expect(screen.getByLabelText("유효 시작일")).toHaveValue("2024-01-01");
  });

  it("'다음' 을 누르면 advanceOnboarding 후 소비 화면으로 간다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([
      { id: "i-1", grossSalary: 50_000_000, netSalary: 3_500_000, effectiveDate: "2024-01-01" },
    ]);
    const advance = vi
      .spyOn(onboardingApi, "advanceOnboarding")
      .mockResolvedValue({ currentStep: "EXPENSE", completed: false });

    renderScreen();
    const user = userEvent.setup();

    await screen.findByText("2024-01-01");
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("소비 화면")).toBeInTheDocument();
    expect(advance).toHaveBeenCalledWith("EXPENSE");
  });

  // 회귀 테스트 — guardOnboarding 은 세션 캐시의 onboardingStep 을 읽는다. "다음" 으로
  // 단계를 넘긴 뒤 이 캐시를 갱신하지 않으면 되튕김이 생긴다.
  it("'다음' 성공 후 세션 캐시의 onboardingStep 을 EXPENSE 로 갱신한다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([]);
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "EXPENSE",
      completed: false,
    });

    renderScreen();
    const user = userEvent.setup();

    await screen.findByText("소득 관리");
    await user.click(screen.getByRole("button", { name: "다음" }));

    await screen.findByText("소비 화면");

    const { requireSession } = await import("../../lib/session");
    await expect(requireSession()).resolves.toMatchObject({
      status: "authenticated",
      onboardingStep: "EXPENSE",
    });
  });

  it("삭제하면 deleteIncome 이 불리고 표에서 사라진다", async () => {
    vi.spyOn(incomeApi, "listIncome")
      .mockResolvedValueOnce([
        { id: "i-1", grossSalary: 50_000_000, netSalary: 3_500_000, effectiveDate: "2024-01-01" },
      ])
      .mockResolvedValueOnce([]);
    const del = vi.spyOn(incomeApi, "deleteIncome").mockResolvedValue(null);

    renderScreen();
    const user = userEvent.setup();

    await screen.findByText("2024-01-01");
    await user.click(screen.getByRole("button", { name: "삭제" }));

    expect(del).toHaveBeenCalledWith("i-1");
    await screen.findByText("등록된 이력이 없습니다.");
  });

  it("'이전' 을 누르면 프로필 화면으로 이동한다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([]);

    renderScreen();
    const user = userEvent.setup();

    await screen.findByText("소득 관리");
    await user.click(screen.getByRole("link", { name: "이전" }));

    expect(await screen.findByText("프로필 화면")).toBeInTheDocument();
  });

  it("조회 중 401 을 받으면 로그인 화면으로 보낸다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockRejectedValue(new UnauthorizedError());

    renderScreen();

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });

  // profile.test.tsx 의 "총 경력이 범위를 벗어나면" 테스트와 같은 모양 — 필드별 메시지·
  // aria-describedby 배선·포커스 이동을 함께 확인한다.
  it("세전 연봉이 범위를 벗어나면 저장하지 않고 필드 오류와 포커스를 보여준다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([]);
    const create = vi.spyOn(incomeApi, "createIncome");

    renderScreen();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("세전 연봉 (원)"), "1000");
    await user.type(screen.getByLabelText("월 실수령액 (원)"), "3500000");
    await user.type(screen.getByLabelText("유효 시작일"), "2024-01-01");
    await user.click(screen.getByRole("button", { name: "추가" }));

    // 라벨("세전 연봉 (원)")도 부분 문자열을 포함해 느슨한 정규식은 다중 매치로 깨진다
    // — 필드 에러 메시지 전체를 정확히 매칭한다.
    const errorText = await screen.findByText(
      "세전 연봉은 1천만 원~30억 원 사이로 입력해 주세요.",
    );
    expect(errorText).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();

    const invalidField = screen.getByLabelText("세전 연봉 (원)");
    expect(invalidField).toHaveAttribute("aria-describedby", errorText.id);
    expect(invalidField).toHaveFocus();
  });

  // Critical — 행 삭제가 401 이 아닌 이유로 실패하면(서버 오류 등) 오류를 화면에
  // 보여줘야 한다. 조용히 사라지면 사용자는 삭제가 됐는지 실패했는지 알 수 없다.
  it("삭제가 API 오류로 실패하면 행에 오류 메시지를 보여준다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([
      { id: "i-1", grossSalary: 50_000_000, netSalary: 3_500_000, effectiveDate: "2024-01-01" },
    ]);
    vi.spyOn(incomeApi, "deleteIncome").mockRejectedValue({
      code: "SERVER_ERROR",
      message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      status: 500,
    });

    renderScreen();
    const user = userEvent.setup();

    await screen.findByText("2024-01-01");
    await user.click(screen.getByRole("button", { name: "삭제" }));

    const banner = await screen.findByRole("alert");
    expect(banner).toHaveTextContent("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    // 행이 그대로 남아 있다 — 삭제되지 않았다.
    expect(screen.getByText("2024-01-01")).toBeInTheDocument();
  });
});
