import type { EvaluateRequest } from "./api/scenario";

/**
 * 초안 저장을 순서대로 보내, 늦게 끝난 앞선 요청이 최신 초안을 덮지 않게 한다.
 * 실패는 호출자에게 던지되 뒤 저장의 줄은 끊지 않는다.
 */
export function createDraftQueue(
  put: (request: EvaluateRequest) => Promise<unknown>,
): (request: EvaluateRequest) => Promise<void> {
  let tail: Promise<void> = Promise.resolve();
  return (request) => {
    const run = tail.then(() => put(request)).then(() => {});
    tail = run.catch(() => {});
    return run;
  };
}
