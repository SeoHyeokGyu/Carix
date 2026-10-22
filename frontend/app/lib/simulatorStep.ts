import type { EvaluateRequest } from "./api/scenario";

export type SimulatorStep = "current" | "stay" | "move" | "result";

const STEPS: readonly SimulatorStep[] = ["current", "stay", "move", "result"];

/**
 * `?step=` 이 유효하면 그것을 따르고, 아니면 초안으로 정한다. 이직 없이 ②에서 떠난 초안은
 * 결과가 아니라 ② 로 되돌린다 — 아직 이직 입력이 없기 때문이다.
 */
export function initialStep(
  search: URLSearchParams,
  draft: EvaluateRequest | null,
): SimulatorStep {
  const requested = search.get("step");
  const valid = STEPS.find((step) => step === requested);
  if (valid) return valid;
  if (!draft) return "current";
  return draft.move ? "result" : "stay";
}
