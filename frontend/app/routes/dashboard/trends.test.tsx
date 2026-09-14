import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import DashboardTrends from "./trends";
import * as dashboardMarketApi from "../../lib/dashboardMarketApi";

vi.mock("../../lib/dashboardMarketApi", () => ({
  fetchSalaryDistribution: vi.fn(),
  fetchSkillDemands: vi.fn(),
  fetchTrends: vi.fn(),
}));

describe("S-14 DashboardTrends 라우트", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockResolvedValue({
      status: "OK",
      sampleSize: 80,
      baseline: {
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

    vi.mocked(dashboardMarketApi.fetchSkillDemands).mockResolvedValue({
      skills: [
        {
          skillId: "s-1",
          standardName: "Kotlin",
          demandIndex: 115.0,
          recentDemandRate: 0.12,
          baselineDemandRate: 0.1,
          sources: [{ source: "SARAMIN", demandIndex: 115.0, sampleSize: 45 }],
          weight: 1.0,
          status: "OK",
        },
      ],
      coverage: "보유 스킬 1개 중 1개 반영 (가중치 100% 재정규화)",
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

  it("S-14 화면 헤더 및 기본 탭(스킬 수요)을 정상 렌더링한다", async () => {
    render(
      <MemoryRouter>
        <DashboardTrends />
      </MemoryRouter>,
    );

    expect(screen.getByText("S-14")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "추이 분석" }),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("Kotlin")).toBeInTheDocument();
      expect(screen.getByText(/▲ 상승/)).toBeInTheDocument();
    });
  });

  it("연봉 분포 탭 클릭 시 SalaryDistributionChart 컴포넌트가 마운트된다", async () => {
    render(
      <MemoryRouter>
        <DashboardTrends />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Kotlin")).toBeInTheDocument();
    });

    const salaryTabBtn = screen.getByRole("button", { name: "연봉 분포" });
    fireEvent.click(salaryTabBtn);

    await waitFor(() => {
      expect(screen.getByText("동료집단 연봉 분포")).toBeInTheDocument();
      expect(screen.getAllByText(/6,200만 원/).length).toBeGreaterThan(0);
      expect(screen.getByText(/현재 세전: 6,500만 원/)).toBeInTheDocument();
    });
  });

  it("소득 vs 물가 탭 클릭 시 IncomeVsPriceChart 컴포넌트가 마운트된다", async () => {
    render(
      <MemoryRouter>
        <DashboardTrends />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Kotlin")).toBeInTheDocument();
    });

    const incomeVsPriceTabBtn = screen.getByRole("button", {
      name: "소득 vs 물가",
    });
    fireEvent.click(incomeVsPriceTabBtn);

    await waitFor(() => {
      expect(screen.getByTestId("income-vs-price-chart")).toBeInTheDocument();
      expect(
        screen.getByText("소득 대 개인물가 지수 추이"),
      ).toBeInTheDocument();
    });
  });

  it("연봉 분포 데이터가 null일 때 안내 문구를 렌더링한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockResolvedValue(
      null as never,
    );

    render(
      <MemoryRouter>
        <DashboardTrends />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Kotlin")).toBeInTheDocument();
    });

    const salaryTabBtn = screen.getByRole("button", { name: "연봉 분포" });
    fireEvent.click(salaryTabBtn);

    await waitFor(() => {
      expect(
        screen.getByText("연봉 분포 데이터를 불러오지 못했습니다."),
      ).toBeInTheDocument();
    });
  });

  it("소득 대 물가 데이터가 null일 때 안내 문구를 렌더링한다", async () => {
    vi.mocked(dashboardMarketApi.fetchTrends).mockResolvedValue(null as never);

    render(
      <MemoryRouter>
        <DashboardTrends />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Kotlin")).toBeInTheDocument();
    });

    const incomeVsPriceTabBtn = screen.getByRole("button", {
      name: "소득 vs 물가",
    });
    fireEvent.click(incomeVsPriceTabBtn);

    await waitFor(() => {
      expect(
        screen.getByText("소득 대 물가 추이 데이터를 불러오지 못했습니다."),
      ).toBeInTheDocument();
    });
  });
});
