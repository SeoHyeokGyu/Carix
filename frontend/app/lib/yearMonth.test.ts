import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { currentYearMonth } from "./yearMonth";

describe("currentYearMonth", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("YYYYMM 형식으로 당월을 반환한다", () => {
    vi.setSystemTime(new Date(2026, 8, 11)); // 2026-09-11 (월은 0-indexed)
    expect(currentYearMonth()).toBe("202609");
  });

  it("한 자리 월은 0으로 채운다", () => {
    vi.setSystemTime(new Date(2026, 0, 1)); // 2026-01-01
    expect(currentYearMonth()).toBe("202601");
  });
});
