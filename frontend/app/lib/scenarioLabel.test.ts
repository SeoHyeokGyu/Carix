import { describe, expect, it } from "vitest";

import { scenarioDisplayName, scenarioOptionLabel } from "./scenarioLabel";

describe("scenarioDisplayName", () => {
  it("이름이 있으면 그대로다", () => {
    expect(scenarioDisplayName("A사 오퍼")).toBe("A사 오퍼");
  });

  it("null·공백뿐인 이름은 이름 없음이다", () => {
    expect(scenarioDisplayName(null)).toBeNull();
    expect(scenarioDisplayName("   ")).toBeNull();
  });

  // 서버가 이름 없는 계산에 "기본 시나리오" 를 채워 저장하던 시절의 행이다
  it("예전 기본명 「기본 시나리오」도 이름 없음으로 본다", () => {
    expect(scenarioDisplayName("기본 시나리오")).toBeNull();
  });
});

describe("scenarioOptionLabel", () => {
  it("이름 뒤에 계산 시각(서울 기준)을 붙인다 — 같은 이름이 여러 번 저장되기 때문이다", () => {
    expect(
      scenarioOptionLabel({
        scenarioName: "A사 오퍼",
        calculatedAt: "2026-10-16T05:30:00Z",
      }),
    ).toBe("A사 오퍼 · 2026. 10. 16. 14:30");
  });

  it("이름이 없으면 계산 시각으로 라벨을 만든다", () => {
    expect(
      scenarioOptionLabel({
        scenarioName: null,
        calculatedAt: "2026-10-16T05:30:00Z",
      }),
    ).toBe("2026. 10. 16. 14:30 계산");
    expect(
      scenarioOptionLabel({
        scenarioName: "기본 시나리오",
        calculatedAt: "2026-01-02T15:05:00Z",
      }),
    ).toBe("2026. 01. 03. 00:05 계산");
  });
});
