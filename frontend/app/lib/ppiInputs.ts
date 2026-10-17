import type { AppliedExpenseResponse } from "./api/expense";
import type { IncomeResponse } from "./api/income";
import { BASE_DATE_PATTERN } from "./verdict";

export interface PpiInputs {
  grossSalary: number | null;
  /** 금액 기준이 없으면(`NONE`) null — 0원으로 내리지 않는다(API 규격 §7.6). */
  expense: { monthlyTotal: number; hasDefaultFill: boolean } | null;
}

/** 대시보드 기준일(`YYYY-MM-DD`)의 연·월을 와이어 형식 `YYYYMM` 으로. */
export function toWireBaseMonth(baseDate: string): string | null {
  const match = BASE_DATE_PATTERN.exec(baseDate);
  return match ? `${match[1]}${match[2]}` : null;
}

/**
 * 구매력 카드의 입력값 한 줄. 연봉은 현재 연봉 공급자와 같이 기준월 말일까지 유효한
 * 마지막 연봉(도메인 정의서 §1.3)이라, 미래 날짜로 넣은 인상분이 카드에 섞이지 않는다.
 */
export function toPpiInputs(
  baseDate: string,
  income: IncomeResponse[] | null,
  applied: AppliedExpenseResponse | null,
): PpiInputs {
  return {
    grossSalary: salaryAt(baseDate, income),
    expense: appliedExpense(applied),
  };
}

function salaryAt(
  baseDate: string,
  income: IncomeResponse[] | null,
): number | null {
  const match = BASE_DATE_PATTERN.exec(baseDate);
  if (!match || !income) return null;
  const baseMonth = `${match[1]}-${match[2]}`;
  const effective = income
    .filter((row) => row.grossSalary > 0)
    .filter((row) => row.effectiveDate.slice(0, 7) <= baseMonth)
    .sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate));
  return effective[0]?.grossSalary ?? null;
}

function appliedExpense(
  applied: AppliedExpenseResponse | null,
): PpiInputs["expense"] {
  if (!applied || applied.amountBasis === "NONE") return null;
  const amounts = applied.categories.flatMap((c) =>
    c.amount === null ? [] : [c.amount],
  );
  if (amounts.length === 0) return null;
  return {
    monthlyTotal: amounts.reduce((sum, amount) => sum + amount, 0),
    hasDefaultFill: applied.categories.some(
      (c) => c.weightStatus === "DEFAULT_CPI",
    ),
  };
}
