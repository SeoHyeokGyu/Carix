import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  currentYearMonth,
  formatYearMonth,
  isFutureYearMonth,
  toWireYearMonth,
} from "./yearMonth";

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

describe("formatYearMonth", () => {
  it("YYYYMM 을 YYYY-MM 으로 바꾼다", () => {
    expect(formatYearMonth("202607")).toBe("2026-07");
  });

  it("형식이 아니면 받은 값을 그대로 돌려준다", () => {
    // 서버가 규약을 어긴 값을 줘도 화면이 죽지 않아야 한다 — 빈 칸보다 원본이 낫다.
    expect(formatYearMonth("2026-07")).toBe("2026-07");
    expect(formatYearMonth("")).toBe("");
  });
});

describe("toWireYearMonth", () => {
  it("YYYY-MM 을 YYYYMM 으로 바꾼다", () => {
    expect(toWireYearMonth("2026-07")).toBe("202607");
  });

  it("이미 와이어 형식이면 그대로 돌려준다", () => {
    expect(toWireYearMonth("202607")).toBe("202607");
  });
});

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

  it("당월보다 뒤면 미래다", () => {
    vi.setSystemTime(new Date(2026, 8, 11)); // 2026-09-11
    const next = String(Number(currentYearMonth()) + 1);
    expect(isFutureYearMonth(next)).toBe(true);
  });

  it("과거 월은 미래가 아니다", () => {
    expect(isFutureYearMonth("200001")).toBe(false);
  });

  it("형식이 아니면 미래로 보지 않는다", () => {
    // 스키마 검증이 따로 형식을 막는다. 여기서 true 를 내면 오류 문구가 "미래 월"로
    // 잘못 잡힌다.
    expect(isFutureYearMonth("abc")).toBe(false);
  });
});
