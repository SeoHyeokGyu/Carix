import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
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
}));

function LocationProbe() {
  return <div data-testid="location-search">{useLocation().search}</div>;
}

function renderTrends(path = "/dashboard/trends") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/dashboard/trends"
          element={
            <>
              <DashboardTrends />
              <LocationProbe />
            </>
          }
        />
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

  it("화면 헤더와 기본 탭(소득 vs 물가)을 렌더링하고 화면 ID 는 보이지 않는다", async () => {
    renderTrends();

    expect(screen.queryByText("S-14")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "추이 분석" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "소득 vs 물가" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    await waitFor(() => {
      expect(screen.getByTestId("income-vs-price-chart")).toBeInTheDocument();
    });
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
    await within(
      await screen.findByRole("tabpanel", { name: "소득 vs 물가" }),
    ).findByRole("heading", { level: 3 });

    // 실패한 연봉 분포 탭에서 다시 시도 — 이번엔 소득 대 물가까지 둘 다 실패한다.
    vi.mocked(dashboardMarketApi.fetchTrends).mockRejectedValue(new Error("x"));
    await user.click(screen.getByRole("tab", { name: "연봉 분포" }));
    await user.click(
      await within(screen.getByRole("tabpanel")).findByRole("button", {
        name: "다시 시도",
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "시장 분석 데이터를 불러오지 못했습니다",
    );
    // 받아 둔 소득 대 물가 차트는 그대로 남는다.
    await user.click(screen.getByRole("tab", { name: "소득 vs 물가" }));
    expect(
      within(screen.getByRole("tabpanel")).getByRole("heading", { level: 3 }),
    ).toBeInTheDocument();
  });
});
