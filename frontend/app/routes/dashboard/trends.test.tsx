import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { UnauthorizedError } from "../../lib/coreApi";
import * as dashboardMarketApi from "../../lib/dashboardMarketApi";
import DashboardTrends from "./trends";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("../../lib/dashboardMarketApi", () => ({
  fetchSalaryDistribution: vi.fn(),
  fetchTrends: vi.fn(),
  fetchWageGrowth: vi.fn(),
}));

const WAGE_GROWTH_OK: dashboardMarketApi.WageGrowthResponse = {
  status: "OK",
  bracket: "MID",
  basis: "AGE_BAND_PROXY",
  source: "KOSIS",
  firstStatYear: "2021",
  latestStatYear: "2025",
  lastRecordedYear: "2025",
  latest: {
    fromYear: "2024",
    toYear: "2025",
    myGrowthPercent: 5,
    marketGrowthPercent: 3,
    gapPp: 2,
  },
  runs: [
    {
      points: [
        {
          year: "2024",
          myIndex: 100,
          marketIndex: 100,
          recorded: true,
          myGross: 40_000_000,
          marketMedian: 10_000_000,
        },
        {
          year: "2025",
          myIndex: 105,
          marketIndex: 103,
          recorded: true,
          myGross: 42_000_000,
          marketMedian: 10_300_000,
        },
      ],
      spans: [
        {
          fromYear: "2024",
          toYear: "2025",
          myGrowthPercent: 5,
          marketGrowthPercent: 3,
        },
      ],
    },
  ],
};

const WAGE_GROWTH_EMPTY: dashboardMarketApi.WageGrowthResponse = {
  ...WAGE_GROWTH_OK,
  status: "NO_INCOME_HISTORY",
  latest: null,
  runs: [],
};

/** a 가 문서에서 b 보다 앞이면 참. */
function isBefore(a: HTMLElement, b: HTMLElement): boolean {
  return Boolean(
    a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING,
  );
}

function sectionHeading(name: string): HTMLElement {
  return screen.getByRole("heading", { level: 2, name });
}

function renderTrends(path = "/dashboard/trends") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/dashboard/trends" element={<DashboardTrends />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("S-14 DashboardTrends 라우트", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockResolvedValue({
      status: "OK",
      sampleSize: 80,
      baseline: {
        basis: "AGE_BAND_PROXY",
        source: "KOSIS",
        median: 60_000_000,
        wageDefinition: "임금통계",
      },
      postingReference: {
        min: 35_000_000,
        p25: 48_000_000,
        median: 62_000_000,
        p75: 75_000_000,
        max: 95_000_000,
        sampleSize: 30,
        missingRate: 62.5,
      },
      userPosition: {
        grossSalary: 65_000_000,
        percentile: 55,
      },
      peerGroup: {
        fallbackLevel: 1,
        appliedConditions: "수도권 / 경력 3년 이상",
      },
    });

    vi.mocked(dashboardMarketApi.fetchWageGrowth).mockResolvedValue(
      WAGE_GROWTH_OK,
    );

    vi.mocked(dashboardMarketApi.fetchTrends).mockResolvedValue({
      trends: [
        {
          yearMonth: "202601",
          incomeIndex: 100,
          personalPriceIndex: 100,
          purchasingPowerIndex: 100,
          marketValueIndex: 100,
          nominalIncome: 60000000,
          realIncome: 60000000,
        },
        {
          yearMonth: "202602",
          incomeIndex: 102,
          personalPriceIndex: 101,
          purchasingPowerIndex: 100.9,
          marketValueIndex: 102,
          nominalIncome: 61200000,
          realIncome: 60500000,
        },
      ],
      purchasingPowerStatus: "OK",
      marketValueStatus: "OK",
    });
  });

  it("탭 없이 지수 추이와 연봉 분포를 한 화면에 함께 그리고 화면 ID 는 보이지 않는다", async () => {
    renderTrends();

    expect(screen.queryByText("S-14")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "추이 분석" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("tab")).not.toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId("income-vs-price-chart")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("heading", { level: 2, name: "동료집단 연봉 분포" }),
    ).toBeInTheDocument();
  });

  // 데이터가 온 뒤에야 섹션이 그려져 브라우저의 기본 앵커 이동이 닿지 않는다.
  it("#salary 로 들어오면 데이터가 온 뒤 연봉 분포 섹션으로 스크롤한다", async () => {
    const scrolled: string[] = [];
    Element.prototype.scrollIntoView = vi.fn(function (this: Element) {
      scrolled.push(this.id);
    });

    renderTrends("/dashboard/trends#salary");

    await waitFor(() => expect(scrolled).toEqual(["salary"]));
  });

  // 세션이 죽으면 두 호출이 동시에 401 이다. 부분 성공 렌더링과 갈라 둬야 한다 —
  // 한 API 만 죽은 경우에 멀쩡한 화면을 로그인으로 내쫓으면 안 된다.
  it("세션이 만료되면 로그인으로 보낸다", async () => {
    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockRejectedValue(
      new UnauthorizedError(),
    );
    vi.mocked(dashboardMarketApi.fetchTrends).mockRejectedValue(
      new UnauthorizedError(),
    );

    render(
      <MemoryRouter>
        <DashboardTrends />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/login", { replace: true });
    });
  });

  it("한 원천만 실패하면 로그인으로 보내지 않고 나머지를 그린다", async () => {
    vi.mocked(dashboardMarketApi.fetchTrends).mockRejectedValue({
      code: "UNKNOWN",
      message: "서버 오류",
      status: 500,
    });

    render(
      <MemoryRouter>
        <DashboardTrends />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(dashboardMarketApi.fetchSalaryDistribution).toHaveBeenCalled();
    });
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(
      screen.queryByText("시장 분석 데이터를 불러오지 못했습니다"),
    ).not.toBeInTheDocument();
  });

  // 이미 받은 데이터가 있으면 다시 불러오기가 모두 실패해도 화면을 비우지 않는다.
  it("일부 성공 뒤 다시 불러오기가 모두 실패하면 받은 데이터를 남기고 한 줄로 알린다", async () => {
    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockRejectedValue(
      new Error("x"),
    );
    renderTrends();
    const user = userEvent.setup();
    await screen.findByTestId("income-vs-price-chart");

    // 실패한 연봉 분포 섹션에서 다시 시도 — 이번엔 지수 추이까지 둘 다 실패한다.
    vi.mocked(dashboardMarketApi.fetchTrends).mockRejectedValue(new Error("x"));
    vi.mocked(dashboardMarketApi.fetchWageGrowth).mockRejectedValue(
      new Error("x"),
    );
    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "시장 분석 데이터를 불러오지 못했습니다",
    );
    // 받아 둔 지수 추이 차트는 그대로 남고, 다시 시도는 알림 한 곳에만 있다.
    expect(screen.getByTestId("income-vs-price-chart")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "다시 시도" })).toHaveLength(
      1,
    );
  });

  it("세 조회가 모두 실패하면 화면 단위 실패 하나만 보이고 다시 시도할 수 있다", async () => {
    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockRejectedValue(
      new Error("x"),
    );
    vi.mocked(dashboardMarketApi.fetchTrends).mockRejectedValue(new Error("x"));
    vi.mocked(dashboardMarketApi.fetchWageGrowth).mockRejectedValue(
      new Error("x"),
    );
    renderTrends();
    const user = userEvent.setup();

    expect(
      await screen.findByText("시장 분석 데이터를 불러오지 못했습니다"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/추이 데이터를 불러오지 못했습니다/)).toBeNull();
    expect(screen.getAllByRole("button", { name: "다시 시도" })).toHaveLength(
      1,
    );
    // 화면 전체 실패는 섹션마다 실패를 그리지 않는다.
    expect(screen.queryByTestId("income-vs-price-chart")).toBeNull();

    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await waitFor(() =>
      expect(dashboardMarketApi.fetchTrends).toHaveBeenCalledTimes(2),
    );
  });

  it("비교할 수 있으면 지수 추이와 연봉 분포 사이에 둔다", async () => {
    renderTrends();

    await waitFor(() => {
      expect(screen.getByTestId("wage-growth-section")).toBeInTheDocument();
    });
    const growth = sectionHeading("상승률 비교");
    expect(isBefore(growth, sectionHeading("동료집단 연봉 분포"))).toBe(true);
    expect(isBefore(screen.getByTestId("income-vs-price-chart"), growth)).toBe(
      true,
    );
  });

  // 빈 상태가 연봉 분포를 밀어내지 않게 한다
  it("비교할 수 없으면 연봉 분포 아래에 둔다", async () => {
    vi.mocked(dashboardMarketApi.fetchWageGrowth).mockResolvedValue(
      WAGE_GROWTH_EMPTY,
    );

    renderTrends();

    await waitFor(() => {
      expect(screen.getByText(/연봉 기록이 두 해 이상/)).toBeInTheDocument();
    });
    expect(
      isBefore(
        sectionHeading("동료집단 연봉 분포"),
        sectionHeading("상승률 비교"),
      ),
    ).toBe(true);
  });

  it("상승률 비교만 실패하면 그 자리에만 실패와 다시 시도를 보이고 다시 부른다", async () => {
    vi.mocked(dashboardMarketApi.fetchWageGrowth).mockRejectedValueOnce(
      new Error("boom"),
    );

    renderTrends();

    const failure = await screen.findByText(
      "상승률 비교 데이터를 불러오지 못했습니다",
    );
    expect(screen.getByTestId("income-vs-price-chart")).toBeInTheDocument();
    expect(isBefore(sectionHeading("동료집단 연봉 분포"), failure)).toBe(true);

    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitFor(() => {
      expect(screen.getByTestId("wage-growth-section")).toBeInTheDocument();
    });
  });

  it("상승률 비교만 401 이어도 로그인으로 보낸다", async () => {
    vi.mocked(dashboardMarketApi.fetchWageGrowth).mockRejectedValue(
      new UnauthorizedError(),
    );

    renderTrends();

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/login", { replace: true });
    });
  });

  it("상승률 비교만 살아 있으면 화면 단위 실패가 아니다", async () => {
    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockRejectedValue(
      new Error("boom"),
    );
    vi.mocked(dashboardMarketApi.fetchTrends).mockRejectedValue(
      new Error("boom"),
    );

    renderTrends();

    await waitFor(() => {
      expect(screen.getByTestId("wage-growth-section")).toBeInTheDocument();
    });
    expect(
      screen.queryByText("시장 분석 데이터를 불러오지 못했습니다"),
    ).not.toBeInTheDocument();
  });

  it("#growth 로 왔는데 비교할 수 없어도 그 섹션으로 스크롤한다", async () => {
    vi.mocked(dashboardMarketApi.fetchWageGrowth).mockResolvedValue(
      WAGE_GROWTH_EMPTY,
    );
    const scrolled: string[] = [];
    Element.prototype.scrollIntoView = vi.fn(function (this: Element) {
      scrolled.push(this.id);
    });

    renderTrends("/dashboard/trends#growth");

    await waitFor(() => expect(scrolled).toEqual(["growth"]));
  });
});
