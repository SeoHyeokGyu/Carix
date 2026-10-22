import type { SimulatorStep } from "./simulatorStep";

export type CurrentJobFocus = "performanceBonus" | "stock" | "hireDate";
export type MoveFocus = "performanceBonus" | "signingBonus" | "stockVesting";

/** 미반영 `넣기` 가 열 단계와 그 단계에서 포커스할 칸. */
export type FillTarget =
  | { step: Extract<SimulatorStep, "current">; field: CurrentJobFocus }
  | { step: Extract<SimulatorStep, "move">; field: MoveFocus };

const TARGETS: Record<string, FillTarget> = {
  PERFORMANCE_BONUS: { step: "current", field: "performanceBonus" },
  STOCK: { step: "current", field: "stock" },
  HIRE_DATE: { step: "current", field: "hireDate" },
  MOVE_PERFORMANCE_BONUS: { step: "move", field: "performanceBonus" },
  SIGNING_BONUS: { step: "move", field: "signingBonus" },
  MOVE_STOCK: { step: "move", field: "stockVesting" },
};

/** 모르는 코드는 undefined — 엉뚱한 단계로 보내지 않는다. */
export function fillTargetOf(code: string): FillTarget | undefined {
  return TARGETS[code];
}
