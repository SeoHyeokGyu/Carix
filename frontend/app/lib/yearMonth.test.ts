import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { currentYearMonth, isFutureYearMonth } from "./yearMonth";

describe("isFutureYearMonth", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("당월은 미래가 아니다", () => {
    vi.setSystemTime(new Date(2026, 8, 11)); // 2026-09-11
    expect(isFutureYearMonth(currentYearMonth())).toBe(false);
  });

  it("형식이 아니면 미래로 보지 않는다", () => {
    // 스키마 검증이 따로 형식을 막는다. 여기서 true 를 내면 오류 문구가 "미래 월"로
    // 잘못 잡힌다.
    expect(isFutureYearMonth("abc")).toBe(false);
  });
});
