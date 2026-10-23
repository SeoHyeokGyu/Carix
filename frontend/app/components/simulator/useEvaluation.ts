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
  /** 이 평가를 시작한 재시도 키. 키가 바뀌면 같은 요청도 낡은 것으로 본다. */
  revision: number;
  result: EvaluationResponse | null;
  error: string | null;
}

const NO_EVALUATION: Evaluation = {
  request: null,
  revision: 0,
  result: null,
  error: null,
};

export interface UseEvaluationOptions {
  /** 바뀌면 같은 요청이라도 다시 평가한다. */
  revision: number;
  /** true 동안은 평가를 시작하지 않는다. 풀리면 그때의 최신 요청으로 한 번 평가한다. */
  paused: boolean;
}

export function useEvaluation(
  active: boolean,
  draft: EvaluateRequest | null,
  onUnauthorized: () => void,
  options: UseEvaluationOptions,
) {
  const { revision, paused } = options;
  const [evaluation, setEvaluation] = useState<Evaluation>(NO_EVALUATION);
  const current =
    evaluation.request === draft && evaluation.revision === revision;
  const stale = active && draft !== null && !current;
  const unauthorized = useRef(onUnauthorized);
  useEffect(() => {
    unauthorized.current = onUnauthorized;
  });

  useEffect(() => {
    if (!stale || paused || !draft) return;
    let cancelled = false;
    evaluate(draft)
      .then((result) => {
        if (!cancelled)
          setEvaluation({ request: draft, revision, result, error: null });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof UnauthorizedError) return unauthorized.current();
        setEvaluation((prev) => ({
          request: draft,
          revision,
          result: prev.result,
          error: describeError(error),
        }));
      });
    return () => {
      cancelled = true;
    };
  }, [stale, paused, draft, revision]);

  return {
    evaluation,
    // 낡은 입력에 대한 실패 문장은 지금 입력과 무관하다.
    error: current ? evaluation.error : null,
    pending: stale,
    retry: () => setEvaluation(NO_EVALUATION),
  };
}
