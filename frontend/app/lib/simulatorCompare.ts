import type { EvaluationResponse } from "./api/scenario";

type MoveView = NonNullable<EvaluationResponse["move"]>;

export type OfferMissing = "gross" | "benefit" | "other";

export function compareHint(
  hasMove: boolean,
  missing: OfferMissing | null,
): string | null {
  if (hasMove) return null;
  if (missing === "benefit")
    return "실업급여를 받을 수 있는지 고르면 비교가 나옵니다";
  return "새 회사 연봉과 실업급여 여부를 넣으면 비교가 나옵니다";
}

export function equalizingLine(
  equal: number | null,
  expectedPercent: number,
): { value: string; note: string | null } {
  if (equal === null) return { value: "계산할 수 없음", note: null };
  const expected = expectedPercent.toFixed(1);
  const note =
    equal <= expectedPercent
      ? `올해 예상 ${expected}%로 이미 넘습니다`
      : `지금 회사에서 이만큼 오르면 4년 누적이 같아집니다 · 올해 예상 ${expected}%`;
  return { value: `${equal.toFixed(1)}%`, note };
}

export function cumulativeGap(
  stay: readonly number[],
  move: readonly number[],
): number[] {
  const length = Math.min(stay.length, move.length);
  return Array.from({ length }, (_, i) => move[i] - stay[i]);
}

export interface FirstYearItem {
  key: "salaryDiff" | "gap" | "oneTimeCost" | "benefit" | "total";
  label: string;
  value: number;
  isTotal: boolean;
}

export function firstYearItems(
  firstYear: MoveView["firstYear"],
): FirstYearItem[] {
  const items: FirstYearItem[] = [
    {
      key: "salaryDiff",
      label: "연봉 차이",
      value: firstYear.salaryDiff,
      isTotal: false,
    },
    {
      key: "gap",
      label: "쉬는 기간 소득",
      value: firstYear.gap,
      isTotal: false,
    },
    {
      key: "oneTimeCost",
      label: "일회성 비용",
      value: firstYear.oneTimeCost,
      isTotal: false,
    },
  ];
  if (firstYear.benefit !== 0) {
    items.push({
      key: "benefit",
      label: "실업급여",
      value: firstYear.benefit,
      isTotal: false,
    });
  }
  items.push({
    key: "total",
    label: "합계",
    value: firstYear.total,
    isTotal: true,
  });
  return items;
}

export const NEW_JOB_SEVERANCE = "NEW_JOB_SEVERANCE";

export function splitAssumptions(list: EvaluationResponse["assumptions"]): {
  warnings: string[];
  rest: EvaluationResponse["assumptions"];
} {
  return {
    warnings: list
      .filter((a) => a.code === NEW_JOB_SEVERANCE)
      .map((a) => a.text),
    rest: list.filter((a) => a.code !== NEW_JOB_SEVERANCE),
  };
}
