import { describe, expect, it } from "vitest";

import type { AppliedExpenseResponse } from "./api/expense";
import type { IncomeResponse } from "./api/income";
import { toPpiInputs, toWireBaseMonth } from "./ppiInputs";

function income(effectiveDate: string, grossSalary: number): IncomeResponse {
  return {
    id: effectiveDate,
    grossSalary,
    netSalary: 0,
    effectiveDate,
    changeReason: null,
    selectedChangeReason: null,
    bonusIncluded: true,
    fixedBonusAnnual: null,
  };
}

function applied(
  overrides: Partial<AppliedExpenseResponse> = {},
): AppliedExpenseResponse {
  return {
    baseYearMonth: "202608",
    weightVersion: 1,
    weightBasePeriod: "2025",
    amountBasis: "USER_INPUT",
    categories: [
      {
        category: "HOUSING",
        amount: 1_000_000,
        weight: 0.2,
        weightStatus: "USER_DEFINED",
      },
      {
        category: "FOOD",
        amount: 1_300_000,
        weight: 0.3,
        weightStatus: "USER_DEFINED",
      },
    ],
    ...overrides,
  };
}

describe("toPpiInputs", () => {
  it("기준월 말일까지 유효한 마지막 연봉을 고르고 그 뒤 날짜의 연봉은 쓰지 않는다", () => {
    const result = toPpiInputs(
      "2026-08-01",
      [
        income("2025-03-01", 48_000_000),
        income("2026-08-31", 52_000_000),
        income("2026-09-01", 60_000_000),
      ],
      applied(),
    );
    expect(result.grossSalary).toBe(52_000_000);
  });

  it("연봉이 0 인 행은 유효 연봉으로 보지 않는다", () => {
    const result = toPpiInputs(
      "2026-08-01",
      [income("2025-03-01", 48_000_000), income("2026-01-01", 0)],
      applied(),
    );
    expect(result.grossSalary).toBe(48_000_000);
  });

  it("유효 연봉이 없으면 null 이다", () => {
    const result = toPpiInputs(
      "2026-08-01",
      [income("2026-09-01", 60_000_000)],
      applied(),
    );
    expect(result.grossSalary).toBeNull();
  });

  it("적용된 소비 금액을 합치고 평균 보간 여부를 알린다", () => {
    expect(toPpiInputs("2026-08-01", [], applied()).expense).toEqual({
      monthlyTotal: 2_300_000,
      hasDefaultFill: false,
    });

    const filled = applied({
      categories: [
        ...applied().categories,
        {
          category: "LEISURE",
          amount: 200_000,
          weight: 0.1,
          weightStatus: "DEFAULT_CPI",
        },
      ],
    });
    expect(toPpiInputs("2026-08-01", [], filled).expense).toEqual({
      monthlyTotal: 2_500_000,
      hasDefaultFill: true,
    });
  });

  it("금액 기준이 없으면(NONE) 0 이 아니라 null 이다", () => {
    const none = applied({
      amountBasis: "NONE",
      categories: applied().categories.map((c) => ({
        ...c,
        amount: null,
        weightStatus: "DEFAULT_CPI" as const,
      })),
    });
    expect(toPpiInputs("2026-08-01", [], none).expense).toBeNull();
  });

  it("조회가 실패해 값이 없으면 그쪽만 null 이다", () => {
    const result = toPpiInputs("2026-08-01", null, null);
    expect(result).toEqual({ grossSalary: null, expense: null });
  });

  it("기준일 형식이 아니면 연봉을 고르지 않는다", () => {
    const result = toPpiInputs(
      "unknown",
      [income("2025-03-01", 48_000_000)],
      null,
    );
    expect(result.grossSalary).toBeNull();
  });
});

describe("toWireBaseMonth", () => {
  it("기준일의 연·월을 와이어 형식으로 바꾼다", () => {
    expect(toWireBaseMonth("2026-08-01")).toBe("202608");
    expect(toWireBaseMonth("oops")).toBeNull();
  });
});
