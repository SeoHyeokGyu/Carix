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
      {
        id: "i-1",
        grossSalary: 58_000_000,
        netSalary: 3_800_000,
        effectiveDate: "2023-01-01",
      },
      {
        id: "i-2",
        grossSalary: 65_000_000,
        netSalary: 4_200_000,
        effectiveDate: "2024-01-01",
      },
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
        {
          id: "i-1",
          grossSalary: 50_000_000,
          netSalary: 3_500_000,
          effectiveDate: "2024-01-01",
        },
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
      {
        id: "i-1",
        grossSalary: 50_000_000,
        netSalary: 3_500_000,
        effectiveDate: "2024-01-01",
      },
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

    const errorText = await screen.findByText(
      "같은 유효 시작일이 이미 있습니다.",
    );
    expect(errorText).toBeInTheDocument();

    // 텍스트만으로는 이 오류가 실제로 effectiveDate 필드에 붙었는지, 아니면
    // 일반 _form 배너로 떨어졌는지 구분되지 않는다(toUserMessage 의 _form 경로도
    // 같은 문자열을 만든다) — range 검증 테스트와 같은 패턴으로 aria-describedby
    // 배선을 직접 확인해야 이 어서션이 실제로 필드 착지를 핀한다.
    const dateInput = screen.getByLabelText("유효 시작일");
    expect(dateInput).toHaveAttribute("aria-describedby", errorText.id);

    // 입력값이 그대로 남아 있어야 한다.
    expect(screen.getByLabelText("세전 연봉 (원)")).toHaveValue(52_000_000);
    expect(screen.getByLabelText("월 실수령액 (원)")).toHaveValue(3_600_000);
    expect(screen.getByLabelText("유효 시작일")).toHaveValue("2024-01-01");
  });

  it("'다음' 을 누르면 advanceOnboarding 후 소비 화면으로 간다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([
      {
        id: "i-1",
        grossSalary: 50_000_000,
        netSalary: 3_500_000,
        effectiveDate: "2024-01-01",
      },
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

  // FIX 1 (Critical, 최종 리뷰) — REVIEW 단계를 지난 사용자가 검토 화면의 "수정"
  // 링크로 돌아와 "다음"을 다시 누르면, 예전에는 advanceOnboarding("EXPENSE") 을
  // 무조건 다시 불러 서버 단계를 REVIEW 에서 EXPENSE 로 되돌렸다(profile.test.tsx 의
  // 같은 이름 테스트와 같은 이유 — 반복되면 화면이 없는 SKILLS 로 사용자가 갇힌다).
  // "다음"은 여전히 소비 화면으로 데려가야 한다.
  it("이미 REVIEW 단계를 지난 사용자가 '다음'을 눌러도 온보딩 단계를 되돌리지 않는다", async () => {
    resetSession();
    setSession({
      status: "authenticated",
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "REVIEW",
    });
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([
      {
        id: "i-1",
        grossSalary: 50_000_000,
        netSalary: 3_500_000,
        effectiveDate: "2024-01-01",
      },
    ]);
    const advance = vi.spyOn(onboardingApi, "advanceOnboarding");

    renderScreen();
    const user = userEvent.setup();

    await screen.findByText("2024-01-01");
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("소비 화면")).toBeInTheDocument();
    expect(advance).not.toHaveBeenCalled();

    const { requireSession } = await import("../../lib/session");
    await expect(requireSession()).resolves.toMatchObject({
      status: "authenticated",
      onboardingStep: "REVIEW",
    });
  });

  it("삭제하면 deleteIncome 이 불리고 표에서 사라진다", async () => {
    vi.spyOn(incomeApi, "listIncome")
      .mockResolvedValueOnce([
        {
          id: "i-1",
          grossSalary: 50_000_000,
          netSalary: 3_500_000,
          effectiveDate: "2024-01-01",
        },
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

  it("수정하면 updateIncome 이 호출되고 표가 새 값으로 갱신된다", async () => {
    vi.spyOn(incomeApi, "listIncome")
      .mockResolvedValueOnce([
        {
          id: "i-1",
          grossSalary: 50_000_000,
          netSalary: 3_500_000,
          effectiveDate: "2024-01-01",
        },
      ])
      .mockResolvedValueOnce([
        {
          id: "i-1",
          grossSalary: 55_000_000,
          netSalary: 3_500_000,
          effectiveDate: "2024-01-01",
        },
      ]);
    const update = vi.spyOn(incomeApi, "updateIncome").mockResolvedValue({
      id: "i-1",
      grossSalary: 55_000_000,
      netSalary: 3_500_000,
      effectiveDate: "2024-01-01",
    });

    renderScreen();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "수정" }));

    // key={editingId} 리마운트로 defaultValue 가 기존 값(5천만 원)으로 다시
    // 채워진다 — 지우고 새 값을 넣는다.
    const grossInput = screen.getByLabelText("세전 연봉 (원)");
    await user.clear(grossInput);
    await user.type(grossInput, "55000000");
    await user.click(screen.getByRole("button", { name: "저장" }));

    // API 호출 인자를 직접 단언한다 — 호출 여부만으로는 create 로 잘못 착지해도
    // (또는 id 를 빠뜨려도) 통과할 수 있다.
    expect(update).toHaveBeenCalledWith("i-1", {
      grossSalary: 55_000_000,
      netSalary: 3_500_000,
      effectiveDate: "2024-01-01",
    });
    expect(
      await screen.findByText((55_000_000).toLocaleString()),
    ).toBeInTheDocument();
    expect(
      screen.queryByText((50_000_000).toLocaleString()),
    ).not.toBeInTheDocument();
  });

  // FIX 4c — 수정 성공 후 editingId 를 지우지 않으면 폼이 계속 편집
  // 모드(제출 버튼 "저장", "취소" 버튼 노출)에 머물고, 숨은 intent=update·id 필드가
  // 방금 수정한 행의 id 를 계속 가리킨다. 그 상태로 같은 행을 삭제하면(다른, 독립된
  // deleteFetcher 를 통해서긴 하지만) 위쪽 폼은 여전히 이미 사라진 그 id 로
  // "저장"을 다시 보낼 준비가 된 채 남는다. 수정 성공 뒤 편집 모드가 실제로
  // 풀리는지("취소"가 사라지고 제출 버튼이 "추가"로 돌아오는지), 그리고 그 뒤 같은
  // 행을 삭제해도 올바른 id 로 나가는지를 함께 확인한다.
  it("수정 성공 후 편집 모드가 풀리고, 그 뒤 같은 행을 삭제해도 문제없이 지워진다", async () => {
    vi.spyOn(incomeApi, "listIncome")
      .mockResolvedValueOnce([
        {
          id: "i-1",
          grossSalary: 50_000_000,
          netSalary: 3_500_000,
          effectiveDate: "2024-01-01",
        },
      ])
      .mockResolvedValueOnce([
        {
          id: "i-1",
          grossSalary: 55_000_000,
          netSalary: 3_500_000,
          effectiveDate: "2024-01-01",
        },
      ])
      .mockResolvedValueOnce([]);
    vi.spyOn(incomeApi, "updateIncome").mockResolvedValue({
      id: "i-1",
      grossSalary: 55_000_000,
      netSalary: 3_500_000,
      effectiveDate: "2024-01-01",
    });
    const del = vi.spyOn(incomeApi, "deleteIncome").mockResolvedValue(null);

    renderScreen();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "수정" }));
    expect(screen.getByRole("button", { name: "취소" })).toBeInTheDocument();

    const grossInput = screen.getByLabelText("세전 연봉 (원)");
    await user.clear(grossInput);
    await user.type(grossInput, "55000000");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await screen.findByText((55_000_000).toLocaleString());

    // 편집 모드가 풀려야 한다 — "취소" 버튼이 사라지고 제출 버튼이 "추가"로
    // 되돌아온다. 풀리지 않으면(버그) "저장" 버튼과 "취소" 버튼이 그대로 남는다.
    expect(
      screen.queryByRole("button", { name: "취소" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "추가" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "삭제" }));

    expect(del).toHaveBeenCalledWith("i-1");
    await screen.findByText("등록된 이력이 없습니다.");
  });

  it("수정이 API 오류로 실패하면 오류를 보여주고 표는 원래 값을 유지한다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([
      {
        id: "i-1",
        grossSalary: 50_000_000,
        netSalary: 3_500_000,
        effectiveDate: "2024-01-01",
      },
    ]);
    const update = vi.spyOn(incomeApi, "updateIncome").mockRejectedValue({
      code: "SERVER_ERROR",
      message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      status: 500,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "수정" }));

    const grossInput = screen.getByLabelText("세전 연봉 (원)");
    await user.clear(grossInput);
    await user.type(grossInput, "60000000");
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(update).toHaveBeenCalledWith("i-1", {
      grossSalary: 60_000_000,
      netSalary: 3_500_000,
      effectiveDate: "2024-01-01",
    });

    const banner = await screen.findByRole("alert");
    expect(banner).toHaveTextContent(
      "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
    // Field 의 필드별 오류 문단은 항상 `id={field}-error` 를 갖는다 — 이 오류가
    // 특정 필드가 아니라 폼 전체(_form) 배너로 떨어졌는지는 id 부재로만
    // 구분된다(FIX 2 와 같은 이유 — 텍스트만으로는 착지 위치를 핀하지 못한다).
    expect(banner).not.toHaveAttribute("id");
    // 서버가 거부했으므로 표는 갱신되지 않고 원래 값이 남아 있어야 한다.
    expect(screen.getByText((50_000_000).toLocaleString())).toBeInTheDocument();
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
    vi.spyOn(incomeApi, "listIncome").mockRejectedValue(
      new UnauthorizedError(),
    );

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
      {
        id: "i-1",
        grossSalary: 50_000_000,
        netSalary: 3_500_000,
        effectiveDate: "2024-01-01",
      },
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
    expect(banner).toHaveTextContent(
      "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
    // 행이 그대로 남아 있다 — 삭제되지 않았다.
    expect(screen.getByText("2024-01-01")).toBeInTheDocument();
  });
});
