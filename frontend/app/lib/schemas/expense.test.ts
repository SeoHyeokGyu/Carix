import { describe, expect, it } from "vitest";

import { expenseSchema } from "./expense";

describe("expenseSchema", () => {
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
