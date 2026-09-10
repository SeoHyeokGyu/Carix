import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { UnauthorizedError } from "../../lib/coreApi";
import { resetSession, setSession } from "../../lib/session";
import OnboardingLayout, { clientLoader as onboardingLayoutLoader } from "./layout";
import Review, { clientLoader, clientAction } from "./review";
import * as profileApi from "../../lib/api/profile";
import * as incomeApi from "../../lib/api/income";
import * as expenseApi from "../../lib/api/expense";
import * as userSkillsApi from "../../lib/api/userSkills";
import * as onboardingApi from "../../lib/onboardingApi";

// baseYearMonth 는 항상 "당월"이다(expense.tsx 와 같은 이유 — 화면에 월 선택 UI 가 없다).
// 구현의 계산 함수를 그대로 가져오면 구현이 틀려도 항상 통과하는 동어반복이 되므로,
// 여기서 독립적으로 계산해 기대값 오라클로 쓴다.
function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, "0")}`;
}

const COMPLETE_PROFILE = {
  jobFamily: "DEVELOPER",
  yearsOfExperience: 5,
  region: "서울",
  employmentType: "FULL_TIME",
  companySize: "MEDIUM",
};

const ONE_INCOME = [
  { id: "i-1", grossSalary: 65_000_000, netSalary: 4_200_000, effectiveDate: "2026-01-01" },
];

const PARTIAL_EXPENSE = [
  {
    baseYearMonth: currentYearMonth(),
    items: [
      { category: "HOUSING", amount: 750_000, status: "USER_DEFINED" as const },
      { category: "TRANSPORT", amount: 120_000, status: "DEFAULT_CPI" as const },
      { category: "MEDICAL", amount: 80_000, status: "DEFAULT_CPI" as const },
    ],
    weightStatus: "PARTIALLY_ESTIMATED",
  },
];

const TWO_SKILLS = [
  {
    id: "us-1",
    skillId: "s-1",
    standardName: "Spring Boot",
    isCustom: false,
    monthsOfExperience: 36,
    lastUsedDate: "2026-08-01",
  },
  {
    id: "us-2",
    skillId: "s-2",
    standardName: "TypeScript",
    isCustom: false,
    monthsOfExperience: 24,
    lastUsedDate: "2026-08-01",
  },
];

describe("S-09 검토 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    resetSession();
    setSession({
      status: "authenticated",
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "REVIEW",
    });
    vi.spyOn(profileApi, "getProfile").mockResolvedValue(COMPLETE_PROFILE);
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue(ONE_INCOME);
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue(PARTIAL_EXPENSE);
    vi.spyOn(userSkillsApi, "listUserSkills").mockResolvedValue(TWO_SKILLS);
  });

  function renderScreen() {
    const Stub = createRoutesStub([
      {
        path: "/onboarding/review",
        Component: Review,
        loader: clientLoader,
        action: clientAction,
        HydrateFallback: () => null,
      },
      { path: "/onboarding/profile", Component: () => <div>프로필 화면</div> },
      { path: "/onboarding/income", Component: () => <div>소득 화면</div> },
      { path: "/onboarding/expense", Component: () => <div>소비 화면</div> },
      { path: "/onboarding/skills", Component: () => <div>스킬 화면</div> },
      { path: "/dashboard", Component: () => <div>대시보드</div> },
      { path: "/login", Component: () => <div>로그인 화면</div> },
    ]);
    return render(<Stub initialEntries={["/onboarding/review"]} />);
  }

  // 브리프 1 — 네 요약 카드를 loader 가 모은 데이터로 그린다.
  it("loader 가 모은 데이터로 네 요약 카드를 그린다", async () => {
    renderScreen();

    expect(await screen.findByText(/개발.*경력 5년.*서울.*정규직/)).toBeInTheDocument();
    expect(
      screen.getByText(/세전 연봉 65,000,000원.*월 실수령액 4,200,000원/),
    ).toBeInTheDocument();
    expect(screen.getByText(/3개 카테고리 입력 완료/)).toBeInTheDocument();
    expect(screen.getByText(/2개 등록.*Spring Boot.*TypeScript/)).toBeInTheDocument();
  });

  // 브리프 2 — status: DEFAULT_CPI 인 카테고리 이름이 「기본값으로 대체된 항목」에 나온다.
  // 문구를 하드코딩하지 않는다 — 서버가 돌려준 status 로만 만든다.
  it("DEFAULT_CPI 카테고리 이름이 기본값으로 대체된 항목에 나온다", async () => {
    renderScreen();

    await screen.findByText(/세전 연봉/);
    const defaultSection = screen.getByText(/기본값으로 대체된 항목/).closest("p");
    expect(defaultSection).toHaveTextContent("교통");
    expect(defaultSection).toHaveTextContent("의료");
    // USER_DEFINED(주거·공공요금)는 이 목록에 없어야 한다.
    expect(defaultSection).not.toHaveTextContent("주거·공공요금");
  });

  it("DEFAULT_CPI 카테고리가 없으면 기본값으로 대체된 항목 문구가 뜨지 않는다", async () => {
    vi.spyOn(expenseApi, "listExpenses").mockResolvedValue([
      {
        baseYearMonth: currentYearMonth(),
        items: [{ category: "HOUSING", amount: 750_000, status: "USER_DEFINED" }],
        weightStatus: "OK",
      },
    ]);

    renderScreen();

    await screen.findByText(/세전 연봉/);
    expect(screen.queryByText(/기본값으로 대체된 항목/)).not.toBeInTheDocument();
    // 계산 제한 사항은 대체 항목 유무와 무관하게 항상 고정으로 뜬다.
    expect(
      screen.getByText(/세금, 성과급, 주식보상, 퇴직금은 추정하지 않으며/),
    ).toBeInTheDocument();
  });

  // 브리프 3 — 필수 프로필 필드가 비어 있으면 제출 버튼 대신 해당 단계로 가는 안내를 보여준다.
  it("필수 프로필 필드가 비어 있으면 제출 버튼 대신 프로필 단계로 가는 안내를 보여준다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      ...COMPLETE_PROFILE,
      jobFamily: null,
    });

    renderScreen();

    await screen.findByText(/세전 연봉/);
    expect(
      screen.queryByRole("button", { name: "제출하고 대시보드로" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "프로필 완료하러 가기" }),
    ).toHaveAttribute("href", "/onboarding/profile");
  });

  // 경계값 — 0년 경력은 유효한 값이지 미입력이 아니다. `yearsOfExperience`를
  // falsy 체크(`!profile.yearsOfExperience`)로 판정하면 신입(0년) 사용자가
  // 영원히 "미완"으로 오판되어 프로필을 아무리 다시 저장해도(0을 그대로
  // 다시 보내므로) 제출할 수 없는 막다른 상태에 빠진다. `!== null` 로만
  // 판정해야 한다.
  it("경력이 0년인 사용자도 프로필 완비로 인정되어 제출 버튼이 보인다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      ...COMPLETE_PROFILE,
      yearsOfExperience: 0,
    });

    renderScreen();

    expect(
      await screen.findByRole("button", { name: "제출하고 대시보드로" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "프로필 완료하러 가기" }),
    ).not.toBeInTheDocument();
  });

  // 브리프 5 — 소득 이력이 없으면 소득 카드가 「입력 필요」로 표시되고 제출이 막힌다.
  it("소득 이력이 없으면 소득 카드가 입력 필요로 표시되고 제출이 막힌다", async () => {
    vi.spyOn(incomeApi, "listIncome").mockResolvedValue([]);

    renderScreen();

    await screen.findByText(/개발.*경력 5년/);
    expect(screen.getByText("입력 필요")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "제출하고 대시보드로" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "소득 완료하러 가기" }),
    ).toHaveAttribute("href", "/onboarding/income");
  });

  // 브리프 4 — 제출하면 advanceOnboarding("COMPLETED") 후 /dashboard 로 간다.
  it("제출하면 advanceOnboarding COMPLETED 후 대시보드로 이동한다", async () => {
    const advance = vi
      .spyOn(onboardingApi, "advanceOnboarding")
      .mockResolvedValue({ currentStep: "COMPLETED", completed: true });

    renderScreen();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "제출하고 대시보드로" }));

    expect(await screen.findByText("대시보드")).toBeInTheDocument();
    expect(advance).toHaveBeenCalledWith("COMPLETED");
  });

  it("제출 성공 후 세션 캐시의 onboardingStep 을 COMPLETED 로 갱신한다", async () => {
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "COMPLETED",
      completed: true,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "제출하고 대시보드로" }));
    await screen.findByText("대시보드");

    const { requireSession } = await import("../../lib/session");
    await expect(requireSession()).resolves.toMatchObject({
      status: "authenticated",
      onboardingStep: "COMPLETED",
    });
  });

  it("제출이 API 오류로 실패하면 오류 메시지를 보여준다", async () => {
    vi.spyOn(onboardingApi, "advanceOnboarding").mockRejectedValue({
      code: "SERVER_ERROR",
      message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      status: 500,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "제출하고 대시보드로" }));

    const banner = await screen.findByRole("alert");
    expect(banner).toHaveTextContent("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
  });

  it("각 항목의 '수정' 링크가 해당 단계로 이동한다", async () => {
    renderScreen();
    const user = userEvent.setup();

    const editLinks = await screen.findAllByRole("link", { name: "수정" });
    expect(editLinks).toHaveLength(4);
    expect(editLinks[0]).toHaveAttribute("href", "/onboarding/profile");
    expect(editLinks[1]).toHaveAttribute("href", "/onboarding/income");
    expect(editLinks[2]).toHaveAttribute("href", "/onboarding/expense");
    expect(editLinks[3]).toHaveAttribute("href", "/onboarding/skills");

    await user.click(editLinks[2]);
    expect(await screen.findByText("소비 화면")).toBeInTheDocument();
  });

  // 팀 리드 지시 — task-8 에서 guardOnboarding 이 "현재 단계와 같거나 이전 단계는
  // 허용"으로 완화된 것은 바로 이 화면의 "수정" 링크를 위해서였다(task-8-report.md
  // 참고). 위 테스트는 스텁이라 실제 가드를 거치지 않는다 — 이 테스트는 실제
  // guardOnboarding(및 onboarding/layout.tsx 의 clientLoader)을 그대로 통과시켜,
  // REVIEW 단계 세션이 "수정"으로 더 이전 단계(프로필)에 진입해도 튕기지 않는지
  // 직접 확인한다. 가드가 다시 엄격한 "현재 단계만 허용" 정책으로 되돌아가면 이
  // 테스트가 깨진다.
  it("'수정' 링크는 실제 guardOnboarding 정책 위에서도 이전 단계로 튕기지 않고 이동한다", async () => {
    const Stub = createRoutesStub([
      {
        path: "/onboarding/review",
        loader: onboardingLayoutLoader,
        HydrateFallback: () => null,
        Component: OnboardingLayout,
        children: [{ index: true, Component: Review, loader: clientLoader, action: clientAction }],
      },
      {
        path: "/onboarding/profile",
        loader: onboardingLayoutLoader,
        HydrateFallback: () => null,
        Component: () => <div>프로필 화면</div>,
      },
    ]);

    render(<Stub initialEntries={["/onboarding/review"]} />);
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("link", { name: "수정" }))[0]);

    expect(await screen.findByText("프로필 화면")).toBeInTheDocument();
  });

  it("'이전' 을 누르면 스킬 화면으로 이동한다", async () => {
    renderScreen();
    const user = userEvent.setup();

    await screen.findByText(/세전 연봉/);
    await user.click(screen.getByRole("link", { name: "이전" }));

    expect(await screen.findByText("스킬 화면")).toBeInTheDocument();
  });

  it("조회 중 401 을 받으면 로그인 화면으로 보낸다", async () => {
    vi.spyOn(profileApi, "getProfile").mockRejectedValue(new UnauthorizedError());

    renderScreen();

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });
});
