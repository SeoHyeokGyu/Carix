import { describe, expect, it } from "vitest";

import { EXPENSE_CATEGORIES, expenseSchema } from "./expense";

describe("expenseSchema", () => {
  const valid = {
    items: [
      { category: "HOUSING", amount: 800_000 },
      { category: "FOOD", amount: 400_000 },
    ],
  };

  it("정상 입력을 통과시킨다", () => {
    expect(expenseSchema.safeParse(valid).success).toBe(true);
  });

  it("카테고리는 ExpenseCategoryEnum 여섯 값만 받는다", () => {
    expect(EXPENSE_CATEGORIES.map((o) => o.value)).toEqual([
      "HOUSING",
      "FOOD",
      "TRANSPORT",
      "MEDICAL",
      "EDUCATION",
      "LEISURE",
    ]);
    expect(
      expenseSchema.safeParse({
        items: [{ category: "TRANSPORTATION", amount: 100_000 }],
      }).success,
    ).toBe(false);
    expect(
      expenseSchema.safeParse({
        items: [{ category: "HEALTH", amount: 100_000 }],
      }).success,
    ).toBe(false);
    expect(
      expenseSchema.safeParse({
        items: [{ category: "OTHER", amount: 100_000 }],
      }).success,
    ).toBe(false);
  });

  it("카테고리별 월 금액은 0~5,000만 원이다", () => {
    expect(
      expenseSchema.safeParse({
        items: [{ category: "FOOD", amount: -1 }],
      }).success,
    ).toBe(false);
    expect(
      expenseSchema.safeParse({
        items: [{ category: "FOOD", amount: 0 }],
      }).success,
    ).toBe(true);
    expect(
      expenseSchema.safeParse({
        items: [{ category: "FOOD", amount: 50_000_000 }],
      }).success,
    ).toBe(true);
    expect(
      expenseSchema.safeParse({
        items: [{ category: "FOOD", amount: 50_000_001 }],
      }).success,
    ).toBe(false);
  });

  it("미입력 카테고리는 items 에서 빼서 보낸다 — 빈 배열도 통과한다", () => {
    expect(expenseSchema.safeParse({ items: [] }).success).toBe(true);
  });
});
