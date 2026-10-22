import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { SalaryDistributionResponse } from "../../lib/dashboardMarketApi";
import {
  formatKoreanSalary,
  SalaryDistributionChart,
} from "./SalaryDistributionChart";

describe("SalaryDistributionChart", () => {
  describe("formatKoreanSalary", () => {
    it("원 단위 금액을 만원/억원으로 올바르게 변환한다", () => {
      expect(formatKoreanSalary(null)).toBe("-");
      expect(formatKoreanSalary(0)).toBe("0원");
      expect(formatKoreanSalary(5000)).toBe("5,000원");
      expect(formatKoreanSalary(55_000_000)).toBe("5,500만 원");
      expect(formatKoreanSalary(100_000_000)).toBe("1억 원");
      expect(formatKoreanSalary(125_000_000)).toBe("1억 2,500만 원");
    });
  });

  it("정상 연봉 분포 데이터를 올바르게 시각화하고 5수치를 표시한다", () => {
    const mockData: SalaryDistributionResponse = {
      status: "OK",
      sampleSize: 150,
      baseline: {
        basis: "AGE_BAND_PROXY",
        source: "KOSIS",
        median: 65_000_000,
        wageDefinition: "임금직무정보시스템 500인 이상",
      },
      postingReference: {
        min: 40_000_000,
        p25: 52_000_000,
        median: 68_000_000,
        p75: 85_000_000,
        max: 120_000_000,
        sampleSize: 50,
        missingRate: 66.7,
      },
      userPosition: {
        grossSalary: 72_000_000,
        percentile: 65,
      },
      peerGroup: {
        fallbackLevel: 1,
        appliedConditions: "서울 전체 / 경력 4~7년(MID) / 회사규모 무관",
      },
      careerLadder: {
        userBracket: "MID",
        steps: [
          {
            bracket: "ENTRY",
            median: 32_000_000,
            raiseFromPrevious: null,
            source: "FALLBACK",
          },
          {
            bracket: "JUNIOR",
            median: 40_000_000,
            raiseFromPrevious: 25,
            source: "KOSIS",
          },
          {
            bracket: "MID",
            median: 52_000_000,
            raiseFromPrevious: 30,
            source: "KOSIS",
          },
          {
            bracket: "SENIOR",
            median: 62_000_000,
            raiseFromPrevious: 19.23,
            source: "UNKNOWN",
          },
          {
            bracket: "LEAD",
            median: null,
            raiseFromPrevious: null,
            source: "UNKNOWN",
          },
        ],
      },
    };

    render(<SalaryDistributionChart data={mockData} />);

    expect(screen.getByTestId("salary-distribution-chart")).toHaveAttribute(
      "data-slot",
      "card",
    );
    // 차트 제목 및 사용자 백분위 위치 확인
    expect(screen.getByText("동료집단 연봉 분포")).toHaveAttribute(
      "data-slot",
      "card-title",
    );
    // 신체 부위 라벨과 「내 위치」는 손수 배지가 아니라 ToneBadge 다.
    for (const el of screen.getAllByText(/^내 위치/)) {
      expect(el).toHaveAttribute("data-tone", "positive");
    }
    expect(screen.getAllByText(/상위 35%/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/현재 세전:/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("[어깨]")).toBeInTheDocument();

    // 5수치 확인
    expect(screen.getAllByText("4,000만 원").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("5,200만 원").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("6,800만 원").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("8,500만 원").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("1억 2,000만 원").length).toBeGreaterThanOrEqual(
      1,
    );

    // 공식 통계 기준선 및 결측률 정보
    expect(
      screen.getByText(/임금직무정보시스템 500인 이상/),
    ).toBeInTheDocument();
    expect(screen.getByText(/66.7%/)).toBeInTheDocument();

    // 내 연봉, 공고 중앙값, 공식 기준선 인디케이터(실루엣 내부 마커 툴팁 및 라벨) 확인
    expect(screen.getByTitle("내 연봉: 7,200만 원")).toBeInTheDocument();
    expect(screen.getByTitle("공고 중앙값: 6,800만 원")).toBeInTheDocument();
    expect(
      screen.getByTitle("공식 임금통계 기준선: 6,500만 원"),
    ).toBeInTheDocument();
    expect(screen.getByText("공고중앙")).toBeInTheDocument();
    expect(screen.getByText("공식기준")).toBeInTheDocument();
    // 카드 안에서는 카드 gap 이 간격을 준다 — 자기 margin 을 더하면 40px 로 벌어진다.
    expect(screen.getByLabelText("경력 사다리")).not.toHaveClass("mt-6");
    expect(screen.getByText("신입")).toBeInTheDocument();
    expect(screen.getByText("주니어")).toBeInTheDocument();
    expect(screen.getByText("미들")).toBeInTheDocument();
    expect(screen.getByText("시니어")).toBeInTheDocument();
    expect(screen.getByText("리드")).toBeInTheDocument();
    expect(screen.queryByText("ENTRY")).not.toBeInTheDocument();
    expect(
      screen.getByText("서울 전체 / 경력 4~7년(미들) / 회사규모 무관"),
    ).toBeInTheDocument();
    expect(screen.getByText("내 구간")).toBeInTheDocument();
    expect(screen.getByText("추정 · FALLBACK")).toBeInTheDocument();
    expect(screen.getAllByText("KOSIS 실측 보간")).toHaveLength(2);
    expect(screen.getByText(/AGE_BAND_PROXY · KOSIS/)).toBeInTheDocument();
  });

  it("임금통계 기준선이 없으면 각주가 기준선을 지어내지 않는다", () => {
    const mockData: SalaryDistributionResponse = {
      status: "OK",
      sampleSize: 12,
      baseline: null,
      postingReference: {
        min: 40_000_000,
        p25: 52_000_000,
        median: 68_000_000,
        p75: 85_000_000,
        max: 120_000_000,
        sampleSize: 12,
        missingRate: 0,
      },
      userPosition: null,
      peerGroup: {
        fallbackLevel: 1,
        appliedConditions: "전국 / 경력 4~7년(MID) / 회사규모 무관",
      },
      trend: [],
      careerLadder: null,
    };

    render(<SalaryDistributionChart data={mockData} />);

    expect(screen.getByText("공식 임금통계 기준선 없음")).toBeInTheDocument();
    expect(screen.queryByText(/UNKNOWN/)).not.toBeInTheDocument();
  });

  it("공고 표본은 충족하지만 공개된 연봉이 0건일 때 왜곡된 0원 차트 대신 안내 화면을 표시한다", () => {
    const noDisclosedSalaryData: SalaryDistributionResponse = {
      status: "OK",
      sampleSize: 20,
      baseline: {
        basis: "AGE_BAND_PROXY",
        source: "KOSIS",
        median: 50_000_000,
        wageDefinition: "임금통계",
      },
      postingReference: {
        min: null,
        p25: null,
        median: null,
        p75: null,
        max: null,
        sampleSize: 0,
        missingRate: 100,
      },
      userPosition: null,
      peerGroup: {
        fallbackLevel: 1,
        appliedConditions: "서울 강남구 / 경력 1~3년(JUNIOR) / 대기업",
      },
    };

    render(<SalaryDistributionChart data={noDisclosedSalaryData} />);

    const noDisclosed = screen.getByTestId("no-disclosed-salary-state");
    expect(
      noDisclosed.querySelector('[data-testid="empty-state-icon"]'),
    ).toHaveAttribute("data-tone", "info");
    // EmptyState 설명은 p 다 — 기준선 문장을 p 로 넣으면 p 안의 p 가 된다.
    expect(noDisclosed.querySelector("p p")).toBeNull();
    expect(screen.getByText(/국가 공식 임금통계 기준선/)).toBeInTheDocument();
    expect(
      screen.getByText("동료집단 공고 중 공개된 연봉 정보가 없습니다."),
    ).toBeInTheDocument();
    expect(screen.getByText(/총 동료집단 공고/)).toBeInTheDocument();
    expect(screen.getByText("20건")).toBeInTheDocument();
    // 0원/0만원 5수치 카드가 없어야 함
    expect(screen.queryByText("0원 (최소)")).not.toBeInTheDocument();
    expect(screen.queryByText("0만 원")).not.toBeInTheDocument();
  });

  it("표본 부족(INSUFFICIENT_SAMPLE) 상태일 때 경고 화면을 표시하고 임의 수치를 그리지 않는다", () => {
    const insufficientData: SalaryDistributionResponse = {
      status: "INSUFFICIENT_SAMPLE",
      sampleSize: 6,
      minRequired: 10,
      baseline: null,
      postingReference: null,
      userPosition: null,
      peerGroup: {
        fallbackLevel: 5,
        appliedConditions: "전국 / 전체 경력 / 회사규모 무관",
      },
      careerLadder: {
        userBracket: "ENTRY",
        steps: [
          {
            bracket: "ENTRY",
            median: null,
            raiseFromPrevious: null,
            source: "UNKNOWN",
          },
        ],
      },
    };

    render(<SalaryDistributionChart data={insufficientData} />);

    const insufficient = screen.getByTestId("insufficient-sample-state");
    expect(
      insufficient.querySelector('[data-testid="empty-state-icon"]'),
    ).toHaveAttribute("data-tone", "warning");
    expect(screen.getByText("⚠️ 표본 부족")).toBeInTheDocument();
    expect(
      screen.getByText(
        /동료집단 표본 수가 부족하여 연봉 분포를 산출할 수 없습니다/i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("6건")).toBeInTheDocument();
    expect(screen.getByLabelText("경력 사다리")).toHaveClass("mt-6");
    expect(
      screen.queryByTestId("salary-distribution-chart"),
    ).not.toBeInTheDocument();
  });

  it("경력 사다리에 기준 연도(basePeriod)와 연도 선택 버튼이 표시되고 연도 변경이 동작한다 (b14)", async () => {
    const onYearChange = vi.fn();
    const dataWithYearAxis: SalaryDistributionResponse = {
      status: "OK",
      sampleSize: 50,
      careerLadder: {
        userBracket: "MID",
        basePeriod: "2025",
        availableYears: ["2023", "2024", "2025"],
        steps: [
          {
            bracket: "MID",
            median: 55_000_000,
            raiseFromPrevious: null,
            source: "KOSIS",
          },
        ],
      },
    };

    render(
      <SalaryDistributionChart
        data={dataWithYearAxis}
        onYearChange={onYearChange}
      />,
    );

    expect(screen.getByText(/2025년 기준/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "2023년" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "2024년" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "2025년" })).toBeInTheDocument();

    const btn2024 = screen.getByRole("button", { name: "2024년" });
    await act(async () => {
      fireEvent.click(btn2024);
    });

    expect(onYearChange).toHaveBeenCalledWith("2024");
  });
});
