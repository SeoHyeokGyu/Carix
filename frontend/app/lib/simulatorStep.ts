import type { EvaluateRequest } from "./api/scenario";

export type SimulatorStep = "current" | "move";

/** 옛 `?step=` 값(stay·result)도 새 단계로 읽어 기존 링크가 깨지지 않게 한다. */
const STEP_BY_QUERY: ReadonlyMap<string, SimulatorStep> = new Map([
  ["current", "current"],
  ["stay", "current"],
  ["move", "move"],
  ["result", "move"],
]);

/**
 * `?step=` 이 유효하면 따르되, 계산 요청이 없으면 move 로 갈 수 없어 current 로 둔다.
 * 쿼리가 없거나 모르는 값이면 이직 입력 유무로 정한다.
 */
export function initialStep(
  search: URLSearchParams,
  request: EvaluateRequest | null,
): SimulatorStep {
  const requested = STEP_BY_QUERY.get(search.get("step") ?? "");
  if (requested === "move") return request === null ? "current" : "move";
  if (requested) return requested;
  return request?.move ? "move" : "current";
}

export type StepStatus = {
  moveReachable: boolean;
  currentDone: boolean;
  moveDone: boolean;
};

/** ✓ 표시는 현재 단계와 무관하게 입력 유무로 정한다. */
export function stepStatus(request: EvaluateRequest | null): StepStatus {
  const currentDone = request !== null;
  return {
    moveReachable: currentDone,
    currentDone,
    moveDone: request?.move != null,
  };
}
