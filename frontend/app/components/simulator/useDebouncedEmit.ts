import { useCallback, useEffect, useRef } from "react";

/** 즉시 계산 대기 시간. 스펙 「즉시 계산」 — 옛 `QuickEdit` 의 값과 같다. */
export const EDIT_DEBOUNCE_MS = 400;

/** `mount` 는 들어오자마자 유효한 입력, `edit` 는 사용자가 바꾼 입력. 라우트는 `edit` 부터 초안을 저장한다. */
export type EmitSource = "mount" | "edit";

/** 단계 입력 컴포넌트가 라우트에 내주는 손잡이. */
export interface StepFormHandle<T> {
  /** 대기 중인 편집을 바로 반영한다. 유효하면 그 값을 내보내고 돌려주고, 무효면 오류를 보이고 null. */
  flush(): T | null;
}

function sameDeps(a: readonly unknown[], b: readonly unknown[]): boolean {
  return a.length === b.length && a.every((value, i) => Object.is(value, b[i]));
}

/**
 * 입력이 바뀌면 `delayMs` 뒤 `compute()` 가 null 이 아닐 때 `emit(value, "edit")`.
 * 처음 그릴 때 유효하면 기다리지 않고 `emit(value, "mount")` 한 번.
 * `flush()` 는 대기를 취소하고 바로 계산해 내보낸다.
 *
 * 언마운트 때 대기 중인 편집은 버린다 — 불러오기로 단계가 새로 그려질 때 옛 편집이
 * 불러온 초안을 덮으면 안 된다. 단계 이동 전에는 호출자가 `flush()` 한다.
 */
export function useDebouncedEmit<T>(
  compute: () => T | null,
  deps: readonly unknown[],
  emit: (value: T, source: EmitSource) => void,
  delayMs = EDIT_DEBOUNCE_MS,
): { flush: () => T | null } {
  const latest = useRef({ compute, emit });
  useEffect(() => {
    latest.current = { compute, emit };
  });
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 직전 커밋의 deps. StrictMode 가 effect 를 두 번 돌려도 deps 가 같으면 편집으로 치지 않는다.
  const committedDeps = useRef<readonly unknown[] | null>(null);

  const cancel = useCallback(() => {
    if (timer.current !== null) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  }, []);

  useEffect(() => {
    const previous = committedDeps.current;
    committedDeps.current = deps;
    if (previous === null) {
      const value = latest.current.compute();
      if (value !== null) latest.current.emit(value, "mount");
      return;
    }
    if (sameDeps(previous, deps)) return;
    cancel();
    timer.current = setTimeout(() => {
      timer.current = null;
      const value = latest.current.compute();
      if (value !== null) latest.current.emit(value, "edit");
    }, delayMs);
  });

  useEffect(() => cancel, [cancel]);

  const flush = useCallback(() => {
    cancel();
    const value = latest.current.compute();
    if (value !== null) latest.current.emit(value, "edit");
    return value;
  }, [cancel]);

  return { flush };
}
