import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { EvaluateRequest } from "../../lib/api/scenario";
import { UNDO_NOTICE_MS, useUndoableAdopt } from "./useUndoableAdopt";

const A: EvaluateRequest = { stay: { raisePercent: 3 }, move: null };
const B: EvaluateRequest = {
  stay: { raisePercent: 5 },
  move: {
    grossSalary: 72_000_000,
    unemploymentMonths: 0,
    oneTimeCost: 0,
    benefitEligible: false,
    bonusIncluded: true,
  },
};

describe("useUndoableAdopt", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("불러온 뒤 되돌리면 직전 계산 요청으로 돌아간다", () => {
    const apply = vi.fn();
    const { result } = renderHook(() => useUndoableAdopt(apply));

    act(() => result.current.adopt(B, A, "「B사」을 불러왔습니다."));
    expect(apply).toHaveBeenLastCalledWith(B, "all");
    expect(result.current.notice?.message).toBe("「B사」을 불러왔습니다.");

    act(() => result.current.undo());
    expect(apply).toHaveBeenLastCalledWith(A, "all");
    expect(result.current.notice).toBeNull();
  });

  it("되돌릴 값이 null 이어도 그대로 돌려준다", () => {
    const apply = vi.fn();
    const { result } = renderHook(() => useUndoableAdopt(apply));

    act(() => result.current.adopt(B, null, "「저장분」을 불러왔습니다."));
    act(() => result.current.undo());

    expect(apply).toHaveBeenLastCalledWith(null, "all");
  });

  it("8초 뒤 알림이 닫힌다", () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useUndoableAdopt(vi.fn()));

    act(() => result.current.adopt(B, A, "「B사」을 불러왔습니다."));
    act(() => vi.advanceTimersByTime(UNDO_NOTICE_MS - 1));
    expect(result.current.notice).not.toBeNull();

    act(() => vi.advanceTimersByTime(1));
    expect(result.current.notice).toBeNull();
  });
});
