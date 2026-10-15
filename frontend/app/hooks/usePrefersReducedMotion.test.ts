import { renderHook } from "@testing-library/react";
import React from "react";
import { renderToString } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";

import { usePrefersReducedMotion } from "./usePrefersReducedMotion";

function mockMatchMedia(matches: boolean) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("usePrefersReducedMotion", () => {
  it("prefers-reduced-motion이 true면 true를 반환한다", () => {
    mockMatchMedia(true);
    const { result } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(true);
  });

  it("prefers-reduced-motion이 false면 false를 반환한다", () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(false);
  });

  it("서버 렌더링(SSR) 시에는 false를 반환한다", () => {
    let captured = true;
    const Component = () => {
      captured = usePrefersReducedMotion();
      return null;
    };
    renderToString(React.createElement(Component));
    expect(captured).toBe(false);
  });
});
