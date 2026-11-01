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
}));

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
      screen.getByRole("heading", { level: 3, name: "동료집단 연봉 분포" }),
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

  it("두 조회가 모두 실패하면 화면 단위 실패 하나만 보이고 다시 시도할 수 있다", async () => {
    vi.mocked(dashboardMarketApi.fetchSalaryDistribution).mockRejectedValue(
      new Error("x"),
    );
    vi.mocked(dashboardMarketApi.fetchTrends).mockRejectedValue(new Error("x"));
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
});
