import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import DashboardTrends from "./trends";
import * as dashboardMarketApi from "../../lib/dashboardMarketApi";
import { UnauthorizedError } from "../../lib/coreApi";

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
  fetchSkillDemands: vi.fn(),
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

  it("제목 줄에 새로고침 버튼이 없다", async () => {
    renderTrends();
    expect(
      await screen.findByRole("heading", { level: 1, name: "추이 분석" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /새로고침/ })).toBeNull();
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

  // 컨셉 질문 순서(구매력 → 시장가치)를 따른다.
  it("탭 순서는 소득 vs 물가 · 스킬 수요 · 연봉 분포다", () => {
    renderTrends();

    expect(screen.getAllByRole("tab").map((tab) => tab.textContent)).toEqual([
      "소득 vs 물가",
      "스킬 수요",
      "연봉 분포",
    ]);
    expect(screen.getByRole("tablist")).toBeInTheDocument();
  });

  it("?tab=skills 로 들어오면 스킬 수요 탭이 열린다", async () => {
    renderTrends("/dashboard/trends?tab=skills");

    await waitFor(() => {
      expect(screen.getByText("Kotlin")).toBeInTheDocument();
      expect(screen.getByText(/▲ 상승/)).toBeInTheDocument();
    });
    expect(screen.getByRole("tab", { name: "스킬 수요" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("?tab=salary 로 들어오면 연봉 분포 차트가 마운트된다", async () => {
    renderTrends("/dashboard/trends?tab=salary");

    await waitFor(() => {
      expect(screen.getByText("동료집단 연봉 분포")).toBeInTheDocument();
      expect(screen.getAllByText(/6,200만 원/).length).toBeGreaterThan(0);
      expect(screen.getByText(/현재 세전: 6,500만 원/)).toBeInTheDocument();
    });
  });

  it("모르는 tab 값이면 기본 탭(소득 vs 물가)으로 연다", () => {
    renderTrends("/dashboard/trends?tab=unknown");

    expect(screen.getByRole("tab", { name: "소득 vs 물가" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("탭을 바꿔도 다른 쿼리 값은 남는다", () => {
    renderTrends("/dashboard/trends?tab=income&from=dashboard");

    fireEvent.click(screen.getByRole("tab", { name: "연봉 분포" }));

    expect(screen.getByTestId("location-search")).toHaveTextContent(
      "?tab=salary&from=dashboard",
    );
  });

  it("로딩 중에도 탭이 가리키는 패널이 있다", () => {
    renderTrends();

    const tab = screen.getByRole("tab", { name: "소득 vs 물가" });
    const panelId = tab.getAttribute("aria-controls");
    expect(panelId).toBeTruthy();
    expect(document.getElementById(panelId!)).toHaveAttribute(
      "role",
      "tabpanel",
    );
  });

  it("탭을 누르면 URL 의 tab 값이 바뀐다", async () => {
    renderTrends();

    fireEvent.click(screen.getByRole("tab", { name: "스킬 수요" }));

    expect(screen.getByTestId("location-search")).toHaveTextContent(
      "?tab=skills",
    );
    await waitFor(() => {
      expect(screen.getByText("Kotlin")).toBeInTheDocument();
    });
  });

  it("연봉 분포 데이터가 null일 때 안내 문구를 렌더링한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockResolvedValue(
      null as never,
    );

    renderTrends("/dashboard/trends?tab=salary");

    await waitFor(() => {
      expect(
        screen.getByText("연봉 분포 데이터를 불러오지 못했습니다."),
      ).toBeInTheDocument();
    });
  });

  it("소득 대 물가 데이터가 null일 때 안내 문구를 렌더링한다", async () => {
    vi.mocked(dashboardMarketApi.fetchTrends).mockResolvedValue(null as never);

    renderTrends();

    await waitFor(() => {
      expect(
        screen.getByText("소득 대 물가 추이 데이터를 불러오지 못했습니다."),
      ).toBeInTheDocument();
    });
  });

  // 세션이 죽으면 세 호출이 동시에 401 이다. 부분 성공 렌더링과 갈라 둬야 한다 —
  // 한 API 만 죽은 경우에 멀쩡한 화면을 로그인으로 내쫓으면 안 된다.
  it("세션이 만료되면 로그인으로 보낸다", async () => {
    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockRejectedValue(
      new UnauthorizedError(),
    );
    vi.mocked(dashboardMarketApi.fetchSkillDemands).mockRejectedValue(
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
      screen.queryByText("시장 분석 데이터를 불러오는 데 실패했습니다."),
    ).not.toBeInTheDocument();
  });
});
