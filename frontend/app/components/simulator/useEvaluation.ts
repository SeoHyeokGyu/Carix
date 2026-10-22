import { useEffect, useRef, useState } from "react";

import {
  evaluate,
  type EvaluateRequest,
  type EvaluationResponse,
} from "../../lib/api/scenario";
import { UnauthorizedError } from "../../lib/coreApi";
import { describeError } from "../../lib/simulatorForm";

/** `request` 가 어느 입력에 대한 평가인지 함께 들고 있어, 초안이 바뀌면 낡은 결과로 알아본다. */
export interface Evaluation {
  request: EvaluateRequest | null;
  result: EvaluationResponse | null;
  error: string | null;
}

const NO_EVALUATION: Evaluation = { request: null, result: null, error: null };

export function useEvaluation(
  active: boolean,
  draft: EvaluateRequest | null,
  onUnauthorized: () => void,
) {
  const [evaluation, setEvaluation] = useState<Evaluation>(NO_EVALUATION);
  const stale = active && draft !== null && evaluation.request !== draft;
  const unauthorized = useRef(onUnauthorized);
  useEffect(() => {
    unauthorized.current = onUnauthorized;
  });

  useEffect(() => {
    if (!stale || !draft) return;
    let cancelled = false;
    evaluate(draft)
      .then((result) => {
        if (!cancelled) setEvaluation({ request: draft, result, error: null });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof UnauthorizedError) return unauthorized.current();
        setEvaluation((prev) => ({
          request: draft,
          result: prev.result,
          error: describeError(error),
        }));
      });
    return () => {
      cancelled = true;
    };
  }, [stale, draft]);

  return {
    evaluation,
    // 낡은 입력에 대한 실패 문장은 지금 입력과 무관하다.
    error: evaluation.request === draft ? evaluation.error : null,
    pending: stale,
    retry: () => setEvaluation(NO_EVALUATION),
  };
}
