import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { UnauthorizedError } from "../../lib/coreApi";
import { resetSession, setSession } from "../../lib/session";
import OnboardingLayout, {
  clientLoader as onboardingLayoutLoader,
} from "./layout";
import Review, { clientLoader, clientAction } from "./review";
import * as profileApi from "../../lib/api/profile";
import type { ProfileResponse } from "../../lib/api/profile";
import * as incomeApi from "../../lib/api/income";
import * as expenseApi from "../../lib/api/expense";
import * as skillApi from "../../lib/skillApi";
import * as onboardingApi from "../../lib/onboardingApi";

// baseYearMonth 는 항상 "당월"이다(expense.tsx 와 같은 이유 — 화면에 월 선택 UI 가 없다).
// 구현의 계산 함수를 그대로 가져오면 구현이 틀려도 항상 통과하는 동어반복이 되므로,
// 여기서 독립적으로 계산해 기대값 오라클로 쓴다.
function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, "0")}`;
}

const COMPLETE_PROFILE: ProfileResponse = {
  jobFamily: "DEVELOPER",
  yearsOfExperience: 5,
  region: "서울",
  employmentType: "FULL_TIME",
  companySize: "MEDIUM",
};

// 완비 게이트가 실제로 네 필수 필드 전부를 보는지, 그리고 선택 필드
// (companySize)는 없어도 막지 않는지를 표 하나로 고정한다. jobFamily 만
// 비우는 단일 사례로는 region·employmentType 판정 코드를 지워도 초록불이
// 나온다 — 필수 필드 넷을 각각 단독으로 비우는 행이 있어야 그 삭제를 잡는다.
// companySize 행은 반대 방향의 회귀(선택 필드를 실수로 필수 목록에 넣는 것)를
// 잡는다.
const PROFILE_GATE_CASES: {
  name: string;
  overrides: Partial<ProfileResponse>;
  expectBlocked: boolean;
  expectCompanySizeInSummary?: boolean;
}[] = [
  {
    name: "jobFamily 없음",
    overrides: { jobFamily: null },
    expectBlocked: true,
  },
  {
    name: "yearsOfExperience 없음",
    overrides: { yearsOfExperience: null },
    expectBlocked: true,
  },
  { name: "region 없음", overrides: { region: null }, expectBlocked: true },
  {
    name: "employmentType 없음",
    overrides: { employmentType: null },
    expectBlocked: true,
  },
  {
    // 경계값 — 0년 경력은 유효한 값이지 미입력이 아니다. falsy 체크
    // (`!profile.yearsOfExperience`)로 판정하면 신입(0년) 사용자가 영원히
    // "미완"으로 오판되어(0 을 아무리 다시 저장해도 여전히 falsy) 제출할
    // 수 없는 막다른 상태에 빠진다.
    name: "yearsOfExperience=0(경계값, 유효한 값)",
    overrides: { yearsOfExperience: 0 },
    expectBlocked: false,
  },
  {
    // companySize 는 profileSchema 에서도 optional() 이다 — 없어도 완비로
    // 인정돼야 하고, 요약 문장에도 회사 규모 부분이 나타나지 않아야 한다.
    name: "companySize 없음(선택 필드, 완비로 인정)",
    overrides: { companySize: null },
    expectBlocked: false,
    expectCompanySizeInSummary: false,
  },
  {
    name: "전체 완비(companySize 포함)",
    overrides: {},
    expectBlocked: false,
    expectCompanySizeInSummary: true,
  },
];

const ONE_INCOME: incomeApi.IncomeResponse[] = [
  {
    id: "i-1",
    grossSalary: 65_000_000,
    netSalary: 4_200_000,
    effectiveDate: "2026-01-01",
    changeReason: "INITIAL",
    selectedChangeReason: null,
  },
];

const PARTIAL_EXPENSE = [
  {
    baseYearMonth: currentYearMonth(),
    items: [
      { category: "HOUSING", amount: 750_000, status: "USER_DEFINED" as const },
      {
        category: "TRANSPORT",
        amount: 120_000,
        status: "DEFAULT_CPI" as const,
      },
      { category: "MEDICAL", amount: 80_000, status: "DEFAULT_CPI" as const },
    ],
    weightStatus: "PARTIALLY_ESTIMATED",
  },
];

// 가중치는 config/default-expense-weights.yml 실값(소수 넷째 자리)이다.
// 리졸버는 여섯 카테고리를 전부 돌려준다 — 입력한 주거만 USER_DEFINED 이고
// 나머지는 입력 합계로 보간된 DEFAULT_CPI 다.
const APPLIED_USER_INPUT: expenseApi.AppliedExpenseResponse = {
  baseYearMonth: currentYearMonth(),
  weightVersion: 1,
  weightBasePeriod: "2025",
  amountBasis: "USER_INPUT",
  categories: [
    {
      category: "HOUSING",
      amount: 750_000,
      weight: 0.1795,
      weightStatus: "USER_DEFINED",
    },
    {
      category: "FOOD",
      amount: 1_300_000,
      weight: 0.3112,
      weightStatus: "DEFAULT_CPI",
    },
    {
      category: "TRANSPORT",
      amount: 120_000,
      weight: 0.1146,
      weightStatus: "DEFAULT_CPI",
    },
    {
      category: "MEDICAL",
      amount: 80_000,
      weight: 0.0774,
      weightStatus: "DEFAULT_CPI",
    },
    {
      category: "EDUCATION",
      amount: 60_000,
      weight: 0.071,
      weightStatus: "DEFAULT_CPI",
    },
    {
      category: "LEISURE",
      amount: 400_000,
      weight: 0.2463,
      weightStatus: "DEFAULT_CPI",
    },
  ],
};

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
    vi.spyOn(skillApi, "fetchUserSkills").mockResolvedValue(TWO_SKILLS);
    vi.spyOn(expenseApi, "getAppliedExpenses").mockResolvedValue(
      APPLIED_USER_INPUT,
    );
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

    expect(
      await screen.findByText(/개발.*경력 5년.*서울.*정규직/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/세전 연봉 65,000,000원.*월 실수령액 4,200,000원/),
    ).toBeInTheDocument();
    expect(screen.getByText(/3개 카테고리 입력 완료/)).toBeInTheDocument();
    expect(
      screen.getByText(/2개 등록.*Spring Boot.*TypeScript/),
    ).toBeInTheDocument();
  });

  // 브리프 2 — 대체 항목은 목록 조회가 아니라 GET /expenses/applied 로 그린다(US-O6).
  // 이름만으로는 어떤 수치로 지수가 계산되는지 모른다 — 실제로 적용된 금액을 붙인다.
  it("적용 금액은 당월을 명시해 조회한다", async () => {
    renderScreen();

    await screen.findByText(/세전 연봉/);
    // 생략하면 서버가 최신 이력 월로 대체한다 — 지난달 값이 이번 달 검토에 뜨면 안 된다.
    expect(expenseApi.getAppliedExpenses).toHaveBeenCalledWith(
      currentYearMonth(),
    );
  });

  it("USER_INPUT — 보간된 카테고리만 적용 금액과 함께 나온다", async () => {
    renderScreen();

    const section = (await screen.findByText(/기본값으로 대체된 항목/)).closest(
      "p",
    );
    expect(section).toHaveTextContent("교통 120,000원");
    expect(section).toHaveTextContent("의료 80,000원");
    expect(section).toHaveTextContent("입력하신 금액 합계에 맞춰 채운 값");
    // 입력한 주거·공공요금은 대체 항목이 아니다.
    expect(section).not.toHaveTextContent("주거·공공요금");
    // 보간 금액은 통계청 평균 그 자체가 아니다.
    expect(section).not.toHaveTextContent("통계청 평균");
  });

  it("USER_INPUT — 대체된 카테고리가 없으면 대체 항목 문구가 뜨지 않는다", async () => {
    vi.spyOn(expenseApi, "getAppliedExpenses").mockResolvedValue({
      ...APPLIED_USER_INPUT,
      categories: APPLIED_USER_INPUT.categories.map((category) => ({
        ...category,
        weightStatus: "USER_DEFINED",
      })),
    });

    renderScreen();

    await screen.findByText(/세전 연봉/);
    expect(
      screen.queryByText(/기본값으로 대체된 항목/),
    ).not.toBeInTheDocument();
    // 계산 제한 사항은 대체 항목 유무와 무관하게 항상 고정으로 뜬다.
    expect(
      screen.getByText(/세금, 성과급, 주식보상, 퇴직금은 추정하지 않으며/),
    ).toBeInTheDocument();
  });

  it("PROPAGATED — 이전 입력 금액이 그대로 적용된다고 밝히고 여섯 금액을 보여준다", async () => {
    vi.spyOn(expenseApi, "getAppliedExpenses").mockResolvedValue({
      ...APPLIED_USER_INPUT,
      amountBasis: "PROPAGATED",
      categories: APPLIED_USER_INPUT.categories.map((category) => ({
        ...category,
        weightStatus: "PROPAGATED",
      })),
    });

    renderScreen();

    const section = (
      await screen.findByText(/이전에 입력하신 소비 금액이 이번 달에 그대로/)
    ).closest("p");
    expect(section).toHaveTextContent("주거·공공요금 750,000원");
    expect(section).toHaveTextContent("여가·기타 400,000원");
    expect(
      screen.queryByText(/기본값으로 대체된 항목/),
    ).not.toBeInTheDocument();
  });

  it("NONE — 금액 없이 적용된 비중과 기준 연도만 보여준다", async () => {
    vi.spyOn(expenseApi, "getAppliedExpenses").mockResolvedValue({
      ...APPLIED_USER_INPUT,
      amountBasis: "NONE",
      categories: APPLIED_USER_INPUT.categories.map((category) => ({
        ...category,
        amount: null,
        weightStatus: "DEFAULT_CPI",
      })),
    });

    renderScreen();

    const section = (await screen.findByText(/소비 금액 기준이 없어/)).closest(
      "p",
    );
    expect(section).toHaveTextContent("2025년");
    expect(section).toHaveTextContent("주거·공공요금 17.95%");
    expect(section).toHaveTextContent("식비 31.12%");
    // 0원이나 평균 금액을 임의로 채우지 않는다 — 금액 표기가 하나도 없어야 한다.
    expect(section).not.toHaveTextContent(/\d원/);
  });

  it("적용 금액 조회가 실패해도 안내만 띄우고 제출은 막지 않는다", async () => {
    vi.spyOn(expenseApi, "getAppliedExpenses").mockRejectedValue(
      new Error("boom"),
    );

    renderScreen();

    expect(
      await screen.findByText(/적용 금액을 불러오지 못했습니다/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "제출하고 대시보드로" }),
    ).toBeInTheDocument();
  });

  it("적용 금액 조회가 401 이면 로그인 화면으로 보낸다", async () => {
    vi.spyOn(expenseApi, "getAppliedExpenses").mockRejectedValue(
      new UnauthorizedError(),
    );

    renderScreen();

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });

  // 브리프 3 — 필수 프로필 필드가 비어 있으면 제출 버튼 대신 해당 단계로 가는
  // 안내를 보여준다. 네 필수 필드 각각을 단독으로 비우는 행 + 0년 경력 경계값
  // + companySize(선택 필드) 행을 표로 돌려, 필드 하나를 실수로 판정에서
  // 빼거나(필수 필드 삭제) 실수로 판정에 더하는(선택 필드 오추가) 두 방향의
  // 회귀를 모두 잡는다.
  it.each(PROFILE_GATE_CASES)(
    "프로필 완비 판정 — $name",
    async ({ overrides, expectBlocked, expectCompanySizeInSummary }) => {
      vi.spyOn(profileApi, "getProfile").mockResolvedValue({
        ...COMPLETE_PROFILE,
        ...overrides,
      });

      renderScreen();
      await screen.findByText(/세전 연봉/);

      if (expectBlocked) {
        expect(
          screen.queryByRole("button", { name: "제출하고 대시보드로" }),
        ).not.toBeInTheDocument();
        expect(
          screen.getByRole("link", { name: "프로필 완료하러 가기" }),
        ).toHaveAttribute("href", "/onboarding/profile");
      } else {
        expect(
          await screen.findByRole("button", { name: "제출하고 대시보드로" }),
        ).toBeInTheDocument();
        expect(
          screen.queryByRole("link", { name: "프로필 완료하러 가기" }),
        ).not.toBeInTheDocument();
      }

      if (expectCompanySizeInSummary !== undefined) {
        const summary = screen.getByText(/개발.*경력.*서울.*정규직/);
        if (expectCompanySizeInSummary) {
          expect(summary).toHaveTextContent("중견·중소");
        } else {
          expect(summary).not.toHaveTextContent("중견·중소");
        }
      }
    },
  );

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

    await user.click(
      await screen.findByRole("button", { name: "제출하고 대시보드로" }),
    );

    expect(await screen.findByText("대시보드")).toBeInTheDocument();
    expect(advance).toHaveBeenCalledWith("COMPLETED");
  });

  // 팀 리드 지시 — 렌더가 버튼을 보여준 뒤에도(진입 시점엔 완비였다) 제출
  // 시점에 조건이 깨질 수 있다(다른 탭에서 소득 이력을 지우고 돌아와 제출).
  // `PATCH /v1/auth/onboarding`은 COMPLETED 를 무조건 받아 주므로, 이
  // 화면이 유일한 방어선이다 — clientAction 이 제출 시점에 다시 조회해
  // 스스로 거부해야 한다.
  it("렌더 시점엔 완비였어도 제출 시점에 조건이 깨졌으면 제출을 거부한다", async () => {
    vi.spyOn(incomeApi, "listIncome")
      .mockResolvedValueOnce(ONE_INCOME) // loader — 진입 시점엔 완비
      .mockResolvedValueOnce([]); // clientAction 재조회 — 그 사이 다른 탭에서 삭제됨
    // 성공 응답을 준다 — 그래야 게이트가 실제로 호출을 막는 것과, mock 없는
    // advanceOnboarding 이 진짜 fetch 로 떨어져 거부처럼 보이는 것을 구분할 수
    // 있다(가짜 fetch 실패도 같은 role="alert" 배너를 만들어 앞의 구성으로는
    // 이 테스트가 게이트를 지워도 통과했다).
    const advance = vi
      .spyOn(onboardingApi, "advanceOnboarding")
      .mockResolvedValue({ currentStep: "COMPLETED", completed: true });

    renderScreen();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "제출하고 대시보드로" }),
    );

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(advance).not.toHaveBeenCalled();
  });

  it("제출 성공 후 세션 캐시의 onboardingStep 을 COMPLETED 로 갱신한다", async () => {
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "COMPLETED",
      completed: true,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "제출하고 대시보드로" }),
    );
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

    await user.click(
      await screen.findByRole("button", { name: "제출하고 대시보드로" }),
    );

    const banner = await screen.findByRole("alert");
    expect(banner).toHaveTextContent(
      "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
  });

  it("각 항목의 '수정' 링크가 해당 단계로 이동한다", async () => {
    renderScreen();
    const user = userEvent.setup();

    await screen.findByText(/세전 연봉/);
    // 네 링크가 각자 자기 구간을 이름 짓는 접근성 이름(aria-label)을 갖는다 —
    // 화면에 보이는 글자는 전부 "수정"이라 위치로만 구분하면 스크린 리더
    // 사용자도, 이 테스트도 서로 쉽게 헷갈린다.
    expect(
      screen.getByRole("link", { name: "프로필 정보 수정" }),
    ).toHaveAttribute("href", "/onboarding/profile");
    expect(
      screen.getByRole("link", { name: "소득 정보 수정" }),
    ).toHaveAttribute("href", "/onboarding/income");
    expect(
      screen.getByRole("link", { name: "소비 정보 수정" }),
    ).toHaveAttribute("href", "/onboarding/expense");
    expect(
      screen.getByRole("link", { name: "스킬 정보 수정" }),
    ).toHaveAttribute("href", "/onboarding/skills");

    await user.click(screen.getByRole("link", { name: "소비 정보 수정" }));
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
        children: [
          {
            index: true,
            Component: Review,
            loader: clientLoader,
            action: clientAction,
          },
        ],
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

    await user.click(
      await screen.findByRole("link", { name: "프로필 정보 수정" }),
    );

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
    vi.spyOn(profileApi, "getProfile").mockRejectedValue(
      new UnauthorizedError(),
    );

    renderScreen();

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });

  describe("온보딩 완료(COMPLETED) 후 재진입 및 재시작", () => {
    beforeEach(() => {
      setSession({
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
      });
    });

    it("COMPLETED 사용자에게 완료 배너와 재시작 버튼을 보여준다", async () => {
      renderScreen();

      expect(await screen.findByText("온보딩 완료 상태")).toBeInTheDocument();
      expect(
        screen.getAllByRole("button", { name: "온보딩 처음부터 다시 시작" }),
      ).toHaveLength(2);
      expect(
        screen.getByRole("link", { name: "대시보드로 돌아가기" }),
      ).toHaveAttribute("href", "/dashboard");
    });

    it("'온보딩 처음부터 다시 시작'을 누르면 resetOnboarding 을 호출하고 프로필 화면으로 이동한다", async () => {
      const resetSpy = vi
        .spyOn(onboardingApi, "resetOnboarding")
        .mockResolvedValue({
          currentStep: "PROFILE",
          completed: false,
        });

      renderScreen();
      const user = userEvent.setup();

      const restartButtons = await screen.findAllByRole("button", {
        name: "온보딩 처음부터 다시 시작",
      });
      await user.click(restartButtons[0]);

      expect(resetSpy).toHaveBeenCalledTimes(1);
      expect(await screen.findByText("프로필 화면")).toBeInTheDocument();
    });

    it("재시작 실패 시 에러 메시지를 표시한다", async () => {
      vi.spyOn(onboardingApi, "resetOnboarding").mockRejectedValue(
        new Error("재시작 실패"),
      );

      renderScreen();
      const user = userEvent.setup();

      const restartButtons = await screen.findAllByRole("button", {
        name: "온보딩 처음부터 다시 시작",
      });
      await user.click(restartButtons[0]);

      const banner = await screen.findByRole("alert");
      expect(banner).toHaveTextContent("요청을 처리하지 못했습니다");
    });
  });
});
