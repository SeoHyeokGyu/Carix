import { describe, expect, it } from "vitest";

import { scenarioSchema } from "./scenario";

describe("scenarioSchema", () => {
  const valid = {
    scenarioName: "이직 vs 잔류",
    stayExpectedRaiseRate: 5,
    moveTargetGrossSalary: 60_000_000,
    moveOneTimeCost: 2_000_000,
    expectedUnemploymentMonths: 1,
    isEligibleForUnemploymentBenefit: true,
  };

  it("정상 입력을 통과시킨다", () => {
    expect(scenarioSchema.safeParse(valid).success).toBe(true);
  });

  it("예상 인상률은 -50~+100 이다 (기능 명세 §3, API 규격의 -100~100 보다 좁다)", () => {
    expect(
      scenarioSchema.safeParse({ ...valid, stayExpectedRaiseRate: -51 })
        .success,
    ).toBe(false);
    expect(
      scenarioSchema.safeParse({ ...valid, stayExpectedRaiseRate: -50 })
        .success,
    ).toBe(true);
    expect(
      scenarioSchema.safeParse({ ...valid, stayExpectedRaiseRate: 100 })
        .success,
    ).toBe(true);
    expect(
      scenarioSchema.safeParse({ ...valid, stayExpectedRaiseRate: 101 })
        .success,
    ).toBe(false);
  });

  it("이직 목표 연봉은 세전 연봉과 같은 범위(1,000만~30억)다", () => {
    expect(
      scenarioSchema.safeParse({
        ...valid,
        moveTargetGrossSalary: 9_999_999,
      }).success,
    ).toBe(false);
    expect(
      scenarioSchema.safeParse({
        ...valid,
        moveTargetGrossSalary: 10_000_000,
      }).success,
    ).toBe(true);
    expect(
      scenarioSchema.safeParse({
        ...valid,
        moveTargetGrossSalary: 3_000_000_000,
      }).success,
    ).toBe(true);
    expect(
      scenarioSchema.safeParse({
        ...valid,
        moveTargetGrossSalary: 3_000_000_001,
      }).success,
    ).toBe(false);
  });

  it("이직 일회성 비용은 0~1억 원이다", () => {
    expect(
      scenarioSchema.safeParse({ ...valid, moveOneTimeCost: -1 }).success,
    ).toBe(false);
    expect(
      scenarioSchema.safeParse({ ...valid, moveOneTimeCost: 0 }).success,
    ).toBe(true);
    expect(
      scenarioSchema.safeParse({ ...valid, moveOneTimeCost: 100_000_000 })
        .success,
    ).toBe(true);
    expect(
      scenarioSchema.safeParse({ ...valid, moveOneTimeCost: 100_000_001 })
        .success,
    ).toBe(false);
  });

  it("예상 공백 기간은 0~6개월이다", () => {
    expect(
      scenarioSchema.safeParse({ ...valid, expectedUnemploymentMonths: -1 })
        .success,
    ).toBe(false);
    expect(
      scenarioSchema.safeParse({ ...valid, expectedUnemploymentMonths: 0 })
        .success,
    ).toBe(true);
    expect(
      scenarioSchema.safeParse({ ...valid, expectedUnemploymentMonths: 6 })
        .success,
    ).toBe(true);
    expect(
      scenarioSchema.safeParse({ ...valid, expectedUnemploymentMonths: 7 })
        .success,
    ).toBe(false);
  });

  it("시나리오 이름은 선택이고 100자를 넘을 수 없다", () => {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { scenarioName: _drop, ...withoutName } = valid;
    expect(scenarioSchema.safeParse(withoutName).success).toBe(true);
    expect(
      scenarioSchema.safeParse({ ...valid, scenarioName: "a".repeat(100) })
        .success,
    ).toBe(true);
    expect(
      scenarioSchema.safeParse({ ...valid, scenarioName: "a".repeat(101) })
        .success,
    ).toBe(false);
  });
});
