export type CurrentJobFocus = "performanceBonus" | "stock" | "hireDate";
export type MoveFocus = "performanceBonus" | "signingBonus" | "stockVesting";

/** 미반영 `넣기` 가 보낼 카드와 그 카드에서 포커스할 칸. */
export type FillTarget =
  | { card: "current"; field: CurrentJobFocus }
  | { card: "offer"; field: MoveFocus };

const TARGETS: Record<string, FillTarget> = {
  PERFORMANCE_BONUS: { card: "current", field: "performanceBonus" },
  STOCK: { card: "current", field: "stock" },
  HIRE_DATE: { card: "current", field: "hireDate" },
  MOVE_PERFORMANCE_BONUS: { card: "offer", field: "performanceBonus" },
  SIGNING_BONUS: { card: "offer", field: "signingBonus" },
  MOVE_STOCK: { card: "offer", field: "stockVesting" },
};

/** 모르는 코드는 undefined — 엉뚱한 카드로 보내지 않는다. */
export function fillTargetOf(code: string): FillTarget | undefined {
  return TARGETS[code];
}
