import { describe, expect, it } from "vitest";

import { SCENARIO_TEMPLATES } from "./simulatorTemplates";

describe("simulatorTemplates", () => {
  it("4가지 기본 템플릿이 정의되어 있다", () => {
    expect(SCENARIO_TEMPLATES).toHaveLength(4);
    const ids = SCENARIO_TEMPLATES.map((t) => t.id);
    expect(ids).toEqual(["jump", "startup", "rest-and-jump", "horizontal"]);
  });

  it("안정적 연봉 점프 템플릿은 기본급 15% 인상 및 10% 성과급을 설정한다", () => {
    const jumpTemplate = SCENARIO_TEMPLATES.find((t) => t.id === "jump")!;
    const move = jumpTemplate.createMove(60_000_000);

    expect(move.grossSalary).toBe(69_000_000); // 6,000 * 1.15 = 6,900
    expect(move.performanceBonusExpected).toBe(6_000_000); // 6,000 * 0.1 = 600
    expect(move.unemploymentMonths).toBe(0);
    expect(move.oneTimeCost).toBe(0);
  });

  it("스타트업 템플릿은 5% 인상과 사이닝/스톡옵션을 설정한다", () => {
    const startupTemplate = SCENARIO_TEMPLATES.find((t) => t.id === "startup")!;
    const move = startupTemplate.createMove(60_000_000);

    expect(move.grossSalary).toBe(63_000_000); // 6,000 * 1.05 = 6,300
    expect(move.signingBonus).toBe(10_000_000);
    expect(move.stockVesting12m).toBe(10_000_000);
  });

  it("충전 후 이직 템플릿은 2개월 공백과 일회성 비용 200만 원을 설정한다", () => {
    const restTemplate = SCENARIO_TEMPLATES.find(
      (t) => t.id === "rest-and-jump",
    )!;
    const move = restTemplate.createMove(60_000_000);

    expect(move.grossSalary).toBe(66_000_000); // 6,000 * 1.10 = 6,600
    expect(move.unemploymentMonths).toBe(2);
    expect(move.oneTimeCost).toBe(2_000_000);
  });

  it("수평 이동 템플릿은 기본급을 동결한다", () => {
    const horizontalTemplate = SCENARIO_TEMPLATES.find(
      (t) => t.id === "horizontal",
    )!;
    const move = horizontalTemplate.createMove(60_000_000);

    expect(move.grossSalary).toBe(60_000_000);
    expect(move.unemploymentMonths).toBe(0);
  });

  it("현재 연봉이 0 이하일 때는 기본 fallback 연봉(5,000만 원)을 기준으로 계산한다", () => {
    const jumpTemplate = SCENARIO_TEMPLATES.find((t) => t.id === "jump")!;
    const move = jumpTemplate.createMove(0);

    expect(move.grossSalary).toBe(57_500_000); // 5,000 * 1.15
  });
});
