import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { useAlertMessage } from "./useAlertMessage";

describe("useAlertMessage", () => {
  it("같은 문장을 다시 넣어도 키가 바뀐다", () => {
    const { result } = renderHook(() => useAlertMessage());
    act(() => result.current.show("실패했습니다"));
    const firstKey = result.current.key;
    act(() => result.current.show("실패했습니다"));
    expect(result.current.message).toBe("실패했습니다");
    expect(result.current.key).not.toBe(firstKey);
  });

  it("지우면 문장이 사라진다", () => {
    const { result } = renderHook(() => useAlertMessage());
    act(() => result.current.show("실패했습니다"));
    act(() => result.current.clear());
    expect(result.current.message).toBeNull();
  });
});
