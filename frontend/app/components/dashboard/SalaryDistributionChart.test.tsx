import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  SalaryDistributionChart,
  formatKoreanSalary,
  getBodyPartInfo,
} from "./SalaryDistributionChart";
import type { SalaryDistributionResponse } from "../../lib/dashboardMarketApi";

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

  describe("getBodyPartInfo", () => {
    it("백분위에 따라 정확한 신체 부위(정수리, 머리, 어깨, 허리, 무릎, 발목)를 매핑한다", () => {
      expect(getBodyPartInfo(95).name).toBe("정수리");
      expect(getBodyPartInfo(80).name).toBe("머리");
      expect(getBodyPartInfo(65).name).toBe("어깨");
      expect(getBodyPartInfo(50).name).toBe("가슴");
      expect(getBodyPartInfo(40).name).toBe("허리");
      expect(getBodyPartInfo(25).name).toBe("무릎");
      expect(getBodyPartInfo(10).name).toBe("발목");
    });
  });

  it("정상 연봉 분포 데이터를 올바르게 시각화하고 5수치를 표시한다", () => {
    const mockData: SalaryDistributionResponse = {
      status: "OK",
      sampleSize: 150,
      baseline: {
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
    };

    render(<SalaryDistributionChart data={mockData} />);

    // 차트 제목 및 사용자 백분위 위치 확인
    expect(screen.getByText("동료집단 연봉 분포")).toBeInTheDocument();
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

    // 내 연봉 인디케이터(세로선 툴팁, 뱃지) 확인
    expect(screen.getByTitle("내 연봉: 7,200만 원")).toBeInTheDocument();
  });

  it("공고 표본은 충족하지만 공개된 연봉이 0건일 때 왜곡된 0원 차트 대신 안내 화면을 표시한다", () => {
    const noDisclosedSalaryData: SalaryDistributionResponse = {
      status: "OK",
      sampleSize: 20,
      baseline: {
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

    expect(screen.getByTestId("no-disclosed-salary-state")).toBeInTheDocument();
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
    };

    render(<SalaryDistributionChart data={insufficientData} />);

    expect(screen.getByTestId("insufficient-sample-state")).toBeInTheDocument();
    expect(screen.getByText("⚠️ 표본 부족")).toBeInTheDocument();
    expect(
      screen.getByText(
        /동료집단 표본 수가 부족하여 연봉 분포를 산출할 수 없습니다/i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("6건")).toBeInTheDocument();
    expect(
      screen.queryByTestId("salary-distribution-chart"),
    ).not.toBeInTheDocument();
  });
});
