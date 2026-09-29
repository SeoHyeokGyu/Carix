import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  NARROW_VIEWPORT_QUERY,
  useIsNarrowViewport,
} from "./useIsNarrowViewport";

function mockMatchMedia(matches: boolean) {
  const matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
  vi.stubGlobal("matchMedia", matchMedia);
  return matchMedia;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("useIsNarrowViewport", () => {
  it("좁은 폭이면 true", () => {
    const matchMedia = mockMatchMedia(true);
    const { result } = renderHook(() => useIsNarrowViewport());
    expect(result.current).toBe(true);
    expect(matchMedia).toHaveBeenCalledWith(NARROW_VIEWPORT_QUERY);
  });

  it("넓은 폭이면 false", () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => useIsNarrowViewport());
    expect(result.current).toBe(false);
  });
});
