import { useCallback, useEffect, useRef, useState } from "react";

import type { EvaluateRequest } from "../../lib/api/scenario";

/** 불러오기 뒤 되돌리기 알림이 떠 있는 시간. */
export const UNDO_NOTICE_MS = 8000;

export interface AdoptNotice {
  name: string | null;
}

/**
 * 저장분을 불러올 때 직전 계산 요청을 기억해 두었다가 되돌린다. 알림은 하나만 — 새 불러오기가
 * 이전 것을 대체하고, 되돌릴 값도 그때의 직전 요청으로 바뀐다.
 * `beforeExpire` 는 알림이 시간이 다 돼 사라지기 직전에 부른다(알림 안 포커스를 옮길 기회).
 */
export function useUndoableAdopt(
  apply: (request: EvaluateRequest | null) => void,
  beforeExpire?: () => void,
) {
  const applyRef = useRef(apply);
  const beforeExpireRef = useRef(beforeExpire);
  useEffect(() => {
    applyRef.current = apply;
    beforeExpireRef.current = beforeExpire;
  });
  const [notice, setNotice] = useState<AdoptNotice | null>(null);
  const previous = useRef<{ request: EvaluateRequest | null } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearTimer = useCallback(() => {
    if (timer.current !== null) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  }, []);
  useEffect(() => clearTimer, [clearTimer]);

  const adopt = useCallback(
    (
      next: EvaluateRequest,
      current: EvaluateRequest | null,
      name: string | null,
    ) => {
      clearTimer();
      previous.current = { request: current };
      applyRef.current(next);
      setNotice({ name });
      timer.current = setTimeout(() => {
        timer.current = null;
        previous.current = null;
        beforeExpireRef.current?.();
        setNotice(null);
      }, UNDO_NOTICE_MS);
    },
    [clearTimer],
  );

  const undo = useCallback(() => {
    const remembered = previous.current;
    if (remembered === null) return;
    clearTimer();
    previous.current = null;
    applyRef.current(remembered.request);
    setNotice(null);
  }, [clearTimer]);

  return { adopt, undo, notice };
}
