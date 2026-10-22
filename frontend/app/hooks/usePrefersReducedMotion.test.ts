import React from "react";
import { renderToString } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";

import { usePrefersReducedMotion } from "./usePrefersReducedMotion";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("usePrefersReducedMotion", () => {
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
