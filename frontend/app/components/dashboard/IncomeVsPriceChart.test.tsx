import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { TrendListResponse } from "../../lib/dashboardMarketApi";
import { IncomeVsPriceChart } from "./IncomeVsPriceChart";

describe("IncomeVsPriceChart", () => {
  it("데이터가 비어 있을 때 비어있음 안내 상태를 렌더링한다", () => {
    const emptyData: TrendListResponse = {
      trends: [],
      purchasingPowerStatus: "NOT_AVAILABLE",
      marketValueStatus: "NOT_AVAILABLE",
    };

    render(<IncomeVsPriceChart data={emptyData} />);
    // 손으로 그린 점선 박스가 아니라 EmptyState 다.
    expect(
      screen
        .getByTestId("trends-empty-state")
        .querySelector('[data-testid="empty-state-icon"]'),
    ).not.toBeNull();
    expect(
      screen.getByText("시계열 추이 데이터가 아직 충분하지 않습니다."),
    ).toBeInTheDocument();
  });

  it("비교 기간이 짧은 구매력 지수는 지연이 아니라 비교 기간 짧음으로 표시한다", () => {
    const data: TrendListResponse = {
      trends: [
        {
          yearMonth: "202605",
          incomeIndex: 100,
          personalPriceIndex: 100,
          purchasingPowerIndex: 100,
          marketValueIndex: 100,
          nominalIncome: null,
          realIncome: null,
        },
        {
          yearMonth: "202606",
          incomeIndex: 100,
          personalPriceIndex: 101,
          purchasingPowerIndex: 99,
          marketValueIndex: 100,
          nominalIncome: null,
          realIncome: null,
        },
      ],
      purchasingPowerStatus: "INSUFFICIENT_HISTORY",
      marketValueStatus: "OK",
    };

    render(<IncomeVsPriceChart data={data} />);
    expect(screen.getByText("비교 기간 짧음")).toHaveAttribute(
      "data-tone",
      "info",
    );
    expect(screen.queryByText("일부 지연")).not.toBeInTheDocument();
    expect(screen.getByTestId("income-vs-price-chart")).toHaveAttribute(
      "data-slot",
      "card",
    );
  });

  it("12개월 이상 정상 소득 및 개인물가 데이터가 주어지면 차트와 손실 구간을 렌더링한다", () => {
    const fullData: TrendListResponse = {
      trends: [
        {
          yearMonth: "202509",
          incomeIndex: 100,
          personalPriceIndex: 100,
          purchasingPowerIndex: 100,
          marketValueIndex: 100,
          nominalIncome: 60000000,
          realIncome: 60000000,
        },
        {
          yearMonth: "202510",
          incomeIndex: 100,
          personalPriceIndex: 101.5,
          purchasingPowerIndex: 98.5,
          marketValueIndex: 102,
          nominalIncome: 60000000,
          realIncome: 59100000,
        },
        {
          yearMonth: "202511",
          incomeIndex: 100,
          personalPriceIndex: 102.3,
          purchasingPowerIndex: 97.7,
          marketValueIndex: 103,
          nominalIncome: 60000000,
          realIncome: 58600000,
        },
        {
          yearMonth: "202512",
          incomeIndex: 102,
          personalPriceIndex: 103.0,
          purchasingPowerIndex: 99.0,
          marketValueIndex: 104,
          nominalIncome: 61200000,
          realIncome: 59400000,
        },
        {
          yearMonth: "202601",
          incomeIndex: 105,
          personalPriceIndex: 103.8,
          purchasingPowerIndex: 101.1,
          marketValueIndex: 106,
          nominalIncome: 63000000,
          realIncome: 60700000,
        },
        {
          yearMonth: "202602",
          incomeIndex: 105,
          personalPriceIndex: 104.5,
          purchasingPowerIndex: 100.4,
          marketValueIndex: 107,
          nominalIncome: 63000000,
          realIncome: 60200000,
        },
        {
          yearMonth: "202603",
          incomeIndex: 105,
          personalPriceIndex: 105.2,
          purchasingPowerIndex: 99.8,
          marketValueIndex: 108,
          nominalIncome: 63000000,
          realIncome: 59800000,
        },
        {
          yearMonth: "202604",
          incomeIndex: 105,
          personalPriceIndex: 105.8,
          purchasingPowerIndex: 99.2,
          marketValueIndex: 109,
          nominalIncome: 63000000,
          realIncome: 59500000,
        },
        {
          yearMonth: "202605",
          incomeIndex: 108,
          personalPriceIndex: 106.3,
          purchasingPowerIndex: 101.5,
          marketValueIndex: 111,
          nominalIncome: 64800000,
          realIncome: 60900000,
        },
        {
          yearMonth: "202606",
          incomeIndex: 108,
          personalPriceIndex: 107.0,
          purchasingPowerIndex: 100.9,
          marketValueIndex: 112,
          nominalIncome: 64800000,
          realIncome: 60500000,
        },
        {
          yearMonth: "202607",
          incomeIndex: 108,
          personalPriceIndex: 107.8,
          purchasingPowerIndex: 100.1,
          marketValueIndex: 114,
          nominalIncome: 64800000,
          realIncome: 60100000,
        },
        {
          yearMonth: "202608",
          incomeIndex: 110,
          personalPriceIndex: 108.5,
          purchasingPowerIndex: 101.3,
          marketValueIndex: 115,
          nominalIncome: 66000000,
          realIncome: 60800000,
        },
      ],
      purchasingPowerStatus: "OK",
      marketValueStatus: "OK",
    };

    render(<IncomeVsPriceChart data={fullData} />);
    expect(screen.getByTestId("income-vs-price-chart")).toBeInTheDocument();
    expect(screen.getByText("소득 대 개인물가 지수 추이")).toBeInTheDocument();
    expect(screen.getByText("소득 지수 (실선)")).toBeInTheDocument();
    expect(screen.getByText("개인물가 지수 (점선)")).toBeInTheDocument();
    expect(screen.getByText("구매력 손실 구간")).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "소득 대 개인물가 지수 시계열 차트" }),
    ).toBeInTheDocument();
  });

  it("12개월 미만 데이터일 때 누적 개월 수 안내 배너를 표시한다", () => {
    const shortData: TrendListResponse = {
      trends: [
        {
          yearMonth: "202605",
          incomeIndex: 100,
          personalPriceIndex: 100,
          purchasingPowerIndex: 100,
          marketValueIndex: 100,
          nominalIncome: null,
          realIncome: null,
        },
        {
          yearMonth: "202606",
          incomeIndex: 102,
          personalPriceIndex: 101,
          purchasingPowerIndex: 100.9,
          marketValueIndex: 102,
          nominalIncome: null,
          realIncome: null,
        },
      ],
      purchasingPowerStatus: "OK",
      marketValueStatus: "OK",
    };

    render(<IncomeVsPriceChart data={shortData} />);
    expect(screen.getByText(/데이터가 누적되어 있습니다/)).toBeInTheDocument();
    expect(screen.getByText("2개월")).toBeInTheDocument();
  });

  it("중간 월 결측치가 포함되어 있어도 오류 없이 렌더링된다", () => {
    const dataWithGap: TrendListResponse = {
      trends: [
        {
          yearMonth: "202605",
          incomeIndex: 100,
          personalPriceIndex: 100,
          purchasingPowerIndex: 100,
          marketValueIndex: 100,
          nominalIncome: null,
          realIncome: null,
        },
        {
          yearMonth: "202606",
          incomeIndex: null,
          personalPriceIndex: null,
          purchasingPowerIndex: null,
          marketValueIndex: null,
          nominalIncome: null,
          realIncome: null,
        },
        {
          yearMonth: "202607",
          incomeIndex: 104,
          personalPriceIndex: 102,
          purchasingPowerIndex: 101.9,
          marketValueIndex: 103,
          nominalIncome: null,
          realIncome: null,
        },
      ],
      purchasingPowerStatus: "OK",
      marketValueStatus: "OK",
    };

    render(<IncomeVsPriceChart data={dataWithGap} />);
    expect(screen.getByTestId("income-vs-price-chart")).toBeInTheDocument();
  });

  it("차트의 데이터 포인트를 호버하면 툴팁(기준월 대비 지수 및 명목/실질 소득)이 렌더링된다", () => {
    const data: TrendListResponse = {
      trends: [
        {
          yearMonth: "202607",
          incomeIndex: 108,
          personalPriceIndex: 105,
          purchasingPowerIndex: 102.8,
          marketValueIndex: 110,
          nominalIncome: 65000000,
          realIncome: 62000000,
        },
      ],
      purchasingPowerStatus: "OK",
      marketValueStatus: "OK",
    };

    render(<IncomeVsPriceChart data={data} />);

    // 포인트 영역을 찾아 hover
    const pointGroup = document.querySelector("g.cursor-pointer");
    expect(pointGroup).not.toBeNull();

    fireEvent.mouseEnter(pointGroup!);

    expect(screen.getByText("202607")).toBeInTheDocument();
    expect(screen.getByText("소득 지수:")).toBeInTheDocument();
    expect(screen.getByText("108")).toBeInTheDocument();
    expect(screen.getByText("개인물가 지수:")).toBeInTheDocument();
    expect(screen.getByText("105")).toBeInTheDocument();
    expect(screen.getByText("명목 소득:")).toBeInTheDocument();
    expect(screen.getByText("6,500만 원")).toBeInTheDocument();
    expect(screen.getByText("실질 소득:")).toBeInTheDocument();
    expect(screen.getByText("6,200만 원")).toBeInTheDocument();

    // 마우스 떠나면 툴팁 제거
    fireEvent.mouseLeave(pointGroup!);
    expect(screen.queryByText("기준월 대비")).not.toBeInTheDocument();
  });
});
